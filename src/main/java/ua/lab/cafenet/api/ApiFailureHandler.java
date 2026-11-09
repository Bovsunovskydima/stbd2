package ua.lab.cafenet.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ua.lab.cafenet.persistence.NotFoundException;
import ua.lab.cafenet.persistence.RuleViolationException;
import ua.lab.cafenet.persistence.StorageException;

import java.util.List;

@RestControllerAdvice
public class ApiFailureHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiFailure> missing(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), List.of());
    }

    @ExceptionHandler(RuleViolationException.class)
    public ResponseEntity<ApiFailure> rule(RuleViolationException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiFailure> invalid(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return body(HttpStatus.BAD_REQUEST, "Некоректні дані запиту", fields);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiFailure> illegal(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), List.of());
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiFailure> storage(StorageException e) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), List.of());
    }

    private ResponseEntity<ApiFailure> body(HttpStatus status, String message, List<String> fields) {
        return ResponseEntity.status(status)
                .body(new ApiFailure(status.value(), status.getReasonPhrase(), message, fields));
    }
}
