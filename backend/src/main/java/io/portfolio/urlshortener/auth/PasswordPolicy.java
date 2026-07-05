package io.portfolio.urlshortener.auth;

import io.portfolio.urlshortener.shortener.ValidationException;

/**
 * Password rules — enforced in-service so that the same policy applies
 * whether a request arrived via Bean Validation, a controller test, or an
 * upstream migration. Length is bounded by the Jakarta {@code @Size}
 * annotation on request DTOs; the character-class check lives here.
 */
final class PasswordPolicy {

    private PasswordPolicy() {
    }

    static void enforce(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ValidationException("password must be 8-128 characters");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            if (hasLetter && hasDigit) return;
        }
        throw new ValidationException("password must contain a letter and a digit");
    }
}
