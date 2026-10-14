package io.github.ianverderame.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.ianverderame.urlshortener.ErrorContractIT;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.catalina.connector.CoyoteAdapter;
import org.apache.catalina.connector.Response;
import org.apache.coyote.OutputBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The valve's report against a real Tomcat response whose bytes are captured. The end-to-end path
 * (a request Tomcat rejects before any servlet) is in {@link ErrorContractIT}.
 */
class ProblemErrorReportValveTest {

  private final CapturedBytes sent = new CapturedBytes();
  private final Response response = newResponse(sent);
  private final ProblemErrorReportValve valve = new ProblemErrorReportValve();

  @ParameterizedTest
  @CsvSource({"400, Bad Request", "505, HTTP Version not supported", "599, Error"})
  void anUnreportedErrorBecomesAProblemWithTypeTitleAndStatusOnly(int status, String title) {
    response.setStatus(status);
    response.setError();

    valve.report(null, response, null);

    assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
    assertThat(sent.text())
        .isEqualTo(
            "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
  }

  @Test
  void aSuccessStatusIsLeftAlone() {
    response.setStatus(399);
    response.setError();

    valve.report(null, response, null);

    assertThat(response.getContentType()).isNull();
    assertThat(sent.text()).isEmpty();
  }

  @Test
  void anErrorThatWasNotFlaggedForReportingIsLeftAlone() {
    response.setStatus(400);

    valve.report(null, response, null);

    assertThat(sent.text()).isEmpty();
  }

  @Test
  void anErrorIsReportedOnlyOnce() {
    response.setStatus(400);
    response.setError();

    valve.report(null, response, null);
    valve.report(null, response, null);

    assertThat(sent.text())
        .isEqualTo("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400}");
  }

  private static Response newResponse(CapturedBytes sent) {
    org.apache.coyote.Response coyote = new org.apache.coyote.Response();
    coyote.setOutputBuffer(sent);
    // Tomcat's own pairing, as its connector sets it up: closing the output also closes the input.
    org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
    coyoteRequest.setResponse(coyote);
    coyoteRequest.setNote(
        CoyoteAdapter.ADAPTER_NOTES,
        new org.apache.catalina.connector.Request(null, coyoteRequest));
    return new Response(coyote);
  }

  /** Stands in for the connection: keeps whatever the response writes. */
  static class CapturedBytes implements OutputBuffer {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    @Override
    public int doWrite(ByteBuffer chunk) {
      int length = chunk.remaining();
      byte[] copy = new byte[length];
      chunk.get(copy);
      bytes.write(copy, 0, length);
      return length;
    }

    @Override
    public long getBytesWritten() {
      return bytes.size();
    }

    String text() {
      return bytes.toString(StandardCharsets.UTF_8);
    }
  }
}
