package dev.zenith.ast;

import java.time.Duration;
import java.util.Objects;

/**
 * Value object encapsulating rate limit capacity and refill interval.
 * Supports string formats like "10/minute", "5/second", "100/hour", "1000/day",
 * or explicit capacity and duration.
 */
public class RateLimitSpec {
    private final int capacity;
    private final Duration duration;
    private final String unit; // "second", "minute", "hour", "day"

    public RateLimitSpec(int capacity, Duration duration, String unit) {
        this.capacity = capacity;
        this.duration = duration != null ? duration : Duration.ofMinutes(1);
        this.unit = unit != null ? unit.toLowerCase() : "minute";
    }

    public static RateLimitSpec of(int capacity, String unitStr) {
        if (unitStr == null || unitStr.trim().isEmpty()) {
            unitStr = "minute";
        }
        String u = unitStr.trim().toLowerCase();
        // Strip leading numbers if passed like "1m", "1 minute", "60s", "1 hour"
        while (u.length() > 0 && Character.isDigit(u.charAt(0))) {
            u = u.substring(1).trim();
        }
        // Normalize plural / shorthand: "s", "sec", "seconds" -> "second"
        Duration d;
        String normalizedUnit;
        if (u.startsWith("sec") || u.equals("s")) {
            d = Duration.ofSeconds(1);
            normalizedUnit = "second";
        } else if (u.startsWith("min") || u.equals("m")) {
            d = Duration.ofMinutes(1);
            normalizedUnit = "minute";
        } else if (u.startsWith("hour") || u.startsWith("hr") || u.equals("h")) {
            d = Duration.ofHours(1);
            normalizedUnit = "hour";
        } else if (u.startsWith("day") || u.equals("d")) {
            d = Duration.ofDays(1);
            normalizedUnit = "day";
        } else {
            throw new IllegalArgumentException("Unsupported rate limit time unit: '" + unitStr
                    + "'. Supported units: second, minute, hour, day.");
        }
        return new RateLimitSpec(capacity, d, normalizedUnit);
    }

    /**
     * Parses specs of the form:
     * - "10/minute"
     * - "5 / second"
     * - "100/hour"
     * - "1000/day"
     * - "50 per minute"
     */
    public static RateLimitSpec parse(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            throw new IllegalArgumentException("Rate limit specification cannot be empty");
        }
        String cleaned = spec.trim().replace("\"", "").replace("'", "");
        String[] parts;
        if (cleaned.contains("/")) {
            parts = cleaned.split("/", 2);
        } else if (cleaned.toLowerCase().contains(" per ")) {
            parts = cleaned.toLowerCase().split(" per ", 2);
        } else {
            throw new IllegalArgumentException("Invalid rate limit format: '" + spec
                    + "'. Expected format '<number>/<unit>' (e.g. '10/minute', '5/second', '100/hour').");
        }

        int capacity;
        try {
            capacity = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Rate limit capacity must be an integer, found: '" + parts[0].trim() + "'");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limit capacity must be greater than zero, found: " + capacity);
        }

        return of(capacity, parts[1].trim());
    }

    public int getCapacity() {
        return capacity;
    }

    public Duration getDuration() {
        return duration;
    }

    public long getDurationSeconds() {
        return duration.getSeconds();
    }

    public String getUnit() {
        return unit;
    }

    public String toDslString() {
        return capacity + "/" + unit;
    }

    @Override
    public String toString() {
        return toDslString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitSpec that = (RateLimitSpec) o;
        return capacity == that.capacity && Objects.equals(duration, that.duration) && Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, duration, unit);
    }
}
