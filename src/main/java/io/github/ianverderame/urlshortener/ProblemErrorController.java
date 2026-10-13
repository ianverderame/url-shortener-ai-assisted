package io.github.ianverderame.urlshortener;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Boot's default {@code /error} body for failures that never reach a controller (a filter,
 * the servlet container), so they get the same problem shape. Only the status is used: the
 * container's error message and exception are never echoed. Hidden from the OpenAPI description: it
 * is plumbing, not part of the API.
 */
@Hidden
@RestController
class ProblemErrorController implements ErrorController {

  @RequestMapping("${server.error.path:/error}")
  ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
    HttpStatus status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
            ? HttpStatus.resolve(code)
            : null;
    if (status == null) {
      // Not an error dispatch (someone requested /error directly): there is no such resource.
      status = HttpStatus.NOT_FOUND;
    }
    return ResponseEntity.status(status).body(ProblemDetailsAdvice.problem(status, null));
  }
}
