package io.github.ianverderame.urlshortener.link;

import io.github.ianverderame.urlshortener.security.ApiKeyAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The OpenAPI annotations describe its responses; they don't change them. */
@RestController
@RequestMapping("/api/v1/links")
@SecurityScheme(
    name = ApiKeyAuthenticationFilter.API_KEY_SCHEME,
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = ApiKeyAuthenticationFilter.HEADER)
class LinkController {

  private final LinkService linkService;
  private final PublicBaseUrl publicBaseUrl;

  LinkController(LinkService linkService, PublicBaseUrl publicBaseUrl) {
    this.linkService = linkService;
    this.publicBaseUrl = publicBaseUrl;
  }

  @PostMapping
  @Operation(
      summary = "Create a short link",
      security = @SecurityRequirement(name = ApiKeyAuthenticationFilter.API_KEY_SCHEME))
  @ApiResponse(
      responseCode = "201",
      description = "The link was created.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = LinkResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "The body is malformed, has an unknown property, or the destination is rejected.",
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
      responseCode = "413",
      description = "The body is larger than 8 KiB.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "415",
      description = "The body is not application/json.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "503",
      description = "No free short code could be generated; retry later.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ProblemDetail.class)))
  ResponseEntity<LinkResponse> create(@RequestBody CreateLinkRequest request) {
    Link link = linkService.create(request.destination());
    LinkResponse response =
        new LinkResponse(
            link.code(), publicBaseUrl.shortUrl(link.code()), link.destination(), link.createdAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
