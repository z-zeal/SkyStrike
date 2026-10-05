package io.github.skystrike.fx.gl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shader loader and cache supporting recursive {@code #include} directives.
 *
 * <p>Resolves included files relative to the shader asset root ({@code shaders/}), detects cyclic
 * dependencies, and throws detailed compiler errors on syntax failures.
 */
public final class ShaderLibrary implements Disposable {

    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("^\\s*#include\\s+[\"<]([^\">]+)[\">]", Pattern.MULTILINE);

    private final String shaderRoot;
    private final Map<String, ShaderProgram> shaders = new HashMap<>();

    public ShaderLibrary() {
        this("shaders/");
    }

    public ShaderLibrary(String shaderRoot) {
        this.shaderRoot = shaderRoot.endsWith("/") ? shaderRoot : shaderRoot + "/";
    }

    /**
     * Loads, preprocesses and compiles a shader pair.
     *
     * @param name unique identifier / cache key
     * @param vertPath path relative to {@code shaderRoot}
     * @param fragPath path relative to {@code shaderRoot}
     * @return compiled {@link ShaderProgram}
     */
    public ShaderProgram load(String name, String vertPath, String fragPath) {
        if (shaders.containsKey(name)) {
            return shaders.get(name);
        }

        String vertSource = loadSource(vertPath, new HashSet<>());
        String fragSource = loadSource(fragPath, new HashSet<>());

        ShaderProgram program = new ShaderProgram(vertSource, fragSource);
        if (!program.isCompiled()) {
            String errorMsg = String.format(
                    "Failed to compile shader '%s' (vert: %s, frag: %s):\n%s",
                    name, vertPath, fragPath, program.getLog());
            Gdx.app.error("ShaderLibrary", errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        shaders.put(name, program);
        return program;
    }

    /**
     * Retrieves an already loaded shader.
     */
    public ShaderProgram get(String name) {
        ShaderProgram program = shaders.get(name);
        if (program == null) {
            throw new IllegalArgumentException("Shader not found: " + name);
        }
        return program;
    }

    public boolean has(String name) {
        return shaders.containsKey(name);
    }

    private String loadSource(String relativePath, Set<String> visited) {
        String cleanPath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        if (visited.contains(cleanPath)) {
            throw new IllegalStateException("Circular #include detected: " + cleanPath);
        }
        visited.add(cleanPath);

        FileHandle file = Gdx.files.internal(shaderRoot + cleanPath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Shader source file not found: " + shaderRoot + cleanPath);
        }

        String source = file.readString("UTF-8");
        StringBuilder resolved = new StringBuilder();

        Matcher matcher = INCLUDE_PATTERN.matcher(source);
        int lastEnd = 0;
        while (matcher.find()) {
            resolved.append(source, lastEnd, matcher.start());
            String includeTarget = matcher.group(1);
            String includedSource = loadSource(includeTarget, new HashSet<>(visited));
            resolved.append(includedSource).append("\n");
            lastEnd = matcher.end();
        }
        resolved.append(source.substring(lastEnd));

        return resolved.toString();
    }

    @Override
    public void dispose() {
        for (ShaderProgram program : shaders.values()) {
            program.dispose();
        }
        shaders.clear();
    }
}
