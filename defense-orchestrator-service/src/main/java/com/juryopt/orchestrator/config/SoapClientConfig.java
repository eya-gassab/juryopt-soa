package com.juryopt.orchestrator.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Configuration
public class SoapClientConfig {

    @Bean
    public Jaxb2Marshaller registrarMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.juryopt.orchestrator.client.soap.gen");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate registrarWebServiceTemplate(
            Jaxb2Marshaller registrarMarshaller,
            @Value("${juryopt.soap.registrar-url}") String registrarUrl,
            @Value("${juryopt.soap.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${juryopt.soap.read-timeout-ms:3000}") long readTimeoutMs) {

        HttpUrlConnectionMessageSender messageSender = new HttpUrlConnectionMessageSender();
        messageSender.setConnectionTimeout(Duration.ofMillis(connectTimeoutMs));
        messageSender.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        WebServiceTemplate template = new WebServiceTemplate();
        template.setDefaultUri(registrarUrl);
        template.setMarshaller(registrarMarshaller);
        template.setUnmarshaller(registrarMarshaller);
        template.setMessageSender(messageSender);
        return template;
    }
}