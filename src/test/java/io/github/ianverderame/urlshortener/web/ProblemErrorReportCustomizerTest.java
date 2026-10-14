package io.github.ianverderame.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ianverderame.urlshortener.ErrorContractIT;
import java.util.Arrays;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

/**
 * The swap itself: {@link ErrorContractIT} can't see it, because the problem valve reports first
 * either way and a leftover HTML valve would then stay silent.
 */
class ProblemErrorReportCustomizerTest {

  private final StandardHost host = new StandardHost();
  private final StandardContext context = new StandardContext();
  private final TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();

  @Test
  void theHostEndsUpWithOnlyTheProblemReportValve() {
    // What Boot adds to the host before this customizer runs.
    host.getPipeline().addValve(new ErrorReportValve());
    context.setParent(host);

    new ProblemErrorReportCustomizer().customize(factory);
    factory.getContextCustomizers().forEach(customizer -> customizer.customize(context));

    assertOnlyTheProblemReportValve();
  }

  // Registered once per context, so a server where both registrations apply gets it twice.
  @Test
  void applyingItTwiceStillLeavesOneValve() {
    host.getPipeline().addValve(new ErrorReportValve());
    context.setParent(host);

    new ProblemErrorReportCustomizer().customize(factory);
    new ProblemErrorReportCustomizer().customize(factory);
    factory.getContextCustomizers().forEach(customizer -> customizer.customize(context));

    assertOnlyTheProblemReportValve();
  }

  private void assertOnlyTheProblemReportValve() {
    assertThat(
            Arrays.stream(host.getPipeline().getValves())
                .filter(ErrorReportValve.class::isInstance))
        .singleElement()
        .isInstanceOf(ProblemErrorReportValve.class);
    // So Tomcat doesn't add its own HTML valve when the host starts.
    assertThat(host.getErrorReportValveClass()).isEqualTo(ProblemErrorReportValve.class.getName());
  }
}
