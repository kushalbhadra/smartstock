package com.smartstock.smartstock.exception;

import com.smartstock.smartstock.entity.Stock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void returnsUnauthorizedForInvalidLoginCredentials() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/auth/login");

        var response = new GlobalExceptionHandler()
                .handleAuthenticationFailure(
                        new BadCredentialsException("Bad credentials"),
                        request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Email or password is incorrect",
                ((Map<?, ?>) response.getBody()).get("message"));
    }

    @Test
    void returnsConflictWhenStockWasUpdatedConcurrently() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/stocks/movements");

        var response = new GlobalExceptionHandler()
                .handleOptimisticLockingFailure(
                        new ObjectOptimisticLockingFailureException(
                                Stock.class, 1L),
                        request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                "Stock was updated by another user. "
                        + "Refresh stock levels and try again.",
                ((Map<?, ?>) response.getBody()).get("message"));
    }
}
