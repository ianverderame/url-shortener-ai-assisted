/**
 * Configuration for the management (actuator) context on its own port. Classes here are
 * {@code @ManagementContextConfiguration}s, which the main component scan excludes; they are
 * registered through {@code META-INF/spring/...ManagementContextConfiguration.imports}, as is
 * {@code web.ProblemErrorReportCustomizer} for the management server. Depends on no other package
 * here.
 */
package io.github.ianverderame.urlshortener.management;
