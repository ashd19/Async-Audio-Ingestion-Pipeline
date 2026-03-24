//package com.AudioPipeline.Configuration;
//
//import org.h2.server.web.WebServlet;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.boot.web.servlet.ServletRegistrationBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//@ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "true", matchIfMissing = false)
//public class H2ConsoleConfiguration {
//
//    @Bean
//    public ServletRegistrationBean<WebServlet> h2Console() {
//        String path = "/h2-console";
//        String pathSpec = path + "/*";
//
//        ServletRegistrationBean<WebServlet> registration = new ServletRegistrationBean<>(new WebServlet());
//        registration.addUrlMappings(pathSpec);
//        registration.setLoadOnStartup(1);
//
//        return registration;
//    }
//}
