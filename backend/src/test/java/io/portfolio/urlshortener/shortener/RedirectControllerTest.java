package io.portfolio.urlshortener.shortener;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RedirectController.class)
@AutoConfigureMockMvc(addFilters = false)
class RedirectControllerTest {

    private static final String LONG_URL = "https://example.com/target";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedirectService redirectService;

    @Test
    void knownCodeReturns302WithLocationAndNoBody() throws Exception {
        when(redirectService.resolve(eq("abc123"), any(), any(), anyString())).thenReturn(LONG_URL);

        mockMvc.perform(get("/abc123")
                        .header("Referer", "https://ref.example")
                        .header("User-Agent", "TestUA/1.0")
                        .header("X-Request-Id", "req-42"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LONG_URL))
                .andExpect(content().string(""));

        verify(redirectService).resolve("abc123", "https://ref.example", "TestUA/1.0", "req-42");
    }

    @Test
    void missingRequestIdIsGenerated() throws Exception {
        when(redirectService.resolve(eq("abc123"), any(), any(), anyString())).thenReturn(LONG_URL);

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound());

        ArgumentCaptor<String> rid = ArgumentCaptor.forClass(String.class);
        verify(redirectService).resolve(eq("abc123"), any(), any(), rid.capture());
        assertThat(rid.getValue()).isNotBlank();
    }

    @Test
    void unknownCodeReturns404WithErrorBody() throws Exception {
        when(redirectService.resolve(eq("nope404"), any(), any(), anyString()))
                .thenThrow(new NotFoundException("nope404"));

        mockMvc.perform(get("/nope404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void infraFailureReturns503WithErrorBody() throws Exception {
        when(redirectService.resolve(eq("abc123"), any(), any(), anyString()))
                .thenThrow(new InfraUnavailableException("db down"));

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void pathsOutsideShortCodePatternDoNotHitTheRedirectHandler() throws Exception {
        // 33 chars — exceeds the {1,32} constraint
        mockMvc.perform(get("/" + "a".repeat(33)))
                .andExpect(status().isNotFound());
        // dotted resource names don't match the charset
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(redirectService);
    }
}
