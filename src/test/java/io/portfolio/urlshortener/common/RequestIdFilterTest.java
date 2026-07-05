package io.portfolio.urlshortener.common;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Certifies the request-id contract: propagate the incoming header when
 * present, generate a UUID otherwise, expose it in the response, put it in
 * MDC during handler execution, and clean up afterwards.
 */
class RequestIdFilterTest {

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
        MDC.clear();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void incomingHeader_isPropagatedToResponseAndMdc() throws ServletException, IOException {
        String incoming = "abc-123-provided";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/me/links");
        req.addHeader(HEADER, incoming);
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s)
                    throws IOException, ServletException {
                mdcDuringChain.set(MDC.get(MDC_KEY));
                super.doFilter(r, s);
            }
        };

        filter.doFilter(req, res, chain);

        assertThat(mdcDuringChain.get()).isEqualTo(incoming);
        assertThat(res.getHeader(HEADER)).isEqualTo(incoming);
    }

    @Test
    void missingHeader_isGeneratedAsUuidAndExposed() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s)
                    throws IOException, ServletException {
                seen.set(MDC.get(MDC_KEY));
                super.doFilter(r, s);
            }
        };

        filter.doFilter(req, res, chain);

        String generated = res.getHeader(HEADER);
        assertThat(generated).isNotBlank();
        assertThat(seen.get()).isEqualTo(generated);
        // Must be a valid UUID — parses without throwing.
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    void emptyHeader_isReplacedWithUuid() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        req.addHeader(HEADER, "");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader(HEADER)).isNotBlank();
        assertThat(UUID.fromString(res.getHeader(HEADER))).isNotNull();
    }

    @Test
    void mdc_isClearedAfterChainCompletes() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void mdc_isClearedEvenWhenChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain throwing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                throw new RuntimeException("handler blew up");
            }
        };

        try {
            filter.doFilter(req, res, throwing);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get(MDC_KEY)).isNull();
    }
}
