package io.github.skystrike.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    @DisplayName("a full bucket permits the configured burst then refills continuously")
    void allowsBurstThenRefills() {
        RateLimiter limiter = new RateLimiter(3, 5_000L, 1_000L);

        assertTrue(limiter.tryAcquire(1_000L));
        assertTrue(limiter.tryAcquire(1_000L));
        assertTrue(limiter.tryAcquire(1_000L));
        assertFalse(limiter.tryAcquire(1_000L));

        assertFalse(limiter.tryAcquire(2_000L), "0.6 tokens is not a whole message");
        assertTrue(limiter.tryAcquire(1_000L + 5_000L / 3L + 1L));
        assertEquals(0, limiter.available(1_000L + 5_000L / 3L + 1L));
    }

    @Test
    @DisplayName("refill caps at capacity and old timestamps cannot create a token")
    void capsRefillAndIgnoresClockRollback() {
        RateLimiter limiter = new RateLimiter(2, 2_000L, 100L);

        assertTrue(limiter.tryAcquire(100L));
        assertTrue(limiter.tryAcquire(100L));
        assertEquals(2, limiter.available(10_000L));
        assertTrue(limiter.tryAcquire(10_000L));
        assertTrue(limiter.tryAcquire(10_000L));
        assertFalse(limiter.tryAcquire(99L), "an old timestamp must not refill the bucket");
    }

    @Test
    @DisplayName("invalid limiter configuration and requests fail closed")
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, 1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, 0L, 0L));

        RateLimiter limiter = new RateLimiter(1, 1_000L, 0L);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0, 0L));
    }

    @Test
    void chatFactoryUsesSharedPolicy() {
        RateLimiter limiter = RateLimiter.chat(0L);

        assertEquals(TextLimits.CHAT_RATE_CAPACITY, limiter.capacity());
        assertEquals(
            (double) TextLimits.CHAT_RATE_CAPACITY * 1_000d / TextLimits.CHAT_RATE_WINDOW_MILLIS,
            limiter.refillTokensPerSecond(),
            1e-9d);
    }
}
