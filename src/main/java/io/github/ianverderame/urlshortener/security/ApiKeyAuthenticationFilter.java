package io.github.ianverderame.urlshortener.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request that carries the configured key in {@code X-API-Key}. A missing or wrong
 * key leaves the request unauthenticated; whether that matters is decided by the authorization
 * rules in {@link SecurityConfig}. Not a bean, so the servlet container doesn't register it twice.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-API-Key";

  /**
   * The name of the OpenAPI security scheme, declared on {@link SecurityConfig}, that documents
   * {@link #HEADER} on the endpoints needing it.
   */
  public static final String API_KEY_SCHEME = "apiKey";

  private final ApiKey apiKey;
  private final SecurityContextHolderStrategy contextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  ApiKeyAuthenticationFilter(ApiKey apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (apiKey.matches(request.getHeader(HEADER))) {
      SecurityContext context = contextHolder.createEmptyContext();
      context.setAuthentication(
          UsernamePasswordAuthenticationToken.authenticated("api-client", null, List.of()));
      contextHolder.setContext(context);
    }
    chain.doFilter(request, response);
  }
}
