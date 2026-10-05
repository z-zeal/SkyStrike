package io.github.skystrike.shared.text;

/**
 * A small deterministic token bucket used by authoritative chat and command request handling.
 *
 * <p>Callers pass a monotonic time source in milliseconds, which keeps the limiter easy to test
 * and avoids coupling the shared module to a wall clock. If an old timestamp arrives, no tokens
 * are fabricated and the last observed time is retained.
 */
public final class RateLimiter {

    private static final double MILLIS_PER_SECOND = 1_000d;

    private final int capacity;
    private final double refillTokensPerMillisecond;

    private double tokens;
    private long lastRefillMillis;

    /** Creates a full bucket that refills {@code capacity} tokens every {@code refillWindowMillis}. */
    public RateLimiter(int capacity, long refillWindowMillis, long nowMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillWindowMillis <= 0L) {
            throw new IllegalArgumentException("refill window must be positive");
        }
        this.capacity = capacity;
        this.refillTokensPerMillisecond = (double) capacity / refillWindowMillis;
        this.tokens = capacity;
        this.lastRefillMillis = nowMillis;
    }

    /** A chat-shaped limiter using the shared policy values. */
    public static RateLimiter chat(long nowMillis) {
        return new RateLimiter(TextLimits.CHAT_RATE_CAPACITY, TextLimits.CHAT_RATE_WINDOW_MILLIS, nowMillis);
    }

    /**
     * Attempts to spend one token at {@code nowMillis}. Returns false without changing the
     * remaining balance when the bucket is empty.
     */
    public boolean tryAcquire(long nowMillis) {
        return tryAcquire(1, nowMillis);
    }

    /** Attempts to spend {@code amount} tokens at {@code nowMillis}. */
    public boolean tryAcquire(int amount, long nowMillis) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        refill(nowMillis);
        if (tokens + 1e-9d < amount) {
            return false;
        }
        tokens -= amount;
        return true;
    }

    /** Adds elapsed-time refill up to the configured burst capacity. */
    public void refill(long nowMillis) {
        if (nowMillis <= lastRefillMillis) {
            return;
        }
        long elapsedMillis = nowMillis - lastRefillMillis;
        tokens = Math.min(capacity, tokens + elapsedMillis * refillTokensPerMillisecond);
        lastRefillMillis = nowMillis;
    }

    /** Whole tokens currently available after applying elapsed time. */
    public int available(long nowMillis) {
        refill(nowMillis);
        return (int) Math.floor(tokens + 1e-9d);
    }

    /** Remaining fractional token balance after applying elapsed time. Useful for diagnostics. */
    public double availableExact(long nowMillis) {
        refill(nowMillis);
        return tokens;
    }

    public int capacity() {
        return capacity;
    }

    /** Refill rate in tokens per second, for diagnostics and server policy reporting. */
    public double refillTokensPerSecond() {
        return refillTokensPerMillisecond * MILLIS_PER_SECOND;
    }
}
