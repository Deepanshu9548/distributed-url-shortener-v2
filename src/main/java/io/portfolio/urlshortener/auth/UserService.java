package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.shortener.SnowflakeIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Register + login. All persistence uses the qualified control transaction
 * manager (see {@link ControlDbConfig}). Password hashing is BCrypt from
 * {@link org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder}.
 */
@Service
public class UserService {

    private final UserRepository users;
    private final SnowflakeIdGenerator idGen;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository users, SnowflakeIdGenerator idGen, PasswordEncoder passwordEncoder) {
        this(users, idGen, passwordEncoder, Clock.systemUTC());
    }

    UserService(UserRepository users, SnowflakeIdGenerator idGen,
                PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.idGen = idGen;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional("controlTransactionManager")
    public User register(String email, String password) {
        String normalized = normalize(email);
        PasswordPolicy.enforce(password);
        if (users.existsByEmailNormalized(normalized)) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User(
                idGen.nextId(),
                email.trim(),
                normalized,
                passwordEncoder.encode(password),
                Instant.now(clock));
        return users.save(user);
    }

    @Transactional(value = "controlTransactionManager", readOnly = true)
    public User login(String email, String password) {
        String normalized = normalize(email);
        User user = users.findByEmailNormalized(normalized).orElse(null);
        // Compare against a valid bcrypt template even on miss to keep the
        // timing similar and reduce user-enumeration signal.
        if (user == null) {
            passwordEncoder.matches(password, "$2a$10$abcdefghijklmnopqrstuvwx1234567890abcdefghijklmnopqrstu");
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    @Transactional(value = "controlTransactionManager", readOnly = true)
    public User byId(long userId) {
        return users.findById(userId).orElseThrow(InvalidCredentialsException::new);
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
