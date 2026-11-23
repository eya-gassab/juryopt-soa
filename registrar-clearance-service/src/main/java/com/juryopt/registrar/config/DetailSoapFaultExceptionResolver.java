package com.juryopt.registrar.config;

import com.juryopt.registrar.exception.RegistrarFaultException;
import com.juryopt.registrar.ws.gen.AcademicIneligibilityFault;
import com.juryopt.registrar.ws.gen.DuplicateRegistrationFault;
import com.juryopt.registrar.ws.gen.StudentNotFoundFault;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;

public class DetailSoapFaultExceptionResolver extends SoapFaultMappingExceptionResolver {

    private final JAXBContext jaxbContext;

    public DetailSoapFaultExceptionResolver() {
        try {
            this.jaxbContext = JAXBContext.newInstance(
                    StudentNotFoundFault.class,
                    AcademicIneligibilityFault.class,
                    DuplicateRegistrationFault.class
            );
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXBContext for SOAP faults", e);
        }
    }

    @Override
    protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
        if (ex instanceof RegistrarFaultException faultEx) {
            try {
                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
                marshaller.marshal(faultEx.getFaultDetail(), fault.addFaultDetail().getResult());
            } catch (JAXBException e) {
                throw new IllegalStateException("Failed to marshal SOAP fault detail", e);
            }
        }
    }
}