package io.github.ianverderame.urlshortener;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Registers a bean in the management child context that never finishes stopping, active only when
 * {@code test.hang-management-shutdown=true}. Used by {@link ShutdownTimeoutBudgetIT} to observe
 * how long the management context's own shutdown-phase timeout actually bounds a close(). Wired in
 * through {@code META-INF/spring/...ManagementContextConfiguration.imports}, the same extension
 * point the production fix uses, so it only affects tests that opt in.
 *
 * <p>{@code ManagementContextType.CHILD} only tells Spring Boot's own import selector which context
 * to add this class to when wiring the management context deliberately. It does nothing to stop the
 * main application's ordinary classpath component scan from also finding this class, since
 * {@code @ManagementContextConfiguration} is itself {@code @Configuration}-annotated and this class
 * sits in the scanned package. Without {@link OnChildApplicationContextCondition}, that scan
 * registers a second, independent hanging bean in the main context too, which is why an earlier
 * version of this test saw two separate "still running" log lines 20 s then 10 s apart.
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
@ConditionalOnProperty("test.hang-management-shutdown")
@Conditional(OnChildApplicationContextCondition.class)
class HangingManagementBeanConfig {

  @Bean
  SmartLifecycle hangingManagementBean() {
    return new SmartLifecycle() {
      private volatile boolean running;

      @Override
      public void start() {
        running = true;
      }

      @Override
      public void stop() {
        running = false;
      }

      @Override
      public void stop(Runnable callback) {
        // Deliberately never invokes callback, simulating a dependency that never finishes
        // stopping.
        running = false;
      }

      @Override
      public boolean isRunning() {
        return running;
      }
    };
  }
}
