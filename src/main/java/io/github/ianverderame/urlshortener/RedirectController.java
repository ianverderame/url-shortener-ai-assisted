package io.github.ianverderame.urlshortener;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RedirectController {

  private final LinkService linkService;

  RedirectController(LinkService linkService) {
    this.linkService = linkService;
  }

  @GetMapping("/{code:[0-9A-Za-z]{8}}")
  ResponseEntity<Void> redirect(@PathVariable String code) {
    return linkService
        .resolve(code)
        .map(
            link ->
                ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(link.destination()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .<Void>build())
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
