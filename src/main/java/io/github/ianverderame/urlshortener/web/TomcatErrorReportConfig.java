package io.github.ianverderame.urlshortener.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Problem responses for errors Tomcat answers itself, on the application server. */
@Configuration(proxyBeanMethods = false)
class TomcatErrorReportConfig {

  @Bean
  ProblemErrorReportCustomizer problemErrorReportCustomizer() {
    return new ProblemErrorReportCustomizer();
  }
}
