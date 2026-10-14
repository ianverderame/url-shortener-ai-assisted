package io.github.ianverderame.urlshortener.web;

import java.io.IOException;
import java.io.Writer;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Tomcat's last-resort error page, as a problem instead of HTML. It only answers errors that never
 * reached Spring, such as a request Tomcat rejects before any servlet runs (an illegal character or
 * an encoded slash in the path). The body is type, title, and status only: nothing from the request
 * is echoed.
 */
class ProblemErrorReportValve extends ErrorReportValve {

  @Override
  protected void report(Request request, Response response, Throwable throwable) {
    int status = response.getStatus();
    // The same guards as the HTML report: only errors, only once, never over a written body.
    if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
      return;
    }
    HttpStatus resolved = HttpStatus.resolve(status);
    String title = resolved != null ? resolved.getReasonPhrase() : "Error";
    try {
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      Writer writer = response.getReporter();
      if (writer != null) {
        writer.write(
            "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
        response.finishResponse();
      }
    } catch (IOException | IllegalStateException e) {
      // The connection is gone or the response is already committed; nothing more can be sent.
    }
  }
}
