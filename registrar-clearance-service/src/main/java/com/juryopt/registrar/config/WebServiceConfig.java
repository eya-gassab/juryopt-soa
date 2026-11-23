package com.juryopt.registrar.config;

import com.juryopt.registrar.exception.RegistrarFaultException;
import java.util.Properties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    public static final String NAMESPACE_URI = "http://juryopt.com/registrar";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "registrar")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema registrarSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("RegistrarPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NAMESPACE_URI);
        wsdl.setSchema(registrarSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema registrarSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/registrar.xsd"));
    }

    @Bean
    public SoapFaultMappingExceptionResolver exceptionResolver() {
        DetailSoapFaultExceptionResolver resolver = new DetailSoapFaultExceptionResolver();

        SoapFaultDefinition defaultFault = new SoapFaultDefinition();
        defaultFault.setFaultCode(SoapFaultDefinition.SERVER);
        resolver.setDefaultFault(defaultFault);

        Properties mappings = new Properties();
        mappings.setProperty(RegistrarFaultException.class.getName(), SoapFaultDefinition.CLIENT.toString());
        resolver.setExceptionMappings(mappings);
        resolver.setOrder(1);
        return resolver;
    }
}