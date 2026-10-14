package io.github.ianverderame.urlshortener.management;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/**
 * Registers a bean in the management child context that never finishes stopping, active only when
 * {@code test.hang-management-shutdown=true}. Used by {@link ShutdownTimeoutBudgetIT} to observe
 * how long the management context's own shutdown-phase timeout actually bounds a close(). Wired in
 * through {@code META-INF/spring/...ManagementContextConfiguration.imports}, the same extension
 * point the production fix uses, so it only affects tests that opt in. The main application's
 * component scan excludes management-context classes ({@link ManagementContextScanIT}), so the
 * hanging bean exists in the management context only.
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
@ConditionalOnProperty("test.hang-management-shutdown")
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
