package io.github.skystrike.ui.text;

import com.badlogic.gdx.graphics.Color;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.text.ChatChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts structured records into colour-safe text runs immediately before layout/drawing.
 *
 * <p>No user body is treated as markup. The renderer draws each run directly, which keeps team
 * names readable without allowing a player to inject colours or formatting into the body.
 */
public final class MessageFormatter {

    private static final float MIN_TEXT_LUMINANCE = 0.62f;

    private static final Color BODY = new Color(0.96f, 0.97f, 1f, 1f);
    private static final Color MUTED = new Color(0.67f, 0.70f, 0.76f, 1f);
    private static final Color AMBER = new Color(1f, 0.72f, 0.20f, 1f);
    private static final Color GREEN = new Color(0.34f, 0.93f, 0.53f, 1f);
    private static final Color RED = new Color(1f, 0.40f, 0.42f, 1f);
    private static final Color TEAM_A = new Color(0.32f, 0.68f, 1f, 1f);
    private static final Color TEAM_B = new Color(1f, 0.43f, 0.36f, 1f);
    private static final Color NEUTRAL = new Color(0.82f, 0.84f, 0.87f, 1f);

    /** One independently coloured, markup-free run. */
    public record StyledRun(String text, Color color) {
        public StyledRun {
            text = text == null ? "" : text;
            color = new Color(Objects.requireNonNull(color, "color"));
        }

        @Override
        public Color color() {
            return new Color(color);
        }
    }

    /** Result of formatting one record, with a plain-text view for wrapping and accessibility. */
    public record FormattedMessage(List<StyledRun> runs) {
        public FormattedMessage {
            runs = List.copyOf(runs == null ? List.of() : runs);
        }

        public String plainText() {
            StringBuilder text = new StringBuilder();
            for (StyledRun run : runs) {
                text.append(run.text());
            }
            return text.toString();
        }
    }

    /** Turns a record into timestamp, channel, author and body runs. */
    public FormattedMessage format(MessageLine line, boolean includeTimestamp) {
        Objects.requireNonNull(line, "line");
        List<StyledRun> runs = new ArrayList<>(4);
        if (includeTimestamp) {
            runs.add(new StyledRun('[' + clockLabel(line.timestampMillis()) + "] ", MUTED));
        }

        String tag = channelTag(line.channel());
        if (!tag.isEmpty()) {
            runs.add(new StyledRun('[' + tag + "] ", channelColor(line.channel(), line.severity())));
        }
        if (line.hasAuthor()) {
            runs.add(new StyledRun(line.authorName() + ": ", teamColor(line.authorTeam())));
        }
        runs.add(new StyledRun(line.body(), bodyColor(line.severity())));
        return new FormattedMessage(runs);
    }

    /** Team/name colours always meet the HUD's minimum luminance floor. */
    public Color teamColor(Team team) {
        Color candidate = switch (team == null ? Team.NEUTRAL : team) {
            case TEAM_A -> TEAM_A;
            case TEAM_B -> TEAM_B;
            case NEUTRAL -> NEUTRAL;
        };
        return withMinimumLuminance(candidate, MIN_TEXT_LUMINANCE);
    }

    /** Applies a luminance floor by mixing only toward white, preserving hue as far as possible. */
    public static Color withMinimumLuminance(Color source, float minimum) {
        Objects.requireNonNull(source, "source");
        if (minimum < 0f || minimum > 1f) {
            throw new IllegalArgumentException("minimum luminance must be in [0, 1]");
        }
        float luminance = luminance(source);
        if (luminance >= minimum || luminance >= 1f) {
            return new Color(source);
        }
        float towardWhite = (minimum - luminance) / (1f - luminance);
        return new Color(
            source.r + (1f - source.r) * towardWhite,
            source.g + (1f - source.g) * towardWhite,
            source.b + (1f - source.b) * towardWhite,
            source.a);
    }

    /** Perceptual relative luminance for contrast checks and debug backgrounds. */
    public static float luminance(Color color) {
        Objects.requireNonNull(color, "color");
        return 0.2126f * color.r + 0.7152f * color.g + 0.0722f * color.b;
    }

    private static String clockLabel(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        long minutes = (seconds / 60L) % 60L;
        return String.format("%02d:%02d", minutes, seconds % 60L);
    }

    private static String channelTag(ChatChannel channel) {
        return switch (channel) {
            case ALL, TEAM -> channel.name();
            case SYSTEM -> "SYSTEM";
            case SERVER -> "SERVER";
            case CONSOLE -> "CONSOLE";
            case COMMAND_ECHO -> ">";
            case DEBUG -> "DEBUG";
        };
    }

    private static Color channelColor(ChatChannel channel, MessageSeverity severity) {
        if (severity == MessageSeverity.ERROR) {
            return RED;
        }
        return switch (channel) {
            case SYSTEM, SERVER, CONSOLE -> AMBER;
            case COMMAND_ECHO, DEBUG -> MUTED;
            case ALL, TEAM -> BODY;
        };
    }

    private static Color bodyColor(MessageSeverity severity) {
        return switch (severity) {
            case SUCCESS -> GREEN;
            case WARNING -> AMBER;
            case ERROR -> RED;
            case INFO, DEBUG -> BODY;
        };
    }
}
