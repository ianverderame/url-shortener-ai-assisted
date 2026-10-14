package io.github.ianverderame.urlshortener.management;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.DefaultLifecycleProcessor;

/**
 * Sets the management context's shutdown-phase timeout explicitly (issue #27). Without this, the
 * management context never gets {@code spring.lifecycle.timeout-per-shutdown-phase}, which only
 * reaches the main context's {@code LifecycleAutoConfiguration}; it falls back to Spring
 * Framework's own {@code DefaultLifecycleProcessor} default of 10 s instead. The 3 s here is the
 * management share of the 10 s "web shutdown" budget from #9 (main gets the rest, 7 s, via {@code
 * application.properties}); management has only its own Tomcat connector and actuator endpoints to
 * stop, no in-flight business requests or database calls.
 *
 * <p>Wired in via {@code META-INF/spring/...ManagementContextConfiguration.imports} rather than
 * {@code @Import}, because the management context is a separate {@code ApplicationContext} that
 * doesn't share the main context's bean definitions.
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
class ManagementShutdownConfiguration {

  static final long MANAGEMENT_SHUTDOWN_TIMEOUT_MS = 3000;

  @Bean(name = "lifecycleProcessor")
  DefaultLifecycleProcessor managementLifecycleProcessor() {
    DefaultLifecycleProcessor processor = new DefaultLifecycleProcessor();
    processor.setTimeoutPerShutdownPhase(MANAGEMENT_SHUTDOWN_TIMEOUT_MS);
    return processor;
  }
}
