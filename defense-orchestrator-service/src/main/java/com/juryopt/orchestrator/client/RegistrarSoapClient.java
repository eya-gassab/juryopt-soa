package com.juryopt.orchestrator.client;

import com.juryopt.orchestrator.client.soap.gen.AcademicIneligibilityFault;
import com.juryopt.orchestrator.client.soap.gen.DuplicateRegistrationFault;
import com.juryopt.orchestrator.client.soap.gen.RegisterOfficialDefenseRequest;
import com.juryopt.orchestrator.client.soap.gen.RegisterOfficialDefenseResponse;
import com.juryopt.orchestrator.client.soap.gen.StudentNotFoundFault;
import com.juryopt.orchestrator.client.soap.gen.VerifyStudentEligibilityRequest;
import com.juryopt.orchestrator.client.soap.gen.VerifyStudentEligibilityResponse;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateRegistrationFaultException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Iterator;
import javax.xml.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.client.SoapFaultClientException;

@Component
public class RegistrarSoapClient {

    private static final Logger log = LoggerFactory.getLogger(RegistrarSoapClient.class);

    private final WebServiceTemplate registrarWebServiceTemplate;
    private final Jaxb2Marshaller registrarMarshaller;

    public RegistrarSoapClient(WebServiceTemplate registrarWebServiceTemplate,
                               Jaxb2Marshaller registrarMarshaller) {
        this.registrarWebServiceTemplate = registrarWebServiceTemplate;
        this.registrarMarshaller = registrarMarshaller;
    }

    public record ClearanceResult(boolean verified, String fullName, String clearanceToken) {
        public static ClearanceResult verified(String fullName, String clearanceToken) {
            return new ClearanceResult(true, fullName, clearanceToken);
        }

        public static ClearanceResult provisional() {
            return new ClearanceResult(false, null, null);
        }
    }

    public record RegistrationResult(String registryCode, String registeredAt) {
    }

    @Retry(name = "registrarSoap", fallbackMethod = "verifyEligibilityFallback")
    @CircuitBreaker(name = "registrarSoap")
    public ClearanceResult verifyEligibility(String studentMatricule) {
        log.info("Appel SOAP VerifyStudentEligibility pour matricule={}", studentMatricule);

        VerifyStudentEligibilityRequest request = new VerifyStudentEligibilityRequest();
        request.setStudentMatricule(studentMatricule);

        try {
            VerifyStudentEligibilityResponse response = (VerifyStudentEligibilityResponse)
                    registrarWebServiceTemplate.marshalSendAndReceive(request);
            if (response == null || response.getClearanceToken() == null) {
                throw new ExternalServiceUnavailableException(
                        "Réponse vide du service de scolarité pour " + studentMatricule
                );
            }
            return ClearanceResult.verified(response.getFullName(), response.getClearanceToken());
        } catch (SoapFaultClientException ex) {
            throw translateSoapFault(ex);
        }
    }

    @Retry(name = "registrarSoap", fallbackMethod = "registerOfficialDefenseFallback")
    @CircuitBreaker(name = "registrarSoap")
    public RegistrationResult registerOfficialDefense(String studentMatricule,
                                                      String clearanceToken,
                                                      String slotCode,
                                                      String roomCode,
                                                      String jurySummary) {
        log.info("Appel SOAP RegisterOfficialDefense pour matricule={} slot={} salle={}",
                studentMatricule, slotCode, roomCode);

        RegisterOfficialDefenseRequest request = new RegisterOfficialDefenseRequest();
        request.setStudentMatricule(studentMatricule);
        request.setClearanceToken(clearanceToken);
        request.setSlotCode(slotCode);
        request.setRoomCode(roomCode);
        request.setJurySummary(jurySummary);

        try {
            RegisterOfficialDefenseResponse response = (RegisterOfficialDefenseResponse)
                    registrarWebServiceTemplate.marshalSendAndReceive(request);
            if (response == null || response.getRegistryCode() == null) {
                throw new ExternalServiceUnavailableException(
                        "Réponse vide du service de scolarité lors de l'enregistrement de " + studentMatricule
                );
            }
            String registeredAt = response.getRegisteredAt() != null
                    ? response.getRegisteredAt().toXMLFormat()
                    : null;
            return new RegistrationResult(response.getRegistryCode(), registeredAt);
        } catch (SoapFaultClientException ex) {
            throw translateSoapFault(ex);
        }
    }

    private ClearanceResult verifyEligibilityFallback(String studentMatricule, Throwable ex) {
        rethrowIfBusinessException(ex);
        log.warn("Repli Resilience4j SOAP VerifyStudentEligibility pour {} ({}: {}) -> PROVISIONAL_PENDING_CLEARANCE",
                studentMatricule, ex.getClass().getSimpleName(), ex.getMessage());
        return ClearanceResult.provisional();
    }

    private RegistrationResult registerOfficialDefenseFallback(String studentMatricule,
                                                               String clearanceToken,
                                                               String slotCode,
                                                               String roomCode,
                                                               String jurySummary,
                                                               Throwable ex) {
        rethrowIfBusinessException(ex);
        log.warn("Échec technique SOAP RegisterOfficialDefense pour {} ({}: {})",
                studentMatricule, ex.getClass().getSimpleName(), ex.getMessage());
        if (ex instanceof ExternalServiceUnavailableException unavailable) {
            throw unavailable;
        }
        throw new ExternalServiceUnavailableException(
                "Le service de scolarité est temporairement indisponible pour l'enregistrement officiel.",
                ex
        );
    }

    private void rethrowIfBusinessException(Throwable ex) {
        if (ex instanceof ResourceNotFoundException e) {
            throw e;
        }
        if (ex instanceof BusinessRuleException e) {
            throw e;
        }
        if (ex instanceof DuplicateResourceException e) {
            throw e;
        }
    }

    private RuntimeException translateSoapFault(SoapFaultClientException ex) {
        QName faultCode = ex.getFaultCode();
        String localCode = faultCode != null ? faultCode.getLocalPart() : "";
        boolean isClientFault = "Client".equalsIgnoreCase(localCode) || "Sender".equalsIgnoreCase(localCode);

        if (!isClientFault) {
            return new ExternalServiceUnavailableException(
                    "Erreur interne du service de scolarité : " + ex.getFaultStringOrReason(),
                    ex
            );
        }

        SoapFault soapFault = ex.getSoapFault();
        SoapFaultDetail detail = soapFault != null ? soapFault.getFaultDetail() : null;
        if (detail != null) {
            Iterator<SoapFaultDetailElement> entries = detail.getDetailEntries();
            while (entries.hasNext()) {
                SoapFaultDetailElement entry = entries.next();
                try {
                    Object unmarshalled = registrarMarshaller.unmarshal(entry.getSource());
                    if (unmarshalled instanceof StudentNotFoundFault fault) {
                        return new ResourceNotFoundException(fault.getMessage());
                    }
                    if (unmarshalled instanceof AcademicIneligibilityFault fault) {
                        return new BusinessRuleException(fault.getMessage());
                    }
                    if (unmarshalled instanceof DuplicateRegistrationFault fault) {
                        return new DuplicateRegistrationFaultException(
                                fault.getStudentMatricule(),
                                fault.getExistingRegistryCode(),
                                fault.getMessage()
                        );
                    }
                } catch (Exception unmarshalError) {
                    log.warn("Impossible de désérialiser un détail SOAP Fault : {}", unmarshalError.getMessage());
                }
            }
        }

        String reason = ex.getFaultStringOrReason();
        return new BusinessRuleException(
                reason != null && !reason.isBlank() ? reason : "Rejet métier du service de scolarité."
        );
    }
}