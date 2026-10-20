package com.aiplatform.config;

import com.aiplatform.dao.ExperimentDao;
import com.aiplatform.repository.TrainingJobRepository;
import com.aiplatform.web.ExportServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the plain ExportServlet with the embedded Tomcat server, next to Spring's own DispatcherServlet. */
@Configuration
public class ServletConfig {

    @Bean
    ServletRegistrationBean<ExportServlet> exportServlet(TrainingJobRepository trainingJobs, ExperimentDao experiments) {
        ServletRegistrationBean<ExportServlet> registration =
                new ServletRegistrationBean<>(new ExportServlet(trainingJobs, experiments), "/export/*");
        registration.setName("exportServlet");
        return registration;
    }
}
