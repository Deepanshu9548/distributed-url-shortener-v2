package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.shortener.SnowflakeIdGenerator;
import io.portfolio.urlshortener.shortener.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-04T10:00:00Z");

    private UserRepository users;
    private SnowflakeIdGenerator idGen;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low strength for test speed
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        idGen = mock(SnowflakeIdGenerator.class);
        when(idGen.nextId()).thenReturn(777L);
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UserService(users, idGen, encoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void register_normalizesEmail_bcrypts_andUsesSnowflakeId() {
        when(users.existsByEmailNormalized("alice@example.com")).thenReturn(false);

        User saved = service.register("  Alice@Example.COM ", "hunter42x");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).save(captor.capture());
        User u = captor.getValue();
        assertThat(u.getId()).isEqualTo(777L);
        assertThat(u.getEmail()).isEqualTo("Alice@Example.COM");
        assertThat(u.getEmailNormalized()).isEqualTo("alice@example.com");
        assertThat(u.getPasswordHash()).startsWith("$2a$");
        assertThat(encoder.matches("hunter42x", u.getPasswordHash())).isTrue();
        assertThat(u.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved).isSameAs(u);
    }

    @Test
    void register_duplicateEmail_throws409Exception() {
        when(users.existsByEmailNormalized("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alice@example.com", "hunter42x"))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void register_passwordPolicy_lettersAndDigitsRequired() {
        when(users.existsByEmailNormalized(any())).thenReturn(false);

        assertThatThrownBy(() -> service.register("a@b.com", "short1"))
                .isInstanceOf(ValidationException.class); // too short
        assertThatThrownBy(() -> service.register("a@b.com", "allletters"))
                .isInstanceOf(ValidationException.class); // no digit
        assertThatThrownBy(() -> service.register("a@b.com", "1234567890"))
                .isInstanceOf(ValidationException.class); // no letter
    }

    @Test
    void login_validCredentials_returnsUser() {
        User existing = new User(1L, "a@b.com", "a@b.com", encoder.encode("hunter42x"), NOW);
        when(users.findByEmailNormalized("a@b.com")).thenReturn(Optional.of(existing));

        assertThat(service.login("A@B.com", "hunter42x")).isSameAs(existing);
    }

    @Test
    void login_wrongPassword_and_unknownEmail_throwSameException() {
        User existing = new User(1L, "a@b.com", "a@b.com", encoder.encode("hunter42x"), NOW);
        when(users.findByEmailNormalized("a@b.com")).thenReturn(Optional.of(existing));
        when(users.findByEmailNormalized("ghost@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("a@b.com", "wrong-pass1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("invalid credentials");
        assertThatThrownBy(() -> service.login("ghost@b.com", "whatever12"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("invalid credentials"); // no user enumeration
    }
}
