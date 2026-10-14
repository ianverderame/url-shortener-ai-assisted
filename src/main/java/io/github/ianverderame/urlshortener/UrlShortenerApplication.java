package io.github.ianverderame.urlshortener;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

// @SpringBootApplication spelled out, to add one exclude filter to its component scan. Management-
// context classes are also @Configuration, so the scan would otherwise register a second copy of
// their beans in the main context (ManagementContextScanIT); they belong only in the management
// context, via META-INF/spring/...ManagementContextConfiguration.imports. The first two filters are
// @SpringBootApplication's own.
//
// Authentication is the API key only (SecurityConfig). Without the exclusion Boot creates a default
// user and logs its generated password.
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@ComponentScan(
    excludeFilters = {
      @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
      @Filter(type = FilterType.ANNOTATION, classes = ManagementContextConfiguration.class)
    })
public class UrlShortenerApplication {

  public static void main(String[] args) {
    SpringApplication.run(UrlShortenerApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
