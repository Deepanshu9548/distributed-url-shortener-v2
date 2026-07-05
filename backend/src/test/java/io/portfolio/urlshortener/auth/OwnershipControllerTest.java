package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.contracts.EventPublisher;
import io.portfolio.urlshortener.contracts.LinkEvent;
import io.portfolio.urlshortener.contracts.ShardRouter;
import io.portfolio.urlshortener.shortener.Link;
import io.portfolio.urlshortener.shortener.LinkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OwnershipController.class)
@AutoConfigureMockMvc(addFilters = false)
class OwnershipControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-04T10:00:00Z");
    private static final AuthenticatedUser ALICE =
            new AuthenticatedUser(42L, "a@b.com", "jti-1", NOW.plus(Duration.ofMinutes(10)));

    @Autowired
    private MockMvc mockMvc;

    @MockBean private LinkIndexRepository linkIndex;
    @MockBean private LinkRepository linkRepository;
    @MockBean private ShardRouter shardRouter;
    @MockBean private EventPublisher events;

    private void stubRouterPassthrough() {
        when(shardRouter.executeWrite(anyString(), any())).thenAnswer(inv ->
                ((Supplier<?>) inv.getArgument(1)).get());
    }

    private void stubOwnership(String code, boolean owned) {
        when(linkIndex.findByShortCodeAndUserId(code, 42L)).thenReturn(
                owned ? Optional.of(new UserLink(code, 42L, NOW)) : Optional.empty());
    }

    @Test
    void myLinks_returnsPagedView() throws Exception {
        when(linkIndex.findByUserIdOrderByCreatedAtDesc(eq(42L), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new UserLink("abc123", 42L, NOW)),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/me/links").requestAttr("auth.currentUser", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shortCode").value("abc123"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void update_owner_mutatesAndPublishesUpdatedEvent() throws Exception {
        stubOwnership("abc123", true);
        stubRouterPassthrough();
        Link link = new Link(1L, "abc123", "https://old.example.com", 42L, NOW, null, false);
        when(linkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));
        when(linkRepository.save(any(Link.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/links/abc123")
                        .requestAttr("auth.currentUser", ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://new.example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.longUrl").value("https://new.example.com"));

        ArgumentCaptor<LinkEvent> event = ArgumentCaptor.forClass(LinkEvent.class);
        verify(events).publishLinkEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(LinkEvent.Type.UPDATED);
        assertThat(event.getValue().shortCode()).isEqualTo("abc123");
        assertThat(event.getValue().userId()).isEqualTo(42L);
    }

    @Test
    void update_nonOwner_returns404AndNoMutation() throws Exception {
        stubOwnership("abc123", false);

        mockMvc.perform(put("/api/links/abc123")
                        .requestAttr("auth.currentUser", ALICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://new.example.com\"}"))
                .andExpect(status().isNotFound());

        verify(shardRouter, never()).executeWrite(anyString(), any());
        verify(events, never()).publishLinkEvent(any());
    }

    @Test
    void delete_owner_deletesAndPublishesDeletedEvent() throws Exception {
        stubOwnership("abc123", true);
        stubRouterPassthrough();
        Link link = new Link(1L, "abc123", "https://x.example.com", 42L, NOW, null, false);
        when(linkRepository.findByShortCode("abc123")).thenReturn(Optional.of(link));

        mockMvc.perform(delete("/api/links/abc123").requestAttr("auth.currentUser", ALICE))
                .andExpect(status().isNoContent());

        verify(linkRepository).delete(link);
        ArgumentCaptor<LinkEvent> event = ArgumentCaptor.forClass(LinkEvent.class);
        verify(events).publishLinkEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(LinkEvent.Type.DELETED);
    }

    @Test
    void delete_nonOwner_returns404() throws Exception {
        stubOwnership("abc123", false);

        mockMvc.perform(delete("/api/links/abc123").requestAttr("auth.currentUser", ALICE))
                .andExpect(status().isNotFound());

        verify(shardRouter, never()).executeWrite(anyString(), any());
    }

    @Test
    void unauthenticated_missingResolverAttribute_returns401() throws Exception {
        mockMvc.perform(get("/api/me/links"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }
}
