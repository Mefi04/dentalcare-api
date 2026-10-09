package com.dentalcare.api.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.dentalcare.api.shared.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(PayloadTooLargeException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(UnsupportedMediaTypeException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    ResponseEntity<ApiErrorResponse> handleUnprocessableEntity(UnprocessableEntityException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(GoneException.class)
    ResponseEntity<ApiErrorResponse> handleGone(GoneException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.GONE, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentNotFoundInStorageException.class)
    ResponseEntity<ApiErrorResponse> handleDocumentNotFoundInStorage(
            com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentNotFoundInStorageException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageDisabledException.class,
            com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageUnavailableException.class
    })
    ResponseEntity<ApiErrorResponse> handleDocumentStorageUnavailable(
            com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException.class)
    ResponseEntity<ApiErrorResponse> handleDocumentStorage(
            com.dentalcare.api.modules.clinicalrecords.storage.exception.DocumentStorageException exception, HttpServletRequest request) {
        LOGGER.error("Document storage operation failed while processing {} {}: {}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to process clinical document storage operation", request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of(), exception.getCode());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException exception, HttpServletRequest request) {
        if ("/api/v1/public/appointment-requests".equals(request.getRequestURI())) {
            return buildResponse(HttpStatus.CONFLICT,
                    "An equivalent appointment request already exists or the idempotency key was already used",
                    request, Map.of());
        }
        LOGGER.error("Database constraint rejected {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, request, Map.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleServiceUnavailable(ServiceUnavailableException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(QueryTimeoutException.class)
    ResponseEntity<ApiErrorResponse> handleQueryTimeout(QueryTimeoutException exception, HttpServletRequest request) {
        LOGGER.warn("Database query timed out while processing {} {}", request.getMethod(), request.getRequestURI());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "The service could not complete the request within the configured time limit", request, Map.of());
    }

    @ExceptionHandler(com.dentalcare.api.security.ratelimit.RateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimit(
            com.dentalcare.api.security.ratelimit.RateLimitExceededException exception,
            HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), exception.getMessage(), request.getRequestURI(), Map.of());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access is denied", request, Map.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.validation.BindException.class})
    ResponseEntity<ApiErrorResponse> handleValidation(
            Exception exception, HttpServletRequest request) {
        org.springframework.validation.BindingResult bindingResult = (exception instanceof MethodArgumentNotValidException manve)
                ? manve.getBindingResult()
                : ((org.springframework.validation.BindException) exception).getBindingResult();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return buildResponse(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File size exceeds the configured maximum upload limit", request, Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Request is malformed or contains an invalid value", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE, request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        return buildResponse(status, message, request, fieldErrors, null);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request,
            Map<String, String> fieldErrors, String code) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), fieldErrors,
                code);
        return ResponseEntity.status(status).body(body);
    }
}
