package io.github.ianverderame.urlshortener;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

  private final ApiKeyInterceptor apiKeyInterceptor;

  WebConfig(ApiKeyInterceptor apiKeyInterceptor) {
    this.apiKeyInterceptor = apiKeyInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(apiKeyInterceptor).addPathPatterns("/api/**");
  }
}
