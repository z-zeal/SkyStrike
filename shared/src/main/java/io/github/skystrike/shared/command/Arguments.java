package io.github.skystrike.shared.command;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The coerced argument values of one invocation, addressed by the name the spec declared.
 *
 * <p>Built by {@link CommandParser} and handed to the handler. Optional arguments a caller did
 * not supply are simply absent — {@link #contains(String)} is how a handler tells "omitted"
 * apart from a value that happens to look like a default.
 */
public final class Arguments {

    private final Map<String, Object> values = new LinkedHashMap<>();

    void put(String name, Object value) {
        values.put(name, value);
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    public Object get(String name) {
        return values.get(name);
    }

    public String getString(String name) {
        return (String) values.get(name);
    }

    public Integer getInt(String name) {
        return (Integer) values.get(name);
    }

    public int getInt(String name, int fallback) {
        Integer value = (Integer) values.get(name);
        return value == null ? fallback : value;
    }

    public Float getFloat(String name) {
        return (Float) values.get(name);
    }

    public boolean getBool(String name, boolean fallback) {
        Boolean value = (Boolean) values.get(name);
        return value == null ? fallback : value;
    }

    public PlayerRef getPlayer(String name) {
        return (PlayerRef) values.get(name);
    }

    /** Names present, in declaration order. Exposed for diagnostics. */
    public Iterable<String> names() {
        return values.keySet();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
