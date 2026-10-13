package io.github.ianverderame.urlshortener;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Basic API-key check on the link API. It is registered against Spring MVC's own path matching (see
 * {@link WebConfig}), so every spelling of a path that reaches a link endpoint is checked. A blank
 * configured key rejects every request. Not constant-time and not RFC 7807 formatted yet; hardening
 * both is issue #5.
 */
@Component
class ApiKeyInterceptor implements HandlerInterceptor {

  private static final String HEADER = "X-API-Key";

  private final String apiKey;

  ApiKeyInterceptor(@Value("${app.api-key:}") String apiKey) {
    this.apiKey = apiKey;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (apiKey.isBlank() || !apiKey.equals(request.getHeader(HEADER))) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }
    return true;
  }
}
