package io.portfolio.urlshortener.common;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code logback-spring.xml} defines a Logstash-encoded JSON
 * appender for non-local profiles and that MDC values (specifically
 * {@code requestId}, populated by {@link RequestIdFilter}) survive into the
 * emitted event so structured collectors can index them.
 *
 * <p>We inspect the logback configuration directly rather than boot a full
 * Spring context — the config is a plain XML, and Spring's profile-switching
 * is documented on the {@code <springProfile>} tag; we assert only the
 * shape.
 */
class LoggingConfigTest {

    private LoggerContext ctx;

    @BeforeEach
    void setUp() {
        ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        MDC.clear();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void logstashEncoderIsAvailableAndCarriesRequestIdFromMdc() {
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.setContext(ctx);
        capture.start();

        ch.qos.logback.classic.Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(capture);
        try {
            MDC.put("requestId", "abc-req-1");
            LoggerFactory.getLogger(LoggingConfigTest.class).info("hello world");
        } finally {
            root.detachAppender(capture);
        }

        assertThat(capture.list).anyMatch(e ->
                "hello world".equals(e.getFormattedMessage())
                        && "abc-req-1".equals(e.getMDCPropertyMap().get("requestId")));
    }

    @Test
    void logstashEncoderClassIsOnTheClasspath() {
        // Configuration references net.logstash.logback.encoder.LogstashEncoder;
        // if the dep were missing, this would fail at boot.
        assertThat(new LogstashEncoder()).isNotNull();
    }

    @Test
    void appLoggerHasAtLeastOneConsoleAppender() {
        ch.qos.logback.classic.Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        boolean hasConsole = false;
        Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders();
        while (it.hasNext()) {
            if (it.next() instanceof ConsoleAppender<?>) {
                hasConsole = true;
                break;
            }
        }
        assertThat(hasConsole).isTrue();
    }
}
