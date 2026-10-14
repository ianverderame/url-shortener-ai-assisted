package io.github.ianverderame.urlshortener.management;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * A management-context configuration with no guard of its own against the main application's
 * component scan, active only when {@code test.management-scan-probe=true}. Used by {@link
 * ManagementContextScanIT} to observe which contexts its bean ends up in. Wired in through {@code
 * META-INF/spring/...ManagementContextConfiguration.imports}, like the production classes.
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
@ConditionalOnProperty("test.management-scan-probe")
class ManagementScanProbeConfig {

  @Bean
  ManagementScanProbe managementScanProbe() {
    return new ManagementScanProbe();
  }

  static final class ManagementScanProbe {}
}
