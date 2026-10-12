package kz.su.currency.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted().reduce((left, right) -> left + "; " + right)
                .orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleJson(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid JSON or field type");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> handleDatabase(DataAccessException exception) {
        log.error("Database operation failed", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Database is unavailable");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
