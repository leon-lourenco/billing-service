package com.cardbilling.billing.infrastructure.web;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds correlation IDs (trace IDs) to all incoming requests.
 * Enables tracing of requests across services via X-Trace-ID header.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TRACE_ID_MDC = "traceId";
    private static final String USER_ID_MDC = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // Store in MDC for all logs in this thread
        MDC.put(TRACE_ID_MDC, traceId);

        // Extract user ID from JWT token if available
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // In production, this would decode the JWT to extract user ID
                // For now, we just mark it as available
                MDC.put(USER_ID_MDC, "system");
            }
        } catch (Exception ex) {
            // Silently ignore parsing errors
        }

        // Add trace ID to response for client correlation
        response.addHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
