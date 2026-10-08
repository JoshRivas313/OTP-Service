package com.otpservice.otp.adapter.config;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Cuerpos sin Content-Length (chunked): el filtro corta la lectura al pasar el tope, aunque la cabecera no lo anuncie.
class SecurityHeadersFilterTest {

    private static byte[] body(int size) {
        return new byte[size];
    }

    private static MockHttpServletRequest chunked(int size) {
        // Sin Content-Length anunciado, como en una peticion chunked: el filtro solo puede contar lo que lee.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/email/otps") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body(size));
        return request;
    }

    private static long drain(jakarta.servlet.ServletRequest request) throws IOException {
        ServletInputStream in = request.getInputStream();
        byte[] buffer = new byte[1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
        }
        return total;
    }

    @Test
    void unCuerpoChunkedDentroDelTopeSeLeeCompleto() throws Exception {
        MockHttpServletRequest request = chunked(SecurityHeadersFilter.MAX_BODY_BYTES);
        long[] read = new long[1];

        new SecurityHeadersFilter().doFilter(request, new MockHttpServletResponse(),
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                    @Override
                    protected void service(jakarta.servlet.http.HttpServletRequest req,
                                           jakarta.servlet.http.HttpServletResponse res) throws IOException {
                        read[0] = drain(req);
                    }
                }));

        assertThat(read[0]).isEqualTo(SecurityHeadersFilter.MAX_BODY_BYTES);
    }

    @Test
    void unCuerpoChunkedMasGrandeQueElTopeCortaLaLectura() {
        MockHttpServletRequest request = chunked(SecurityHeadersFilter.MAX_BODY_BYTES + 5_000);

        assertThatThrownBy(() -> new SecurityHeadersFilter().doFilter(request, new MockHttpServletResponse(),
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                    @Override
                    protected void service(jakarta.servlet.http.HttpServletRequest req,
                                           jakarta.servlet.http.HttpServletResponse res) throws IOException {
                        drain(req);
                    }
                })))
                .isInstanceOf(IOException.class)
                .hasMessage("Cuerpo demasiado grande");
    }
}
