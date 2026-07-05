package io.portfolio.urlshortener.sharding;

public class ShardUnavailableException extends RuntimeException {
    public ShardUnavailableException(String shard, Throwable cause) {
        super("Shard unavailable: " + shard, cause);
    }
}