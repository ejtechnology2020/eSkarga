package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.util.Collection;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Nagłówki bezpieczeństwa (T-005, NFR-04): HSTS, X-Frame-Options, Referrer-Policy, X-Content-Type-Options,
 * Permissions-Policy oraz {@code SameSite=Strict} dla ciasteczka sesji. Nagłówek Content-Security-Policy
 * (z nonce, bez {@code unsafe-eval}) ustawia PrimeFaces w trybie {@code primefaces.CSP=true}.
 */
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (res instanceof HttpServletResponse http) {
            http.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            http.setHeader("X-Frame-Options", "DENY");
            http.setHeader("X-Content-Type-Options", "nosniff");
            http.setHeader("Referrer-Policy", "same-origin");
            http.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            chain.doFilter(req, new SameSiteResponse(http));
            applySameSite(http);
        } else {
            chain.doFilter(req, res);
        }
    }

    private static void applySameSite(HttpServletResponse http) {
        Collection<String> cookies = http.getHeaders("Set-Cookie");
        if (cookies == null || cookies.isEmpty()) {
            return;
        }
        boolean first = true;
        for (String c : cookies) {
            String v = c.toLowerCase().contains("samesite") ? c : c + "; SameSite=Strict";
            if (first) {
                http.setHeader("Set-Cookie", v);
                first = false;
            } else {
                http.addHeader("Set-Cookie", v);
            }
        }
    }

    /** Dopisuje SameSite=Strict do każdego ciasteczka ustawianego w trakcie żądania. */
    private static final class SameSiteResponse extends HttpServletResponseWrapper {
        SameSiteResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name) && !value.toLowerCase().contains("samesite")) {
                value = value + "; SameSite=Strict";
            }
            super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Set-Cookie".equalsIgnoreCase(name) && !value.toLowerCase().contains("samesite")) {
                value = value + "; SameSite=Strict";
            }
            super.setHeader(name, value);
        }
    }
}
