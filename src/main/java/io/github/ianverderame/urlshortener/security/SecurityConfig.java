package io.github.ianverderame.urlshortener.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The link API ({@code /api/**}) requires the API key; everything else (redirects, docs, health and
 * metrics on the management port, {@code /error}) is public. Stateless: no sessions, cookies, or
 * CSRF tokens.
 *
 * <p>Paths are matched with {@link PathPatternRequestMatcher}, which decodes the path and drops
 * matrix parameters the way Spring MVC routing does, so no spelling of an API path skips the key
 * check. A 401 is handed to the MVC exception resolvers, so it gets the same problem response as
 * every other error.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ApiKey apiKey,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver)
      throws Exception {
    AuthenticationEntryPoint problemEntryPoint =
        (request, response, e) -> {
          if (exceptionResolver.resolveException(request, response, null, e) == null) {
            // Fail closed: never let an unresolved authentication failure through as a 200.
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
          }
        };
    // CSRF is off on purpose (CodeQL java/spring-disabled-csrf-protection, dismissed as a false
    // positive): the only credential is the X-API-Key header, which browsers never attach on their
    // own, and there are no cookies, sessions, or allowed cross-origin callers. CsrfAssumptionsIT
    // pins those assumptions; adding cookie or session auth, or CORS, means turning CSRF back on.
    return http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new ApiKeyAuthenticationFilter(apiKey), AuthorizationFilter.class)
        .exceptionHandling(e -> e.authenticationEntryPoint(problemEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .build();
  }
}
