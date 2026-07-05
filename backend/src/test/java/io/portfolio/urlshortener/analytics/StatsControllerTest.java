package io.portfolio.urlshortener.analytics;

import io.portfolio.urlshortener.auth.AuthenticatedUser;
import io.portfolio.urlshortener.auth.AuthenticatedUserResolver;
import io.portfolio.urlshortener.auth.LinkIndexRepository;
import io.portfolio.urlshortener.auth.UserLink;
import io.portfolio.urlshortener.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LinkIndexRepository linkIndexRepository;

    @MockBean
    private LinkStatsRepository linkStatsRepository;

    @Test
    void getStats_owned_returnsStats() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(1L, "u@example.com", "jti", Instant.now().plusSeconds(3600));
        
        when(linkIndexRepository.findByShortCodeAndUserId("mycode", 1L))
                .thenReturn(Optional.of(new UserLink("mycode", 1L, Instant.now())));
                
        when(linkStatsRepository.findById("mycode"))
                .thenReturn(Optional.of(new LinkStats("mycode", 5, Instant.now(), "ref")));

        mockMvc.perform(get("/api/links/mycode/stats")
                        .requestAttr("auth.currentUser", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(5))
                .andExpect(jsonPath("$.shortCode").value("mycode"));
    }

    @Test
    void getStats_notOwned_returns404() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(2L, "u@example.com", "jti", Instant.now().plusSeconds(3600));
        
        when(linkIndexRepository.findByShortCodeAndUserId("othercode", 2L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/links/othercode/stats")
                        .requestAttr("auth.currentUser", user))
                .andExpect(status().isNotFound());
    }
}
