package io.github.ianverderame.urlshortener.analytics;

import io.github.ianverderame.urlshortener.security.ApiKeyAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The OpenAPI annotations describe its responses; they don't change them. */
@RestController
@RequestMapping("/api/v1/links")
class LinkStatsController {

  private final LinkStatsService linkStatsService;

  LinkStatsController(LinkStatsService linkStatsService) {
    this.linkStatsService = linkStatsService;
  }

  @GetMapping("/{code:[0-9A-Za-z]{8}}/stats")
  @Operation(
      summary = "Read a link's clicks per UTC day",
      security = @SecurityRequirement(name = ApiKeyAuthenticationFilter.API_KEY_SCHEME))
  @ApiResponse(
      responseCode = "200",
      description = "The range total and every day in the range, oldest first, zero-filled.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LinkStats.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "A date is malformed or before 0001-01-01, the range spans more than 366 days, 'from'"
              + " is after 'to', or 'to' is after today (UTC).",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "401",
      description = "The X-API-Key header is missing or wrong.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No link has this code.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  LinkStats stats(
      @PathVariable String code,
      @Parameter(description = "First UTC day, inclusive. Defaults to 29 days before 'to'.")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate from,
      @Parameter(description = "Last UTC day, inclusive. Defaults to today (UTC).")
          @RequestParam(required = false)
          @DateTimeFormat(iso = ISO.DATE)
          LocalDate to) {
    return linkStatsService.stats(code, from, to);
  }
}
