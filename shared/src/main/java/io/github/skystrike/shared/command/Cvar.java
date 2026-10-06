package io.github.skystrike.shared.command;


/**
 * A typed, ranged, described console variable (console plan §7.3).
 *
 * <p>Values are stored canonically as strings produced by the argument type: a bool set with
 * {@code "1"} reads back as {@code "true"}. Range and format validation live in the type, so a
 * cvar can never hold a value its own declaration forbids.
 *
 * <p>The change hook fires exactly once per <i>effective</i> change — setting the current value
 * again is a no-op — and always after the value moved, never before.
 */
public final class Cvar {

    /** What changed, in strings: the previous canonical value and the new one. */
    @FunctionalInterface
    public interface ChangeHook {
        void changed(String previousValue, String newValue);
    }

    private final String name;
    private final ArgType<?> type;
    private final String description;
    private final Permission permission;
    private final String defaultValue;
    private final ChangeHook hook;

    private String value;

    private Cvar(Builder builder) throws CommandException {
        this.name = builder.name;
        this.type = builder.type;
        this.description = builder.description;
        this.permission = builder.permission;
        this.hook = builder.hook;
        // Parse the default so "1" as a bool default still reads canonically as "true".
        this.defaultValue = canonical(builder.defaultValue);
        this.value = this.defaultValue;
    }

    public static Builder builder(String name, ArgType<?> type, String defaultValue) {
        return new Builder(name, type, defaultValue);
    }

    public String name() {
        return name;
    }

    public ArgType<?> type() {
        return type;
    }

    public String description() {
        return description;
    }

    /** Level required to read or write this variable. Mirrors command permission rules. */
    public Permission permission() {
        return permission;
    }

    public String defaultValue() {
        return defaultValue;
    }

    /** The current value in canonical form. */
    public String value() {
        return value;
    }

    /**
     * Sets from a user-typed token.
     *
     * @return true when the value actually changed
     * @throws CommandException when the token is not a value of this cvar's type
     */
    public boolean set(String raw) throws CommandException {
        String canonical = canonical(raw);
        if (canonical.equals(value)) {
            return false;
        }
        String previous = value;
        value = canonical;
        if (hook != null) {
            hook.changed(previous, canonical);
        }
        return true;
    }

    /** Restores the default, firing the hook if that is a change. */
    public boolean reset() throws CommandException {
        return set(defaultValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String canonical(String raw) throws CommandException {
        Object parsed = ((ArgType) type).parse(raw == null ? "" : raw);
        return String.valueOf(parsed);
    }

    /** The lines printed when the cvar is typed with no value: current, default, what it is. */
    public java.util.List<String> describeLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(name + " = " + value + " (default: " + defaultValue + ")");
        lines.add("  " + type.describe() + (description.isEmpty() ? "" : " — " + description));
        return lines;
    }

    public static final class Builder {

        private final String name;
        private final ArgType<?> type;
        private final String defaultValue;
        private String description = "";
        private Permission permission = Permission.EVERYONE;
        private ChangeHook hook;

        private Builder(String name, ArgType<?> type, String defaultValue) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("cvar name is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("cvar type is required for " + name);
            }
            this.name = name.trim().toLowerCase(java.util.Locale.ROOT);
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public Builder permission(Permission permission) {
            this.permission = permission == null ? Permission.EVERYONE : permission;
            return this;
        }

        public Builder onChange(ChangeHook hook) {
            this.hook = hook;
            return this;
        }

        public Cvar build() {
            try {
                return new Cvar(this);
            } catch (CommandException badDefault) {
                throw new IllegalArgumentException(
                    name + ": default does not satisfy the type: " + badDefault.getMessage());
            }
        }
    }
}
