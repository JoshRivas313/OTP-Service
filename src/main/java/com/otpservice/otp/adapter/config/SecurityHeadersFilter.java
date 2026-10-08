package com.otpservice.otp.adapter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Cabeceras de seguridad en toda respuesta y un tope al tamano del cuerpo. Ninguna peticion legitima de esta API pasa de
// unos cientos de bytes; sin tope, un cuerpo de megabytes se lee entero antes de poder rechazarlo.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 16 * 1024;

    // Los scripts solo vienen de este mismo origen (por eso las paginas no llevan JavaScript en linea). Los estilos en
    // linea se permiten: son atributos style y no ejecutan codigo. Las fuentes salen de Google Fonts.
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    private static final String TOO_LARGE_BODY =
            "{\"success\":false,\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"La solicitud es demasiado grande\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        addHeaders(request, response);
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.setStatus(413);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(TOO_LARGE_BODY.getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request), response);
    }

    private static void addHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        // Swagger UI trae sus propios scripts: la politica estricta es para la aplicacion, no para esa herramienta.
        if (!isDocumentation(request.getRequestURI())) {
            response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        }
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    private static boolean isDocumentation(String uri) {
        return uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }

    // Cuerpos sin Content-Length (chunked): se corta la lectura al pasar el tope.
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

        LimitedBodyRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int value = delegate.read();
                    if (value >= 0 && ++read > MAX_BODY_BYTES) {
                        throw new IOException("Cuerpo demasiado grande");
                    }
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = delegate.read(buffer, offset, length);
                    if (count > 0 && (read += count) > MAX_BODY_BYTES) {
                        throw new IOException("Cuerpo demasiado grande");
                    }
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
