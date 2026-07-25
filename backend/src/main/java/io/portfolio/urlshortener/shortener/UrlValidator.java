package io.portfolio.urlshortener.shortener;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Syntactic validation of long URLs and custom aliases (policy frozen in
 * ADR-011). URL checks: length ≤ 8192, http/https only, no whitespace/control
 * characters, and literal-host SSRF/open-redirect hygiene (loopback, RFC1918,
 * link-local, 0.0.0.0, ::1 rejected). No DNS resolution — literal checks only.
 */
@Component
public class UrlValidator {

    public static final int MAX_URL_LENGTH = 8192;

    /** ADR-011: {@code ^[a-zA-Z0-9_-]{4,30}$}. */
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{4,30}$");

    /** ADR-011 reserved blocklist. */
    static final Set<String> ALIAS_BLOCKLIST = Set.of(
            "api", "auth", "actuator", "swagger-ui", "swagger", "metrics", "health", "admin", "v3");

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    /** @throws ValidationException when the URL violates any rule */
    public void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException("longUrl is required");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new ValidationException("longUrl exceeds maximum length of " + MAX_URL_LENGTH);
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7f) {
                throw new ValidationException("longUrl must not contain whitespace or control characters");
            }
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException("longUrl is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ValidationException("longUrl must use http or https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("longUrl must have a valid host");
        }
        if (isForbiddenHost(host)) {
            throw new ValidationException("longUrl host is not allowed");
        }
    }

    /** @throws ValidationException when the alias violates charset/length/blocklist rules */
    public void validateAlias(String alias) {
        if (alias == null || !ALIAS_PATTERN.matcher(alias).matches()) {
            throw new ValidationException(
                    "customAlias must match [a-zA-Z0-9_-] and be 4-30 characters long");
        }
        if (ALIAS_BLOCKLIST.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new ValidationException("customAlias is reserved");
        }
    }

    private boolean isForbiddenHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) { // URI keeps brackets on IPv6 literals
            h = h.substring(1, h.length() - 1);
        }
        if (h.equals("localhost") || h.endsWith(".localhost")) {
            return true;
        }
        if (isForbiddenIpv6(h)) {
            return true;
        }
        var m = IPV4_PATTERN.matcher(h);
        if (m.matches()) {
            int o1 = Integer.parseInt(m.group(1));
            int o2 = Integer.parseInt(m.group(2));
            int o3 = Integer.parseInt(m.group(3));
            int o4 = Integer.parseInt(m.group(4));
            if (o1 > 255 || o2 > 255 || o3 > 255 || o4 > 255) {
                return true; // malformed IPv4 literal — reject outright
            }
            return o1 == 127                          // loopback 127.0.0.0/8
                    || o1 == 0                        // 0.0.0.0/8 ("this host")
                    || o1 == 10                       // RFC1918 10.0.0.0/8
                    || (o1 == 172 && o2 >= 16 && o2 <= 31)  // RFC1918 172.16.0.0/12
                    || (o1 == 192 && o2 == 168)       // RFC1918 192.168.0.0/16
                    || (o1 == 169 && o2 == 254);      // link-local 169.254.0.0/16
        }
        return false;
    }

    private boolean isForbiddenIpv6(String h) {
        if (!h.contains(":")) {
            return false;
        }
        // Loopback / unspecified
        if (h.equals("::1") || h.equals("0:0:0:0:0:0:0:1") || h.equals("::")) {
            return true;
        }
        // Link-local fe80::/10 and unique-local fc00::/7
        return h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd");
    }
}
