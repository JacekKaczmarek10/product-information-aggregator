package pl.kaczmarek.aggregator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldReuseCorrelationIdFromHeader() throws ServletException, IOException {
        String existingId = "test-correlation-id";
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(existingId);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get("correlationId")).isNull(); // MDC is cleared in finally
        verify(response).setHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldGenerateCorrelationIdIfHeaderIsMissing() throws ServletException, IOException {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldGenerateCorrelationIdIfHeaderIsBlank() throws ServletException, IOException {
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn("  ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(CorrelationIdFilter.CORRELATION_ID_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldClearMdcAfterRequest() throws ServletException, IOException {
        String existingId = "test-id";
        when(request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).thenReturn(existingId);
        
        // Use a real MDC check during filter execution if needed, but the logic 
        // in CorrelationIdFilter uses try-finally which we can trust if it compiles.
        // We verify filterChain was called.
        filter.doFilterInternal(request, response, filterChain);
        
        assertThat(MDC.get("correlationId")).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
