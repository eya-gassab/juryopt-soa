package com.juryopt.orchestrator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.juryopt.orchestrator.client.RegistrarSoapClient.ClearanceResult;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import java.io.IOException;
import java.util.List;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.xml.transform.StringSource;

@SpringBootTest(
        classes = RegistrarSoapClientResilienceTest.TestConfig.class,
        properties = {
                "resilience4j.retry.instances.registrarSoap.wait-duration=10ms"
        }
)
class RegistrarSoapClientResilienceTest {

    @Configuration
    @Import({
            AopAutoConfiguration.class,
            RetryAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            RegistrarSoapClient.class
    })
    static class TestConfig {
        @Bean
        Jaxb2Marshaller registrarMarshaller() {
            Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
            marshaller.setContextPath("com.juryopt.orchestrator.client.soap.gen");
            return marshaller;
        }
    }

    @Autowired
    private RegistrarSoapClient registrarSoapClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private WebServiceTemplate registrarWebServiceTemplate;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("registrarSoap").reset();
        reset(registrarWebServiceTemplate);
    }

    @Test
    void verifyEligibility_onTechnicalIoFailure_shouldRetryTwiceAndFallbackToProvisional() {
        when(registrarWebServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(new WebServiceIOException("Connection refused", new IOException("refused")));

        ClearanceResult result = registrarSoapClient.verifyEligibility("ETU-2026-001");

        assertThat(result.verified()).isFalse();
        assertThat(result.clearanceToken()).isNull();
        verify(registrarWebServiceTemplate, times(2)).marshalSendAndReceive(any());
    }

    @Test
    void verifyEligibility_onServerSoapFault_shouldTreatAsTechnicalFailureAndFallback() {
        SoapFaultClientException serverFault = mockServerFault("Internal registrar database failure");
        when(registrarWebServiceTemplate.marshalSendAndReceive(any())).thenThrow(serverFault);

        ClearanceResult result = registrarSoapClient.verifyEligibility("ETU-2026-001");

        assertThat(result.verified()).isFalse();
        verify(registrarWebServiceTemplate, times(2)).marshalSendAndReceive(any());
        assertThat(circuitBreakerRegistry.circuitBreaker("registrarSoap")
                .getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    void verifyEligibility_onRepeatedBusinessFaults_shouldNeverFallbackOrOpenCircuit() {
        String ineligibilityXml = """
                <AcademicIneligibilityFault xmlns="http://juryopt.com/registrar">
                  <studentMatricule>ETU-2026-005</studentMatricule>
                  <reason>INSUFFICIENT_CREDITS</reason>
                  <creditsEarned>150</creditsEarned>
                  <requiredCredits>180</requiredCredits>
                  <message>Crédits insuffisants (150/180)</message>
                </AcademicIneligibilityFault>
                """;
        when(registrarWebServiceTemplate.marshalSendAndReceive(any()))
                .thenAnswer(inv -> {
                    throw mockClientFault(ineligibilityXml);
                });

        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> registrarSoapClient.verifyEligibility("ETU-2026-005"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("150/180");
        }

        verify(registrarWebServiceTemplate, times(6)).marshalSendAndReceive(any());
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("registrarSoap");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void verifyEligibility_onStudentNotFoundFault_shouldThrowNotFoundWithoutFallback() {
        String notFoundXml = """
                <StudentNotFoundFault xmlns="http://juryopt.com/registrar">
                  <studentMatricule>ETU-2026-999</studentMatricule>
                  <message>Aucun dossier académique trouvé pour le matricule ETU-2026-999</message>
                </StudentNotFoundFault>
                """;
        SoapFaultClientException notFoundFault = mockClientFault(notFoundXml);
        when(registrarWebServiceTemplate.marshalSendAndReceive(any())).thenThrow(notFoundFault);

        assertThatThrownBy(() -> registrarSoapClient.verifyEligibility("ETU-2026-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ETU-2026-999");

        verify(registrarWebServiceTemplate, times(1)).marshalSendAndReceive(any());
        assertThat(circuitBreakerRegistry.circuitBreaker("registrarSoap").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void verifyEligibility_whenCircuitIsOpen_shouldFallbackImmediatelyWithoutRetryOrNetworkCall() {
        when(registrarWebServiceTemplate.marshalSendAndReceive(any()))
                .thenThrow(new WebServiceIOException("Connection refused", new IOException("refused")));

        registrarSoapClient.verifyEligibility("ETU-2026-001");
        registrarSoapClient.verifyEligibility("ETU-2026-002");

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("registrarSoap");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        reset(registrarWebServiceTemplate);

        ClearanceResult fallbackWhenOpen = registrarSoapClient.verifyEligibility("ETU-2026-003");
        assertThat(fallbackWhenOpen.verified()).isFalse();
        verifyNoInteractions(registrarWebServiceTemplate);
    }

    private SoapFaultClientException mockClientFault(String detailXml) {
        SoapFaultClientException ex = mock(SoapFaultClientException.class);
        SoapFault soapFault = mock(SoapFault.class);
        SoapFaultDetail detail = mock(SoapFaultDetail.class);
        SoapFaultDetailElement element = mock(SoapFaultDetailElement.class);

        when(ex.getFaultCode()).thenReturn(new QName("http://schemas.xmlsoap.org/soap/envelope/", "Client"));
        when(ex.getSoapFault()).thenReturn(soapFault);
        when(soapFault.getFaultDetail()).thenReturn(detail);
        when(detail.getDetailEntries()).thenReturn(List.of(element).iterator());
        when(element.getSource()).thenReturn(new StringSource(detailXml));
        return ex;
    }

    private SoapFaultClientException mockServerFault(String reason) {
        SoapFaultClientException ex = mock(SoapFaultClientException.class);
        when(ex.getFaultCode()).thenReturn(new QName("http://schemas.xmlsoap.org/soap/envelope/", "Server"));
        when(ex.getFaultStringOrReason()).thenReturn(reason);
        return ex;
    }
}