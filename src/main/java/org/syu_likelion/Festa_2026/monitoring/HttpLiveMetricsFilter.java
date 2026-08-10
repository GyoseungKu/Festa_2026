package org.syu_likelion.Festa_2026.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HttpLiveMetricsFilter extends OncePerRequestFilter {
    private final HttpLiveMetrics metrics;

    public HttpLiveMetricsFilter(HttpLiveMetrics metrics) { this.metrics = metrics; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        metrics.requestStarted();
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metrics.requestFinished(response.getStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        if ("/admin/system/snapshot".equals(path)) return true;
        return !(path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/api/"));
    }

    @Override protected boolean shouldNotFilterAsyncDispatch() { return true; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return true; }
}
