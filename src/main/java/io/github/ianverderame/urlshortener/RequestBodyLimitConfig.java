package io.github.ianverderame.urlshortener;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The 8 KiB body cap on the API (decided on #9): the destination is capped at 2,048 characters, so
 * this leaves about four times headroom. Ordered last, after Spring Security's filter chain, so the
 * key check always comes first.
 */
@Configuration(proxyBeanMethods = false)
class RequestBodyLimitConfig {

  static final int MAX_BODY_BYTES = 8 * 1024;

  @Bean
  FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    FilterRegistrationBean<RequestBodyLimitFilter> registration =
        new FilterRegistrationBean<>(new RequestBodyLimitFilter(MAX_BODY_BYTES, exceptionResolver));
    registration.addUrlPatterns("/api/*");
    registration.setOrder(Ordered.LOWEST_PRECEDENCE);
    return registration;
  }
}
