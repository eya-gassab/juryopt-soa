package com.juryopt.registrar.endpoint;

import com.juryopt.registrar.config.WebServiceConfig;
import com.juryopt.registrar.service.RegistrarService;
import com.juryopt.registrar.ws.gen.RegisterOfficialDefenseRequest;
import com.juryopt.registrar.ws.gen.RegisterOfficialDefenseResponse;
import com.juryopt.registrar.ws.gen.VerifyStudentEligibilityRequest;
import com.juryopt.registrar.ws.gen.VerifyStudentEligibilityResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class RegistrarSoapEndpoint {

    private final RegistrarService registrarService;

    public RegistrarSoapEndpoint(RegistrarService registrarService) {
        this.registrarService = registrarService;
    }

    @PayloadRoot(namespace = WebServiceConfig.NAMESPACE_URI, localPart = "VerifyStudentEligibilityRequest")
    @ResponsePayload
    public VerifyStudentEligibilityResponse verifyStudentEligibility(
            @RequestPayload VerifyStudentEligibilityRequest request) {
        return registrarService.verifyEligibility(request.getStudentMatricule());
    }

    @PayloadRoot(namespace = WebServiceConfig.NAMESPACE_URI, localPart = "RegisterOfficialDefenseRequest")
    @ResponsePayload
    public RegisterOfficialDefenseResponse registerOfficialDefense(
            @RequestPayload RegisterOfficialDefenseRequest request) {
        return registrarService.registerOfficialDefense(request);
    }
}