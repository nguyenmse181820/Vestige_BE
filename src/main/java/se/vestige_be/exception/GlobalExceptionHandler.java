package se.vestige_be.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import se.vestige_be.dto.response.ObjectResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ObjectResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.NOT_FOUND.toString())
                        .message(ex.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ObjectResponse> handleBusinessLogic(BusinessLogicException ex) {
        log.warn("Business logic violation: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.toString())
                        .message(ex.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ObjectResponse> handleUnauthorized(UnauthorizedException ex) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.UNAUTHORIZED.toString())
                        .message(ex.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<Object> handleTokenRefreshException(
            TokenRefreshException ex, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Forbidden");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).substring(4));

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(TokenPossibleCompromiseException.class)
    public ResponseEntity<Object> handleTokenCompromiseException(
            TokenPossibleCompromiseException ex, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Security Alert");
        body.put("message", "Session invalidated due to security concerns");
        body.put("path", request.getDescription(false).substring(4));

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentialsException(
            BadCredentialsException ex, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("message", "Invalid username or password");
        body.put("path", request.getDescription(false).substring(4));

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        response.put("status", HttpStatus.BAD_REQUEST.toString());
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ObjectResponse> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.NOT_FOUND.toString())
                        .message(ex.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            errors.put(propertyPath, violation.getMessage());
        });

        response.put("status", HttpStatus.BAD_REQUEST.toString());
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ObjectResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid parameter type: " + ex.getMessage();

        log.warn("Type mismatch: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.toString())
                        .message(message)
                        .data(null)
                        .build());
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ObjectResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.FORBIDDEN.toString())
                        .message("Access Denied: You do not have the required permissions to perform this action.")
                        .data(null)
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ObjectResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.toString())
                        .message(ex.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ObjectResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);

        // Check message content to determine appropriate response
        String message = ex.getMessage().toLowerCase();

        if (message.contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ObjectResponse.builder()
                            .status(HttpStatus.NOT_FOUND.toString())
                            .message(ex.getMessage())
                            .data(null)
                            .build());
        }

        if (message.contains("unauthorized") || message.contains("permission") || message.contains("access denied")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ObjectResponse.builder()
                            .status(HttpStatus.UNAUTHORIZED.toString())
                            .message(ex.getMessage())
                            .data(null)
                            .build());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.toString())
                        .message(ex.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ObjectResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.toString())
                        .message("An unexpected error occurred")
                        .data(null)
                        .build());
    }
}