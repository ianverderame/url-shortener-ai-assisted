package io.github.ianverderame.urlshortener.redirect;

import io.github.ianverderame.urlshortener.analytics.ClickRecorder;
import io.github.ianverderame.urlshortener.link.Link;
import io.github.ianverderame.urlshortener.link.LinkNotFoundException;
import io.github.ianverderame.urlshortener.link.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public: no API key. The OpenAPI annotations describe its responses; they don't change them. */
@RestController
class RedirectController {

  private final LinkService linkService;
  private final ClickRecorder clickRecorder;

  RedirectController(LinkService linkService, ClickRecorder clickRecorder) {
    this.linkService = linkService;
    this.clickRecorder = clickRecorder;
  }

  /** HEAD is mapped here implicitly; only a GET counts as a click. */
  @GetMapping("/{code:[0-9A-Za-z]{8}}")
  @Operation(summary = "Follow a short link")
  @ApiResponse(
      responseCode = "302",
      description = "Redirects to the link's destination.",
      headers =
          @Header(
              name = HttpHeaders.LOCATION,
              description = "The destination URL.",
              schema = @Schema(type = "string", format = "uri")))
  @ApiResponse(
      responseCode = "404",
      description = "No link has this code.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  ResponseEntity<Void> redirect(@PathVariable String code, HttpMethod method) {
    Link link = linkService.resolve(code).orElseThrow(LinkNotFoundException::new);
    if (HttpMethod.GET.equals(method)) {
      clickRecorder.record(link.id());
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.destination()))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .build();
  }
}
