package com.aldercrest.atlas.http;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class SafeExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SafeExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> notFound() { return SafeErrors.response(404); }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> invalid() { return SafeErrors.response(400); }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> databaseUnavailable() {
        LOG.warn("ATLAS_DATABASE_ACTION_FAILED"); // No exception, SQL, IDs, query or payload.
        return SafeErrors.response(503);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unavailable(Exception error) {
        if (error instanceof TypeMismatchException) {
            return SafeErrors.response(400);
        }
        if (error instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            return SafeErrors.response(response.getStatusCode().value());
        }
        LOG.warn("ATLAS_REQUEST_FAILED");
        return SafeErrors.response(503);
    }
}
