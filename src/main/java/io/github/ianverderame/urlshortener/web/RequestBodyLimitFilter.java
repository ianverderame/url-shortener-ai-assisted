package io.github.ianverderame.urlshortener.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Caps request bodies before anything parses them. A declared length over the cap is rejected
 * without reading the body; a streamed body is read up to one byte past the cap and rejected there.
 * A body within the cap is passed on from memory. A rejection is handed to the MVC exception
 * resolvers, so it gets the same problem response as every other error.
 *
 * <p>Registered after Spring Security's filter chain (see {@link RequestBodyLimitConfig}), so a
 * request without a valid key gets its 401 before any of its body is read.
 */
class RequestBodyLimitFilter extends OncePerRequestFilter {

  private final int maxBytes;
  private final HandlerExceptionResolver exceptionResolver;

  RequestBodyLimitFilter(int maxBytes, HandlerExceptionResolver exceptionResolver) {
    this.maxBytes = maxBytes;
    this.exceptionResolver = exceptionResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    byte[] body =
        request.getContentLengthLong() > maxBytes
            ? null
            : request.getInputStream().readNBytes(maxBytes + 1);
    if (body == null || body.length > maxBytes) {
      reject(request, response);
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    RequestBodyTooLargeException e = new RequestBodyTooLargeException(maxBytes);
    if (exceptionResolver.resolveException(request, response, null, e) == null) {
      // Fail closed: never let an unresolved rejection through as a 200.
      response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }
  }

  /** The request with its already-read body served from memory. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return in.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("The body is already read.");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
  }
}
