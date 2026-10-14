package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ianverderame.urlshortener.ManagementScanProbeConfig.ManagementScanProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Issue #35: {@code @ManagementContextConfiguration} classes are themselves
 * {@code @Configuration}-annotated, so the main application's component scan used to find them too
 * and register a second, independent copy of their beans in the main context. #27 guarded each such
 * class with a {@code Condition}; the scan now excludes them instead. {@link
 * ManagementScanProbeConfig} has no guard of its own, so its bean must turn up in the management
 * context and nowhere else.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "test.management-scan-probe=true")
@Import({TestcontainersConfiguration.class, ManagementContextScanIT.ManagementContextCapture.class})
class ManagementContextScanIT {

  @DynamicPropertySource
  static void apiKey(DynamicPropertyRegistry registry) {
    TestApiKey.register(registry);
  }

  @Autowired ApplicationContext mainContext;

  @Autowired ManagementContextCapture capture;

  @Test
  void managementContextConfigurationIsRegisteredInTheManagementContextOnly() {
    // getBeansOfType looks at a context's own beans only, not its parent's.
    assertThat(capture.managementContext.getBeansOfType(ManagementScanProbe.class)).hasSize(1);
    assertThat(mainContext.getBeansOfType(ManagementScanProbe.class)).isEmpty();
  }

  /**
   * Captures the management child context: a child context's events are also published to its
   * parent, so this main-context listener sees the child's refresh.
   */
  static class ManagementContextCapture implements ApplicationListener<ContextRefreshedEvent> {

    volatile ApplicationContext managementContext;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
      if (event.getApplicationContext().getParent() != null) {
        managementContext = event.getApplicationContext();
      }
    }
  }
}
