package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;

/**
 * {@link UrlShortenerApplication} spells out {@code @SpringBootApplication} to add one exclude
 * filter (#35), so its copy of Boot's own filters must keep up when Boot is upgraded.
 */
class UrlShortenerApplicationTest {

  @Test
  void componentScanKeepsSpringBootApplicationsExcludeFiltersAndAddsOne() {
    Filter[] boot = SpringBootApplication.class.getAnnotation(ComponentScan.class).excludeFilters();
    Filter[] ours =
        UrlShortenerApplication.class.getAnnotation(ComponentScan.class).excludeFilters();

    assertThat(ours).hasSize(boot.length + 1).contains(boot);
  }
}
