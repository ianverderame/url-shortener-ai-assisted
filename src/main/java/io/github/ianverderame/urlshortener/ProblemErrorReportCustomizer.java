package io.github.ianverderame.urlshortener;

import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;

/**
 * Swaps Tomcat's HTML error report for {@link ProblemErrorReportValve}. Runs last so it replaces
 * the report valve Boot adds, and names the class on the host so Tomcat doesn't add its own at
 * startup. Applying it twice leaves the same single valve.
 *
 * <p>Each Tomcat needs its own registration: {@link TomcatErrorReportConfig} for the application
 * server, and {@code META-INF/spring/...ManagementContextConfiguration.imports} for the separate
 * management server, whose child context doesn't see the application's customizers.
 */
class ProblemErrorReportCustomizer
    implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, Ordered {

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    factory.addContextCustomizers(
        context -> {
          StandardHost host = (StandardHost) context.getParent();
          for (Valve valve : host.getPipeline().getValves()) {
            if (valve instanceof ErrorReportValve) {
              host.getPipeline().removeValve(valve);
            }
          }
          host.getPipeline().addValve(new ProblemErrorReportValve());
          host.setErrorReportValveClass(ProblemErrorReportValve.class.getName());
        });
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
