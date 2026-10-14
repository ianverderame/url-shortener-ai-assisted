/**
 * HTTP concerns shared by every endpoint: RFC 7807 problem responses, including errors Tomcat
 * answers itself, and the request-body size limit. Controllers live in their feature packages, not
 * here. The features' own errors extend {@link ProblemException} and carry their own problem, so
 * this package knows none of them; it depends only on {@code security}, for the API-key header name
 * in the 401 detail. {@code ProblemErrorReportCustomizer} also serves the management server,
 * registered through the management context's imports file.
 */
package io.github.ianverderame.urlshortener.web;
