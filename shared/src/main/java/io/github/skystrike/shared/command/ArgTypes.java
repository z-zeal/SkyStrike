package io.github.skystrike.shared.command;

import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The built-in argument types (console plan §7.3).
 *
 * <p>Every type here is a singleton or a small factory; adding a domain type is a one-line
 * registration in this file, never a parser change. Range types own both the validation and the
 * description of the range, so the error a player sees is derived from the same numbers the
 * validator enforces.
 */
public final class ArgTypes {

    /** A bare true/false token; also accepts {@code 1/0}, {@code on/off} and {@code yes/no}. */
    public static final ArgType<Boolean> BOOL = new ArgType<>() {
        @Override
        public Boolean parse(String token) throws CommandException {
            Boolean value = parseBool(token);
            if (value == null) {
                throw new CommandException(
                    "expected true/false (or on/off, 1/0, yes/no), got '" + token + "'");
            }
            return value;
        }

        @Override
        public List<String> complete(String prefix, CommandContext context) {
            return filter(List.of("true", "false"), prefix);
        }

        @Override
        public String describe() {
            return "true|false";
        }
    };

    /** One whitespace-free token, kept verbatim. */
    public static final ArgType<String> STRING = new ArgType<>() {
        @Override
        public String parse(String token) {
            return token;
        }

        @Override
        public String describe() {
            return "text";
        }
    };

    /**
     * Everything from this argument to the end of the line, spacing preserved. Only legal as the
     * last argument of a spec, which {@link CommandSpec} enforces at build time.
     */
    public static final ArgType<String> GREEDY_STRING = new ArgType<>() {
        @Override
        public String parse(String token) throws CommandException {
            if (token == null || token.isBlank()) {
                throw new CommandException("expected some text");
            }
            return token.trim();
        }

        @Override
        public String describe() {
            return "text...";
        }
    };

    /**
     * A player selector: a numeric id, or a case-insensitive name resolved by the executor
     * against its own roster. The type never looks players up itself — it cannot see the
     * authoritative registry, and pretending it can is how a client-side guess becomes an
     * authoritative action.
     */
    public static final ArgType<PlayerRef> PLAYER = new ArgType<>() {
        @Override
        public PlayerRef parse(String token) throws CommandException {
            if (token == null || token.isBlank()) {
                throw new CommandException("expected a player name or id");
            }
            try {
                return new PlayerRef(token, Integer.parseInt(token.trim()));
            } catch (NumberFormatException notAnId) {
                return new PlayerRef(token, -1);
            }
        }

        @Override
        public List<String> complete(String prefix, CommandContext context) {
            return filter(context == null ? List.of() : context.connectedPlayerNames(), prefix);
        }

        @Override
        public String describe() {
            return "player (name or id)";
        }
    };

    /** A team: {@code a}, {@code b} or {@code neutral}, matched loosely. */
    public static final ArgType<Team> TEAM = new ArgType<>() {
        @Override
        public Team parse(String token) throws CommandException {
            String normalised = normalise(token);
            for (Team team : Team.values()) {
                if (normalise(team.name()).equals(normalised)
                    || normalise(team.displayName()).equals(normalised)) {
                    return team;
                }
            }
            // Bare letters are the fastest thing to type in a match and are unambiguous here.
            return switch (normalised) {
                case "a" -> Team.TEAM_A;
                case "b" -> Team.TEAM_B;
                case "n", "neutral" -> Team.NEUTRAL;
                default -> throw new CommandException(
                    "expected a team (a, b or neutral), got '" + token + "'");
            };
        }

        @Override
        public List<String> complete(String prefix, CommandContext context) {
            return filter(List.of("a", "b", "neutral"), prefix);
        }

        @Override
        public String describe() {
            return "a|b|neutral";
        }
    };

    /**
     * A duration such as {@code 30s}, {@code 5m} or {@code 1h}, returned as seconds. A bare
     * number means seconds — writing {@code 90} when {@code 90s} works too costs nothing.
     */
    public static final ArgType<Float> DURATION = new ArgType<>() {
        @Override
        public Float parse(String token) throws CommandException {
            String value = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            float multiplier = 1f;
            if (value.endsWith("ms")) {
                throw new CommandException("durations finer than a second are not supported");
            } else if (value.endsWith("s")) {
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("m")) {
                multiplier = 60f;
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("h")) {
                multiplier = 3600f;
                value = value.substring(0, value.length() - 1);
            }
            try {
                float seconds = Float.parseFloat(value) * multiplier;
                if (!(seconds > 0f) || !Float.isFinite(seconds)) {
                    throw new CommandException("expected a positive duration, got '" + token + "'");
                }
                return seconds;
            } catch (NumberFormatException notANumber) {
                throw new CommandException(
                    "expected a duration like 30s, 5m or 1h, got '" + token + "'");
            }
        }

        @Override
        public String describe() {
            return "duration (30s, 5m, 1h)";
        }
    };

    /** A primary/handgun weapon by id name or display name, e.g. {@code iron_carbine}. */
    public static final ArgType<WeaponId> WEAPON = new ArgType<>() {
        @Override
        public WeaponId parse(String token) throws CommandException {
            String normalised = normalise(token);
            for (WeaponId weapon : WeaponId.values()) {
                if (normalise(weapon.name()).equals(normalised)) {
                    return weapon;
                }
            }
            throw new CommandException("unknown weapon '" + token + "' (id names like iron_carbine)");
        }

        @Override
        public List<String> complete(String prefix, CommandContext context) {
            List<String> names = new ArrayList<>(WeaponId.values().length);
            for (WeaponId weapon : WeaponId.values()) {
                names.add(weapon.name().toLowerCase(Locale.ROOT));
            }
            return filter(names, prefix);
        }

        @Override
        public String describe() {
            return "weapon id (e.g. iron_carbine)";
        }
    };

    private ArgTypes() {
    }

    /** A ranged integer; both ends inclusive and part of the description shown to players. */
    public static ArgType<Integer> INT(int min, int max) {
        return new ArgType<>() {
            @Override
            public Integer parse(String token) throws CommandException {
                final int value;
                try {
                    value = Integer.parseInt(token.trim());
                } catch (NumberFormatException notAnInt) {
                    throw new CommandException("expected an integer, got '" + token + "'");
                }
                if (value < min || value > max) {
                    throw new CommandException(
                        "expected an integer between " + min + " and " + max + ", got " + value);
                }
                return value;
            }

            @Override
            public String describe() {
                return "integer " + min + ".." + max;
            }
        };
    }

    /** A ranged float; both ends inclusive and part of the description shown to players. */
    public static ArgType<Float> FLOAT(float min, float max) {
        return new ArgType<>() {
            @Override
            public Float parse(String token) throws CommandException {
                final float value;
                try {
                    value = Float.parseFloat(token.trim());
                } catch (NumberFormatException notAFloat) {
                    throw new CommandException("expected a number, got '" + token + "'");
                }
                if (!Float.isFinite(value) || value < min || value > max) {
                    throw new CommandException(
                        "expected a number between " + min + " and " + max + ", got '" + token + "'");
                }
                return value;
            }

            @Override
            public String describe() {
                return "number " + min + ".." + max;
            }
        };
    }

    /** An enum matched case-insensitively, with the constants as completion candidates. */
    public static <E extends Enum<E>> ArgType<E> ENUM(Class<E> type) {
        return new ArgType<>() {
            @Override
            public E parse(String token) throws CommandException {
                String normalised = normalise(token);
                for (E constant : type.getEnumConstants()) {
                    if (normalise(constant.name()).equals(normalised)) {
                        return constant;
                    }
                }
                throw new CommandException(
                    "expected one of " + describe() + ", got '" + token + "'");
            }

            @Override
            public List<String> complete(String prefix, CommandContext context) {
                List<String> names = new ArrayList<>();
                for (E constant : type.getEnumConstants()) {
                    names.add(constant.name().toLowerCase(Locale.ROOT));
                }
                return filter(names, prefix);
            }

            @Override
            public String describe() {
                StringBuilder list = new StringBuilder();
                for (E constant : type.getEnumConstants()) {
                    if (list.length() > 0) {
                        list.append('|');
                    }
                    list.append(constant.name().toLowerCase(Locale.ROOT));
                }
                return list.toString();
            }
        };
    }

    /** Case/space/underscore-insensitive comparison key for names typed by a human mid-match. */
    static String normalise(String token) {
        if (token == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c != '_' && c != ' ' && c != '-') {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    /** Prefix-filtering shared by built-in and custom argument types. Case-insensitive. */
    public static List<String> filter(List<String> candidates, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return List.copyOf(candidates);
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    static Boolean parseBool(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "1", "yes" -> Boolean.TRUE;
            case "false", "off", "0", "no" -> Boolean.FALSE;
            default -> null;
        };
    }
}
