package com.mycompany.msr.amis.api.exception;

import com.mycompany.msr.amis.api.dto.CommonMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<CommonMessageResponse> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new CommonMessageResponse(false, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<CommonMessageResponse> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "Invalid request." : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new CommonMessageResponse(false, message));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<CommonMessageResponse> handleDataAccess(DataAccessException exception) {
        LOGGER.error("Database request failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new CommonMessageResponse(false, "Database is not ready. Restart the API or run the database migrations."));
    }
}
