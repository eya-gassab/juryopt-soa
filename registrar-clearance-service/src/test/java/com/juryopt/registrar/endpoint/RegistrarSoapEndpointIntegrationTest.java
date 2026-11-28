package com.juryopt.registrar.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import com.juryopt.registrar.config.WebServiceConfig;
import com.juryopt.registrar.repository.OfficialDefenseRegistryRepository;
import java.time.Year;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

@SpringBootTest
@ActiveProfiles("test")
class RegistrarSoapEndpointIntegrationTest {

    private static final Map<String, String> NS = Map.of("ns", WebServiceConfig.NAMESPACE_URI);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OfficialDefenseRegistryRepository registryRepository;

    private MockWebServiceClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = MockWebServiceClient.createClient(applicationContext);
        registryRepository.deleteAll();
    }

    @Test
    void verifyEligibility_whenEligible_shouldReturnDetailsAndClearanceToken() {
        String requestXml = """
                <ns:VerifyStudentEligibilityRequest xmlns:ns="http://juryopt.com/registrar">
                  <ns:studentMatricule>ETU-2026-001</ns:studentMatricule>
                </ns:VerifyStudentEligibilityRequest>
                """;

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(noFault())
                .andExpect(xpath("/ns:VerifyStudentEligibilityResponse/ns:studentMatricule", NS)
                        .evaluatesTo("ETU-2026-001"))
                .andExpect(xpath("/ns:VerifyStudentEligibilityResponse/ns:fullName", NS)
                        .evaluatesTo("Amine Ben Salem"))
                .andExpect(xpath("/ns:VerifyStudentEligibilityResponse/ns:creditsEarned", NS)
                        .evaluatesTo(180))
                .andExpect(xpath("/ns:VerifyStudentEligibilityResponse/ns:requiredCredits", NS)
                        .evaluatesTo(180))
                .andExpect(xpath("starts-with(/ns:VerifyStudentEligibilityResponse/ns:clearanceToken, 'CLR-')", NS)
                        .evaluatesTo(true));
    }

    @Test
    void verifyEligibility_whenUnknownStudent_shouldReturnStudentNotFoundFault() {
        String requestXml = """
                <ns:VerifyStudentEligibilityRequest xmlns:ns="http://juryopt.com/registrar">
                  <ns:studentMatricule>ETU-2026-999</ns:studentMatricule>
                </ns:VerifyStudentEligibilityRequest>
                """;

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns:StudentNotFoundFault/ns:studentMatricule", NS)
                        .evaluatesTo("ETU-2026-999"))
                .andExpect(xpath("//ns:StudentNotFoundFault/ns:message", NS).exists());
    }

    @Test
    void verifyEligibility_whenInsufficientCredits_shouldReturnAcademicIneligibilityFault() {
        String requestXml = """
                <ns:VerifyStudentEligibilityRequest xmlns:ns="http://juryopt.com/registrar">
                  <ns:studentMatricule>ETU-2026-005</ns:studentMatricule>
                </ns:VerifyStudentEligibilityRequest>
                """;

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns:AcademicIneligibilityFault/ns:studentMatricule", NS)
                        .evaluatesTo("ETU-2026-005"))
                .andExpect(xpath("//ns:AcademicIneligibilityFault/ns:reason", NS)
                        .evaluatesTo("INSUFFICIENT_CREDITS"))
                .andExpect(xpath("//ns:AcademicIneligibilityFault/ns:creditsEarned", NS)
                        .evaluatesTo(150))
                .andExpect(xpath("//ns:AcademicIneligibilityFault/ns:requiredCredits", NS)
                        .evaluatesTo(180));
    }

    @Test
    void verifyEligibility_whenAdminHold_shouldReturnAcademicIneligibilityFault() {
        String requestXml = """
                <ns:VerifyStudentEligibilityRequest xmlns:ns="http://juryopt.com/registrar">
                  <ns:studentMatricule>ETU-2026-006</ns:studentMatricule>
                </ns:VerifyStudentEligibilityRequest>
                """;

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns:AcademicIneligibilityFault/ns:studentMatricule", NS)
                        .evaluatesTo("ETU-2026-006"))
                .andExpect(xpath("//ns:AcademicIneligibilityFault/ns:reason", NS)
                        .evaluatesTo("ADMIN_HOLD"));
    }

    @Test
    void registerOfficialDefense_whenValid_shouldPersistAndReturnRegistryCode() {
        String expectedCode = String.format("REG-%d-0001", Year.now().getValue());
        String requestXml = """
                <ns:RegisterOfficialDefenseRequest xmlns:ns="http://juryopt.com/registrar">
                  <ns:studentMatricule>ETU-2026-001</ns:studentMatricule>
                  <ns:clearanceToken>CLR-12345678</ns:clearanceToken>
                  <ns:slotCode>2026-06-15_09:00</ns:slotCode>
                  <ns:roomCode>SALLE-A1</ns:roomCode>
                  <ns:jurySummary>Encadrant=PROF-01, President=PROF-02, Rapporteur=PROF-03</ns:jurySummary>
                </ns:RegisterOfficialDefenseRequest>
                """;

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(noFault())
                .andExpect(xpath("/ns:RegisterOfficialDefenseResponse/ns:registryCode", NS)
                        .evaluatesTo(expectedCode))
                .andExpect(xpath("/ns:RegisterOfficialDefenseResponse/ns:registeredAt", NS).exists());

        assertThat(registryRepository.findByStudentMatricule("ETU-2026-001"))
                .isPresent()
                .get()
                .satisfies(entry -> {
                    assertThat(entry.getRegistryCode()).isEqualTo(expectedCode);
                    assertThat(entry.getSlotCode()).isEqualTo("2026-06-15_09:00");
                    assertThat(entry.getRoomCode()).isEqualTo("SALLE-A1");
                });
    }

    @Test
    void registerOfficialDefense_whenAlreadyRegistered_shouldReturnDuplicateRegistrationFault() {
        String expectedCode = String.format("REG-%d-0001", Year.now().getValue());
        String requestXml = """
                <ns:RegisterOfficialDefenseRequest xmlns:ns="http://juryopt.com/registrar">
                  <ns:studentMatricule>ETU-2026-001</ns:studentMatricule>
                  <ns:clearanceToken>CLR-12345678</ns:clearanceToken>
                  <ns:slotCode>2026-06-15_09:00</ns:slotCode>
                  <ns:roomCode>SALLE-A1</ns:roomCode>
                  <ns:jurySummary>Encadrant=PROF-01, President=PROF-02, Rapporteur=PROF-03</ns:jurySummary>
                </ns:RegisterOfficialDefenseRequest>
                """;

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(noFault());

        mockClient.sendRequest(withPayload(new StringSource(requestXml)))
                .andExpect(clientOrSenderFault())
                .andExpect(xpath("//ns:DuplicateRegistrationFault/ns:studentMatricule", NS)
                        .evaluatesTo("ETU-2026-001"))
                .andExpect(xpath("//ns:DuplicateRegistrationFault/ns:existingRegistryCode", NS)
                        .evaluatesTo(expectedCode));
    }
}