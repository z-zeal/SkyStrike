package io.github.skystrike.ui.console;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.chat.ChatClient;
import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.command.ClientCommandService;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.settings.ClientPreferences;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.text.ChatTarget;
import io.github.skystrike.ui.text.FontManager;
import io.github.skystrike.ui.text.MessageBuffer;
import io.github.skystrike.ui.text.MessageFormatter;
import io.github.skystrike.ui.text.MessageFormatter.StyledRun;
import io.github.skystrike.ui.text.MessageLine;
import io.github.skystrike.ui.text.MessageSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The one dialog behind the chat key: player chat and the console are the same panel
 * (console plan §3.1).
 *
 * <p>Closed, it renders the passive view — the last few lines fading with age — and owns no
 * input at all. Open, it owns <i>all</i> of it: every keystroke routes here through
 * {@link ConsoleFocus}, gameplay sees zero intent, and the player reads and writes freely.
 * Enter submits and closes; Enter on an empty bar closes; Escape closes keeping the draft; the
 * wheel walks the scrollback. The bar restyles live the moment a lone leading slash appears —
 * the same rule the submit path applies, so what the player sees is what the submit will do.
 *
 * <p>The dialog draws with its own batch, shape renderer and font, falling back to the stock
 * bitmap font when {@code fonts/dialog.ttf} is not present in assets.
 */
public final class ConsoleDialog extends InputAdapter implements Disposable {

    private static final String CUSTOM_FONT_PATH = "fonts/dialog.ttf";
    private static final long CARET_BLINK_MILLIS = 500L;
    private static final int MAX_SCROLLBACK_VISUAL_LINES = 400;
    private static final float MIN_WRAP_WIDTH = 40f;

    /** One wrapped visual line: run-coloured text plus the timestamp of its source message. */
    private record VisualLine(List<StyledRun> runs, long timestampMillis) {
    }

    /** One character and its colour, between formatting and wrapping. */
    private record CharColor(char c, Color color) {
    }

    /** Pointer hit target for one currently drawn completion row. */
    private record CompletionHit(int index, float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    private final ClientCommandService commands;
    private final ChatClient chatClient;
    private final MessageBuffer messages;
    private final ConsoleFocus focus;

    private final ConsoleTheme theme = new ConsoleTheme();
    private final ConsoleInputField field;
    private final ChatTargetButton targetButton;
    private final ConsoleCompletionPopup completion;
    private final ConsoleHintLine hintLine;
    private final MessageFormatter formatter = new MessageFormatter();
    private final List<CompletionHit> completionHits = new ArrayList<>();

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Matrix4 projection = new Matrix4();
    private final GlyphLayout measurer = new GlyphLayout();

    /** The freetype-backed font when the asset exists, otherwise the stock bitmap font. */
    private FontManager fontManager;
    private BitmapFont fallbackFont;
    private boolean fontChecked;

    private boolean open;
    private float openProgress;
    /** Draft kept by Escape; restored on next open. */
    private String keptDraft = "";
    /** Visual lines scrolled back from the newest while open. */
    private int scrollOffsetLines;
    /** Notified on close so the opener ignores this frame's key state (console plan §6.4). */
    private Runnable closeListener;

    // Wrapped-scrollback cache, keyed on buffer revision + wrap width.
    private long cachedRevision = -1;
    private float cachedWrapWidth = -1f;
    private final List<VisualLine> cachedWrapped = new ArrayList<>();

    private int measuredWidth = 1;
    private int measuredHeight = 1;

    public ConsoleDialog(
        ClientCommandService commands,
        ClientCapabilities capabilities,
        ChatClient chatClient,
        MessageBuffer messages,
        InputRouter router,
        ClientPreferences preferences,
        IntSupplier localPlayerId,
        Supplier<String> localPlayerName,
        Supplier<List<String>> connectedPlayerNames
    ) {
        if (commands == null || capabilities == null || chatClient == null || messages == null
            || router == null || preferences == null || localPlayerId == null
            || localPlayerName == null || connectedPlayerNames == null) {
            throw new IllegalArgumentException("all dialog collaborators are required");
        }
        this.commands = commands;
        this.chatClient = chatClient;
        this.messages = messages;
        this.focus = new ConsoleFocus(router);

        this.field = new ConsoleInputField(commands::isCommandLine);
        this.targetButton = new ChatTargetButton(preferences);
        this.completion = new ConsoleCompletionPopup(
            commands, capabilities, localPlayerId, localPlayerName, connectedPlayerNames);
        this.hintLine = new ConsoleHintLine(commands, capabilities);
    }

    public void setCloseListener(Runnable closeListener) {
        this.closeListener = closeListener;
    }

    // ---------------------------------------------------------------- open / close

    public boolean isOpen() {
        return open;
    }

    /** The one gate every gameplay reader checks (console plan §6.2). */
    public boolean isGameplayActive() {
        return focus.isGameplayActive();
    }

    /** The current ALL/TEAM destination, for any HUD badge that mirrors it. */
    public ChatTarget chatTarget() {
        return targetButton.target();
    }

    /**
     * Opens the dialog. Any draft kept by Escape comes back, so a player who closed on the wrong
     * key never loses a half-typed callout. Opening is keystroke-safe: the opener's key event
     * precedes focus, so the same press can never type into the freshly opened field.
     */
    public void open() {
        if (open) {
            return;
        }
        open = true;
        openProgress = 0f;
        scrollOffsetLines = 0;
        focus.take(this);
        if (!keptDraft.isEmpty()) {
            field.setText(keptDraft);
            keptDraft = "";
        }
    }

    private void close() {
        if (!open) {
            return;
        }
        open = false;
        keptDraft = field.text();
        focus.release();
        completion.clear();
        completionHits.clear();
        if (closeListener != null) {
            closeListener.run();
        }
    }

    // ---------------------------------------------------------------- input (open only)

    @Override
    public boolean keyDown(int keycode) {
        if (!open) {
            return false;
        }
        // Console plan §6.3: Escape is owned by one thing per press. The dialog holds focus, so
        // while open it is the dialog's; the surveillance view and the pause menu only ever see
        // the press when this processor is not on the stack.
        switch (keycode) {
            case Input.Keys.ESCAPE -> {
                close();
                return true;
            }
            case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> {
                submit();
                return true;
            }
            case Input.Keys.TAB -> {
                onTab();
                return true;
            }
            case Input.Keys.UP -> {
                if (!currentCandidates().isEmpty()) {
                    completion.cycle(-1);
                } else {
                    field.historyBack();
                }
                return true;
            }
            case Input.Keys.DOWN -> {
                if (!currentCandidates().isEmpty()) {
                    completion.cycle(1);
                } else {
                    field.historyForward();
                }
                return true;
            }
            case Input.Keys.LEFT -> {
                field.moveCaretLeft();
                return true;
            }
            case Input.Keys.RIGHT -> {
                field.moveCaretRight();
                return true;
            }
            case Input.Keys.HOME -> {
                field.moveCaretHome();
                return true;
            }
            case Input.Keys.END -> {
                field.moveCaretEnd();
                return true;
            }
            case Input.Keys.BACKSPACE -> {
                field.backspace();
                return true;
            }
            case Input.Keys.FORWARD_DEL -> {
                field.delete();
                return true;
            }
            case Input.Keys.PAGE_UP -> {
                scrollOffsetLines = Math.min(scrollOffsetLines + 8, maxScrollOffset());
                return true;
            }
            case Input.Keys.PAGE_DOWN -> {
                scrollOffsetLines = Math.max(0, scrollOffsetLines - 8);
                return true;
            }
            default -> {
                // Consume every other key: gameplay hears nothing while the bar owns focus.
                return true;
            }
        }
    }

    @Override
    public boolean keyTyped(char character) {
        if (!open) {
            return false;
        }
        if (character >= 0x20 && character != 0x7F) {
            field.typeChar(character);
        }
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!open) {
            return false;
        }
        // Input arrives y-down against this dialog's y-up projection.
        float y = measuredHeight - screenY;
        if (targetButton.touchDown(screenX, y)) {
            return true;
        }
        for (CompletionHit hit : completionHits) {
            if (hit.contains(screenX, y)) {
                completion.select(hit.index());
                acceptCompletion();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!open) {
            return false;
        }
        if (amountY > 0f) {
            scrollOffsetLines = Math.min(scrollOffsetLines + 3, maxScrollOffset());
        } else if (amountY < 0f) {
            scrollOffsetLines = Math.max(0, scrollOffsetLines - 3);
        }
        // Consumed while open, so the loadout controller's wheel slots stay silent.
        return true;
    }

    private void onTab() {
        if (!currentCandidates().isEmpty()) {
            acceptCompletion();
            return;
        }
        targetButton.onTabPressed();
    }

    private List<String> currentCandidates() {
        if (!field.isCommand()) {
            return List.of();
        }
        String body = field.text().substring(ClientCapabilities.COMMAND_PREFIX.length());
        return completion.candidatesFor(body);
    }

    /** Writes the selected completion back into the field, preserving what came before. */
    private void acceptCompletion() {
        String candidate = completion.selectedCandidate();
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        String body = field.text().substring(ClientCapabilities.COMMAND_PREFIX.length());
        boolean trailingSpace = body.endsWith(" ");
        String head;
        if (trailingSpace) {
            head = body;
        } else {
            int lastSpace = body.lastIndexOf(' ');
            head = lastSpace < 0 ? "" : body.substring(0, lastSpace + 1);
        }
        boolean completingFirstToken = head.isEmpty();
        String suffix = completingFirstToken || trailingSpace ? " " : "";
        field.setText(ClientCapabilities.COMMAND_PREFIX + head + candidate + suffix);
    }

    /**
     * Enter. Non-empty lines leave as a command or as a chat line and the bar closes; an empty
     * bar just closes. A command never produces a chat packet, and a chat line never reaches the
     * command engine — one submit, one destination, decided by the same rule the bar displays.
     */
    private void submit() {
        String text = field.text().trim();
        if (text.isEmpty()) {
            close();
            return;
        }
        if (commands.isCommandLine(text)) {
            commands.submit(text);
        } else {
            // ChatClient owns the //x -> /x unescape and the sanitising client-side preview.
            boolean sent = chatClient.send(targetButton.target(), text);
            if (!sent) {
                String reason = text.startsWith("//")
                    ? "Nothing to say after the escape."
                    : "Chat could not be sent (not connected).";
                chatClient.addSystemLine(
                    System.currentTimeMillis(), ChatChannel.SYSTEM, reason, MessageSeverity.WARNING);
            }
        }
        field.commit(text);
        close();
    }

    // ---------------------------------------------------------------- sizing / lifecycle

    public void resize(int width, int height) {
        measuredWidth = Math.max(1, width);
        measuredHeight = Math.max(1, height);
        projection.setToOrtho2D(0f, 0f, measuredWidth, measuredHeight);
        if (fontManager != null) {
            fontManager.resize(height, Gdx.graphics == null ? 1f : Gdx.graphics.getDensity());
        }
    }

    /** Drives the font lifecycle and the open transition. */
    public void update(float delta) {
        ensureFont();
        float target = open ? 1f : 0f;
        float step = delta / Math.max(0.01f, theme.openTransitionSeconds);
        if (openProgress < target) {
            openProgress = Math.min(target, openProgress + step);
        } else {
            openProgress = Math.max(target, openProgress - step);
        }
    }

    private void ensureFont() {
        if (fontChecked) {
            return;
        }
        fontChecked = true;
        if (Gdx.files != null && Gdx.files.internal(CUSTOM_FONT_PATH).exists()) {
            fontManager = new FontManager(CUSTOM_FONT_PATH);
        } else {
            fallbackFont = new BitmapFont();
            fallbackFont.setColor(Color.WHITE);
        }
    }

    private BitmapFont font() {
        return fontManager != null ? fontManager.font() : fallbackFont;
    }

    /**
     * Draws the passive view when closing, the full panel when opening/open. Must be called on
     * the render thread, after the world passes and the HUD, so it sits above everything.
     */
    public void render(long nowMillis) {
        ensureFont();
        BitmapFont font = font();
        if (font == null) {
            return;
        }
        float scale = ConsoleTheme.scale(measuredHeight);
        if (open || openProgress > 0.001f) {
            renderOpen(font, scale, nowMillis);
        } else {
            renderPassive(font, scale, nowMillis);
        }
    }

    // ---------------------------------------------------------------- scrollback model

    /**
     * The wrapped, run-coloured scrollback for the given width, rebuilt only when the buffer or
     * the width changed — never re-wrapped per frame.
     */
    private List<VisualLine> wrappedScrollback(float wrapWidth) {
        if (cachedRevision == messages.revision() && cachedWrapWidth == wrapWidth) {
            return cachedWrapped;
        }
        cachedRevision = messages.revision();
        cachedWrapWidth = wrapWidth;
        cachedWrapped.clear();
        // Console channels are rendered whenever the console exists for this client — the
        // server grant, or the local debug flag (playable build plan M1 §2.8: with the console
        // unlocked locally its own output must be readable, else /help would echo into the void).
        List<MessageLine> visible = messages.visibleLines(commands.unlocked());
        for (MessageLine line : visible) {
            wrapMessage(line, Math.max(MIN_WRAP_WIDTH, wrapWidth));
        }
        while (cachedWrapped.size() > MAX_SCROLLBACK_VISUAL_LINES) {
            cachedWrapped.remove(0);
        }
        return cachedWrapped;
    }

    /**
     * Formats one message into single-space-normalised per-character colours, then wraps it into
     * run-sliced visual lines. Owning the offsets here (rather than mapping a plain wrapper's
     * output back) is what keeps team colours intact on wrapped continuation lines.
     */
    private void wrapMessage(MessageLine line, float maxWidth) {
        List<CharColor> stream = normalised(line);
        if (stream.isEmpty()) {
            cachedWrapped.add(new VisualLine(List.of(), line.timestampMillis()));
            return;
        }
        int i = 0;
        int n = stream.size();
        StringBuilder candidate = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        while (i < n) {
            while (i < n && stream.get(i).c() == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            plain.setLength(0);
            int lastSpaceAt = -1;
            while (i < n) {
                char c = stream.get(i).c();
                candidate.setLength(0);
                candidate.append(plain).append(c);
                if (measure(candidate.toString()) > maxWidth) {
                    break;
                }
                plain.append(c);
                if (c == ' ') {
                    lastSpaceAt = plain.length() - 1;
                }
                i++;
            }
            if (i >= n) {
                emit(stream, n - plain.length(), plain.length(), line.timestampMillis());
                break;
            }
            if (plain.length() == 0) {
                // Nothing fits at all: emit one character so progress is guaranteed.
                emit(stream, i, 1, line.timestampMillis());
                i++;
                continue;
            }
            int breakAt = lastSpaceAt > 0 ? lastSpaceAt : plain.length();
            emit(stream, i - plain.length(), breakAt, line.timestampMillis());
            // Resume at the breaking character, or on the space the break consumed — the
            // leading-space skipper at the top of the loop cleans either up.
            i -= plain.length() - breakAt;
        }
    }

    /** Emits {@code stream[start, start+length)} as one visual line of styled runs. */
    private void emit(List<CharColor> stream, int start, int length, long timestamp) {
        int from = Math.max(0, start);
        int to = Math.min(stream.size(), from + Math.max(1, length));
        List<StyledRun> runs = new ArrayList<>();
        for (int index = from; index < to; index++) {
            CharColor cc = stream.get(index);
            if (!runs.isEmpty() && runs.get(runs.size() - 1).color().equals(cc.color())) {
                StyledRun previous = runs.remove(runs.size() - 1);
                runs.add(new StyledRun(previous.text() + cc.c(), previous.color()));
            } else {
                runs.add(new StyledRun(String.valueOf(cc.c()), cc.color()));
            }
        }
        cachedWrapped.add(new VisualLine(runs, timestamp));
    }

    /**
     * Flattens a message into per-character colours with whitespace collapsed to single spaces,
     * so offsets here are exactly the offsets the wrapper walks.
     */
    private List<CharColor> normalised(MessageLine line) {
        List<CharColor> stream = new ArrayList<>();
        boolean lastWasSpace = true;
        for (StyledRun run : formatter.format(line, false).runs()) {
            String runText = run.text();
            for (int i = 0; i < runText.length(); i++) {
                char c = runText.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (!lastWasSpace) {
                        stream.add(new CharColor(' ', run.color()));
                        lastWasSpace = true;
                    }
                } else {
                    stream.add(new CharColor(c, run.color()));
                    lastWasSpace = false;
                }
            }
        }
        if (!stream.isEmpty() && stream.get(stream.size() - 1).c() == ' ') {
            stream.remove(stream.size() - 1);
        }
        return stream;
    }

    private float measure(String text) {
        BitmapFont font = font();
        if (font == null) {
            return text.length() * 8f;
        }
        measurer.setText(font, text);
        return measurer.width;
    }

    // ---------------------------------------------------------------- drawing

    private void renderPassive(BitmapFont font, float scale, long nowMillis) {
        float margin = theme.margin * scale;
        float wrapWidth = preferredPanelWidth(measuredWidth, scale)
            - theme.innerPadding * 2f * scale;
        List<VisualLine> wrapped = wrappedScrollback(wrapWidth);
        if (wrapped.isEmpty()) {
            return;
        }
        float lineHeight = font.getLineHeight();
        float age = 0f;
        float y = margin;
        int shown = 0;
        batch.setProjectionMatrix(projection);
        batch.begin();
        for (int i = wrapped.size() - 1; i >= 0 && shown < theme.passiveLines; i--, shown++) {
            VisualLine visual = wrapped.get(i);
            age = Math.max(0f, (nowMillis - visual.timestampMillis()) / 1000f);
            float alpha = passiveAlpha(age);
            if (alpha <= 0.02f) {
                continue;
            }
            y += lineHeight + theme.lineGap * scale;
            drawRunLine(font, visual.runs(), margin, y, alpha);
        }
        batch.end();
        font.setColor(Color.WHITE);
    }

    private float passiveAlpha(float ageSeconds) {
        if (ageSeconds <= theme.passiveFadeDelay) {
            return 1f;
        }
        float fade = (ageSeconds - theme.passiveFadeDelay) / theme.passiveFadeDuration;
        return Math.max(0f, 1f - fade);
    }

    private void renderOpen(BitmapFont font, float scale, long nowMillis) {
        float margin = theme.margin * scale;
        float panelWidth = preferredPanelWidth(measuredWidth, scale);
        float panelHeight = measuredHeight * theme.openHeightFraction;
        float inputHeight = theme.inputHeight * scale;
        float panelBottom = margin + inputHeight + theme.innerPadding * scale * 0.5f;
        float alpha = Math.min(1f, Math.max(0f, openProgress));

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color fill = theme.panelFill;
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        shapes.rect(margin - 2f, panelBottom, panelWidth + 4f, panelHeight);
        Color edge = theme.panelEdge;
        shapes.setColor(edge.r, edge.g, edge.b, edge.a * alpha);
        shapes.rect(margin - 2f, panelBottom + panelHeight - 1.5f, panelWidth + 4f, 1.5f);
        Color inputFill = theme.inputFill;
        shapes.setColor(inputFill.r, inputFill.g, inputFill.b, inputFill.a * alpha);
        shapes.rect(margin - 2f, margin, panelWidth + 4f, inputHeight);
        float buttonWidth = theme.buttonWidth * scale;
        targetButton.layout(margin, margin, buttonWidth, inputHeight);
        Color buttonColor = theme.buttonFill;
        shapes.setColor(buttonColor.r, buttonColor.g, buttonColor.b, buttonColor.a * alpha);
        shapes.rect(targetButton.x(), targetButton.y(), targetButton.width(), targetButton.height());
        Color buttonEdge = theme.buttonEdge;
        shapes.setColor(buttonEdge.r, buttonEdge.g, buttonEdge.b, buttonEdge.a * alpha);
        shapes.rect(targetButton.x() + targetButton.width() - 1.5f, targetButton.y(),
            1.5f, targetButton.height());
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();

        float textMargin = margin + theme.innerPadding * scale;
        float wrapWidth = panelWidth
            - (theme.innerPadding * 2f + theme.scrollbarWidth) * scale;
        List<VisualLine> wrapped = wrappedScrollback(wrapWidth);
        float lineHeight = font.getLineHeight();
        float strut = theme.lineGap * scale;
        int visibleCap = (int) ((panelHeight - theme.innerPadding * scale) / (lineHeight + strut));
        int top = wrapped.size() - 1 - Math.min(scrollOffsetLines, maxScrollOffset());
        float y = panelBottom + theme.innerPadding * scale * 0.5f + lineHeight;
        for (int i = top, drawn = 0; i >= 0 && drawn < visibleCap; i--, drawn++) {
            drawRunLine(font, wrapped.get(i).runs(), textMargin, y, alpha);
            y += lineHeight + strut;
        }

        // The hint line, flush above the panel.
        float fieldX = targetButton.x() + targetButton.width() + theme.innerPadding * scale * 0.5f;
        float fieldTextY = margin + inputHeight * 0.68f;
        if (alpha > 0.4f) {
            String hint = hintLine.hintFor(field.text());
            if (!hint.isEmpty()) {
                font.setColor(theme.clampedText(theme.hintText));
                font.draw(batch, hint, fieldX, panelBottom + panelHeight + lineHeight * 0.75f);
            }
            List<String> candidates = currentCandidates();
            completionHits.clear();
            if (!candidates.isEmpty()) {
                int rows = ConsoleCompletionPopup.visibleCount(
                    candidates, theme.completionMaxRows);
                int first = Math.max(0, Math.min(
                    completion.selected() - rows + 1, candidates.size() - rows));
                for (int r = 0; r < rows; r++) {
                    int index = first + r;
                    String candidate = candidates.get(index);
                    Color c = index == completion.selected()
                        ? theme.clampedText(theme.inputTextCommand)
                        : theme.clampedText(theme.hintText);
                    font.setColor(c);
                    float baseline = panelBottom + panelHeight + lineHeight * (r + 1.75f);
                    font.draw(batch, candidate, fieldX, baseline);
                    measurer.setText(font, candidate);
                    completionHits.add(new CompletionHit(
                        index, fieldX, baseline - lineHeight, measurer.width, lineHeight));
                }
            }
        } else {
            completionHits.clear();
        }

        // The ALL/TEAM label.
        font.setColor(targetButton.labelColor());
        measurer.setText(font, targetButton.label());
        font.draw(batch, targetButton.label(),
            targetButton.x() + (targetButton.width() - measurer.width) * 0.5f, fieldTextY);

        // The field: command-styled text and a blinking caret.
        boolean command = field.isCommand();
        font.setColor(theme.clampedText(theme.inputTextFor(command)));
        String typed = field.text();
        font.draw(batch, typed, fieldX, fieldTextY);
        if ((nowMillis / CARET_BLINK_MILLIS) % 2 == 0) {
            measurer.setText(font, typed.substring(0, Math.min(field.caret(), typed.length())));
            font.setColor(theme.caret);
            font.draw(batch, "_", fieldX + measurer.width + 1f, fieldTextY);
        }

        batch.end();
        font.setColor(Color.WHITE);
    }

    /** Draws one visual line run by run, left to right. */
    private void drawRunLine(BitmapFont font, List<StyledRun> runs, float x, float y, float alpha) {
        float cursor = x;
        for (StyledRun run : runs) {
            Color c = run.color();
            font.setColor(c.r, c.g, c.b, c.a * alpha);
            font.draw(batch, run.text(), cursor, y);
            measurer.setText(font, run.text());
            cursor += measurer.width;
        }
    }

    private int maxScrollOffset() {
        float scale = ConsoleTheme.scale(measuredHeight);
        float wrapWidth = preferredPanelWidth(measuredWidth, scale)
            - (theme.innerPadding * 2f + theme.scrollbarWidth) * scale;
        return Math.max(0, wrappedScrollback(wrapWidth).size() - 1);
    }

    private float preferredPanelWidth(int screenWidth, float scale) {
        float desired = screenWidth * theme.panelWidthFraction;
        return Math.max(
            theme.minPanelWidth * scale, Math.min(theme.maxPanelWidth * scale, desired));
    }

    @Override
    public void dispose() {
        if (focus.isFocused()) {
            focus.release();
        }
        batch.dispose();
        shapes.dispose();
        if (fontManager != null) {
            fontManager.dispose();
        }
        if (fallbackFont != null) {
            fallbackFont.dispose();
        }
    }
}
