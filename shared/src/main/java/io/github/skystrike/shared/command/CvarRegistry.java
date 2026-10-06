package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lookup over every registered cvar, sharing the command namespace (console plan §7.3).
 *
 * <p>Typing {@code /r_shadows} with no value prints its current value, default and description;
 * typing it with a value sets it. That dispatch lives in {@link CommandDispatcher}; this class
 * owns registration, permission-filtered listing and completion so cvars behave identically to
 * commands for the person typing.
 */
public final class CvarRegistry {

    private final Map<String, Cvar> byName = new HashMap<>();

    /** Registers one cvar. Duplicate names are a bug, not an override. */
    public void register(Cvar cvar) {
        if (cvar == null) {
            throw new IllegalArgumentException("cvar is required");
        }
        Cvar previous = byName.putIfAbsent(cvar.name(), cvar);
        if (previous != null && previous != cvar) {
            throw new IllegalStateException("cvar '" + cvar.name() + "' is registered twice");
        }
    }

    public Cvar find(String name) {
        if (name == null) {
            return null;
        }
        return byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Every cvar visible at {@code permission}, sorted — mirrors command filtering exactly. */
    public List<Cvar> visibleTo(Permission permission) {
        Permission level = permission == null ? Permission.EVERYONE : permission;
        List<Cvar> visible = new ArrayList<>();
        for (Cvar cvar : byName.values()) {
            if (level.atLeast(cvar.permission())) {
                visible.add(cvar);
            }
        }
        visible.sort(Comparator.comparing(Cvar::name));
        return visible;
    }

    /** Names available at {@code permission} starting with {@code prefix}, for completion. */
    public List<String> completeNames(String prefix, Permission permission) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Cvar cvar : visibleTo(permission)) {
            if (cvar.name().startsWith(lower)) {
                out.add(cvar.name());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    public int size() {
        return byName.size();
    }
}
