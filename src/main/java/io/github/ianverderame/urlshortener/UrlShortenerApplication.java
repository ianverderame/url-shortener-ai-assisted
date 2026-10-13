package io.github.ianverderame.urlshortener;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

// Authentication is the API key only (SecurityConfig). Without the exclusion Boot creates a default
// user and logs its generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class UrlShortenerApplication {

  public static void main(String[] args) {
    SpringApplication.run(UrlShortenerApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
