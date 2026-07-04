package io.portfolio.urlshortener.shortener;

/**
 * System clock drifted backwards beyond the generator's tolerance; minting ids
 * would risk duplicates, so we refuse (ADR-001). Subtype of
 * {@link InfraUnavailableException} — surfaces as 503.
 */
public class ClockMovedBackwardsException extends InfraUnavailableException {

    public ClockMovedBackwardsException(long driftMillis) {
        super("clock moved backwards by " + driftMillis + "ms; refusing to mint ids");
    }
}
