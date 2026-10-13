package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Issue #26: the body cap rejects an oversized body without reading it in full, whether the length
 * is declared up front or the body is streamed. That a rejection is a 413 problem on the wire, and
 * comes after the key check, is covered by {@link RequestBodyLimitIT}.
 */
class RequestBodyLimitTest {

  private static final int CAP = 8 * 1024;

  private final AtomicReference<Exception> resolved = new AtomicReference<>();

  // Stands in for the MVC exception resolvers, which turn the exception into the problem response.
  private final HandlerExceptionResolver resolver =
      (request, response, handler, e) -> {
        resolved.set(e);
        return new ModelAndView();
      };

  @Test
  void aDeclaredLengthOverTheCapIsRejectedWithoutReadingTheBody() throws Exception {
    CountingInputStream body = new CountingInputStream(CAP + 1);
    MockHttpServletRequest request = streamingRequest(body, CAP + 1);
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, resolver)
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(resolved.get()).isInstanceOf(RequestBodyTooLargeException.class);
    assertThat(chain.getRequest()).as("request passed on").isNull();
    assertThat(body.bytesRead).isZero();
  }

  @Test
  void aStreamedBodyOverTheCapIsRejectedAfterReadingAtMostOneByteMoreThanTheCap() throws Exception {
    CountingInputStream body = new CountingInputStream(50 * 1024 * 1024);
    MockHttpServletRequest request = streamingRequest(body, -1);
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, resolver)
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(resolved.get()).isInstanceOf(RequestBodyTooLargeException.class);
    assertThat(chain.getRequest()).as("request passed on").isNull();
    assertThat(body.bytesRead).isLessThanOrEqualTo(CAP + 1);
  }

  @Test
  void aStreamedBodyOfExactlyTheCapIsPassedOnIntact() throws Exception {
    byte[] content = new byte[CAP];
    Arrays.fill(content, (byte) ' ');
    MockHttpServletRequest request = streamingRequest(new ByteArrayInputStream(content), -1);
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, resolver)
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(resolved.get()).isNull();
    assertThat(chain.getRequest()).as("request passed on").isNotNull();
    assertThat(chain.getRequest().getInputStream().readAllBytes()).isEqualTo(content);
  }

  @Test
  void aDeclaredBodyOfExactlyTheCapIsPassedOnIntact() throws Exception {
    String content = "x".repeat(CAP);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/links");
    request.setCharacterEncoding("UTF-8");
    request.setContent(content.getBytes(StandardCharsets.UTF_8));
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, resolver)
        .doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(resolved.get()).isNull();
    assertThat(chain.getRequest()).as("request passed on").isNotNull();
    assertThat(chain.getRequest().getReader().readLine()).isEqualTo(content);
  }

  @Test
  void thePassedOnBodyReadsLikeAnAlreadyArrivedStream() throws Exception {
    byte[] content = {'a', 'b'};
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, resolver)
        .doFilter(
            streamingRequest(new ByteArrayInputStream(content), -1),
            new MockHttpServletResponse(),
            chain);

    ServletInputStream passedOn = chain.getRequest().getInputStream();
    assertThat(passedOn.isReady()).isTrue();
    assertThat(passedOn.isFinished()).isFalse();
    assertThat(passedOn.read()).isEqualTo('a');
    assertThat(passedOn.read()).isEqualTo('b');
    assertThat(passedOn.isFinished()).isTrue();
    assertThat(passedOn.read()).isEqualTo(-1);
  }

  // The servlet default when the request names no charset.
  @Test
  void thePassedOnReaderDecodesAsLatin1WhenNoCharsetIsGiven() throws Exception {
    byte[] content = {(byte) 0xE9};
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, resolver)
        .doFilter(
            streamingRequest(new ByteArrayInputStream(content), -1),
            new MockHttpServletResponse(),
            chain);

    assertThat(chain.getRequest().getReader().readLine()).isEqualTo("é");
  }

  @Test
  void anUnresolvedRejectionStillFailsClosedWithPayloadTooLarge() throws Exception {
    MockHttpServletRequest request = streamingRequest(new CountingInputStream(CAP + 1), CAP + 1);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    new RequestBodyLimitFilter(CAP, (req, res, handler, e) -> null)
        .doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    assertThat(chain.getRequest()).as("request passed on").isNull();
  }

  private static MockHttpServletRequest streamingRequest(InputStream body, long contentLength) {
    ServletInputStream stream = new DelegatingServletInputStream(body);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/links") {
          @Override
          public ServletInputStream getInputStream() {
            return stream;
          }

          @Override
          public long getContentLengthLong() {
            return contentLength;
          }

          @Override
          public int getContentLength() {
            return (int) contentLength;
          }
        };
    request.setContentType("application/json");
    return request;
  }

  /** An endless-looking body that records how much of it was read. */
  private static final class CountingInputStream extends InputStream {

    private final long size;
    long bytesRead;

    CountingInputStream(long size) {
      this.size = size;
    }

    @Override
    public int read() throws IOException {
      if (bytesRead >= size) {
        return -1;
      }
      bytesRead++;
      return ' ';
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (bytesRead >= size) {
        return -1;
      }
      int n = (int) Math.min(len, size - bytesRead);
      Arrays.fill(b, off, off + n, (byte) ' ');
      bytesRead += n;
      return n;
    }
  }
}
