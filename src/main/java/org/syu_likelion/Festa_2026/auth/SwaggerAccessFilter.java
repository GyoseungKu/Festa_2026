package org.syu_likelion.Festa_2026.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;
import org.syu_likelion.Festa_2026.admin.AdminAccessService;
import org.syu_likelion.Festa_2026.admin.AdminCookieManager;
import org.syu_likelion.Festa_2026.error.ApiException;
import org.syu_likelion.Festa_2026.sso.SsoException;

/** Keep Swagger protected, except for the public JSON specification used for type generation. */
public class SwaggerAccessFilter extends OncePerRequestFilter {
    private final AdminAccessService admins;
    private final AdminCookieManager cookies;
    private final HandlerExceptionResolver exceptions;

    public SwaggerAccessFilter(AdminAccessService admins, AdminCookieManager cookies,
                               HandlerExceptionResolver exceptions) {
        this.admins = admins;
        this.cookies = cookies;
        this.exceptions = exceptions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        boolean docs = path.equals("/admin/swagger-ui.html") || path.startsWith("/admin/swagger-ui/")
                || path.startsWith("/admin/v3/api-docs");
        if (!docs) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        if (path.equals("/admin/v3/api-docs") && "GET".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        boolean page = path.equals("/admin/swagger-ui.html") || path.equals("/admin/swagger-ui/index.html");
        try {
            var authenticated = admins.authenticateForSwagger(cookies.readAccessToken(request), cookies.readRefreshToken(request));
            cookies.applyRotation(response, authenticated.newAccessToken(), authenticated.newRefreshToken());
        } catch (ApiException exception) {
            if (exception.status().value() == 401) {
                cookies.clear(response);
                if (page) {
                    response.sendRedirect(request.getContextPath() + "/admin/login?next=swagger");
                    return;
                }
            }
            exceptions.resolveException(request, response, null, exception);
            return;
        } catch (SsoException exception) {
            if (exception.statusCode() == 401 || exception.statusCode() == 403) {
                cookies.clear(response);
                if (page && exception.statusCode() == 401) {
                    response.sendRedirect(request.getContextPath() + "/admin/login?next=swagger");
                    return;
                }
            }
            exceptions.resolveException(request, response, null, exception);
            return;
        }
        chain.doFilter(request, response);
    }
}
