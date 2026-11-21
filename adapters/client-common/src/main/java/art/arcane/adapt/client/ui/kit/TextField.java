package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A single-line text field: caret, selection (shift + arrows, mouse drag, select all), word jumps (Ctrl or Alt),
 * clipboard (Cmd on macOS, Ctrl elsewhere), Home/End, and a maximum length. Enter commits; every edit reports the
 * new text. Positions are UTF-16 indices that never split a surrogate pair.
 */
public class TextField extends UiNode {
    private static final long BLINK_MILLIS = 530L;
    private static final int INSET = 4;

    private String text = "";
    private String placeholder = "";
    private int maxLength;
    private int caret;
    private int anchor;
    private int scroll;
    private boolean dirty;
    private Consumer<String> onChange = (String value) -> {
    };
    private Consumer<String> onCommit = (String value) -> {
    };
    private UiDraw metrics;
    private long caretMovedAt;

    public TextField(String id, int maxLength) {
        super(id);
        this.maxLength = Math.max(1, maxLength);
    }

    @Override
    public String kind() {
        return "textfield";
    }

    @Override
    public String label() {
        return LegacyText.strip(placeholder);
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("value", text);
        state.put("caret", Integer.toString(caret));
        state.put("selectionStart", Integer.toString(selectionStart()));
        state.put("selectionEnd", Integer.toString(selectionEnd()));
        state.put("dirty", Boolean.toString(dirty));
    }

    public String text() {
        return text;
    }

    /** Replaces the text without notifying listeners, puts the caret at the end and clears the dirty mark. */
    public TextField setText(String value) {
        text = clip(sanitize(Objects.requireNonNull(value, "text")));
        caret = text.length();
        anchor = caret;
        scroll = 0;
        dirty = false;
        return this;
    }

    public TextField setPlaceholder(String hint) {
        placeholder = Objects.requireNonNull(hint, "placeholder");
        return this;
    }

    public TextField setMaxLength(int length) {
        maxLength = Math.max(1, length);
        text = clip(text);
        caret = Math.min(caret, text.length());
        anchor = Math.min(anchor, text.length());
        return this;
    }

    public int maxLength() {
        return maxLength;
    }

    public TextField setOnChange(Consumer<String> listener) {
        onChange = Objects.requireNonNull(listener, "onChange");
        return this;
    }

    public TextField setOnCommit(Consumer<String> listener) {
        onCommit = Objects.requireNonNull(listener, "onCommit");
        return this;
    }

    /** Whether the user edited the text since the last {@link #setText} or commit. */
    public boolean dirty() {
        return dirty;
    }

    public int caret() {
        return caret;
    }

    public int selectionStart() {
        return Math.min(caret, anchor);
    }

    public int selectionEnd() {
        return Math.max(caret, anchor);
    }

    public String selectedText() {
        return text.substring(selectionStart(), selectionEnd());
    }

    /** Moves the caret; with {@code select} the selection anchor stays put. */
    public void moveCaret(int position, boolean select) {
        caret = snap(Math.max(0, Math.min(text.length(), position)));
        if (!select) {
            anchor = caret;
        }
        caretMovedAt = services().motion().now();
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    /** Replaces the selection with {@code typed}, dropping control characters and whatever exceeds the maximum length. */
    public void insert(String typed) {
        String clean = sanitize(typed);
        int start = selectionStart();
        int end = selectionEnd();
        int room = maxLength - (text.length() - (end - start));
        if (clean.length() > room) {
            clean = cut(clean, Math.max(0, room));
        }
        if (clean.isEmpty() && start == end) {
            return;
        }
        edit(text.substring(0, start) + clean + text.substring(end), start + clean.length());
    }

    public void deleteBackward(boolean word) {
        if (selectionStart() != selectionEnd()) {
            insert("");
            return;
        }
        int from = word ? previousWord(caret) : previous(caret);
        if (from < caret) {
            edit(text.substring(0, from) + text.substring(caret), from);
        }
    }

    public void deleteForward(boolean word) {
        if (selectionStart() != selectionEnd()) {
            insert("");
            return;
        }
        int to = word ? nextWord(caret) : next(caret);
        if (to > caret) {
            edit(text.substring(0, caret) + text.substring(to), caret);
        }
    }

    /** Reports the text to the commit listener and clears the dirty mark. */
    public void commit() {
        dirty = false;
        onCommit.accept(text);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public boolean capturesText() {
        return true;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().controlHeight();
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        UiModifiers modifiers = event.modifiers();
        boolean shift = modifiers.shift();
        boolean word = modifiers.control() || modifiers.alt();
        if (modifiers.shortcut()) {
            switch (event.key()) {
                case A -> {
                    selectAll();
                    return true;
                }
                case C -> {
                    copy();
                    return true;
                }
                case X -> {
                    copy();
                    insert("");
                    return true;
                }
                case V -> {
                    insert(services().clipboard());
                    return true;
                }
                default -> {
                }
            }
        }
        switch (event.key()) {
            case LEFT -> {
                if (!shift && !word && selectionStart() != selectionEnd()) {
                    moveCaret(selectionStart(), false);
                } else {
                    moveCaret(word ? previousWord(caret) : previous(caret), shift);
                }
                return true;
            }
            case RIGHT -> {
                if (!shift && !word && selectionStart() != selectionEnd()) {
                    moveCaret(selectionEnd(), false);
                } else {
                    moveCaret(word ? nextWord(caret) : next(caret), shift);
                }
                return true;
            }
            case HOME -> {
                moveCaret(0, shift);
                return true;
            }
            case END -> {
                moveCaret(text.length(), shift);
                return true;
            }
            case BACKSPACE -> {
                deleteBackward(word);
                return true;
            }
            case DELETE -> {
                deleteForward(word);
                return true;
            }
            case ENTER -> {
                commit();
                return true;
            }
            case ESCAPE, TAB, UP, DOWN, PAGE_UP, PAGE_DOWN -> {
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public boolean charTyped(UiCharEvent event) {
        if (event.modifiers().shortcut() && !event.modifiers().alt()) {
            return true;
        }
        insert(event.text());
        return true;
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (event.button() != UiMouseButton.LEFT) {
            return false;
        }
        moveCaret(positionAt(event.x()), event.modifiers().shift());
        return true;
    }

    @Override
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        moveCaret(positionAt(event.x()), true);
        return true;
    }

    @Override
    protected void focusChanged(boolean focused) {
        if (!focused) {
            anchor = caret;
        }
    }

    /** Text index nearest to the pointer x, using the widths of the last frame. */
    public int positionAt(double x) {
        if (metrics == null) {
            return text.length();
        }
        double local = x - (bounds().x() + INSET);
        int start = scroll;
        int best = start;
        int index = start;
        while (index < text.length()) {
            int following = next(index);
            int right = metrics.textWidth(text.substring(start, following));
            int left = metrics.textWidth(text.substring(start, index));
            if (local < (left + right) / 2.0) {
                return index;
            }
            best = following;
            index = following;
        }
        return best;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        metrics = draw;
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean focused = frame.isFocused(this);
        boolean active = enabled();
        UiTome.paper(draw, UiTome.FIELD, null, box, theme.tooltip(), theme.tileBorder());
        if (focused || frame.isHovered(this) && active) {
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), focused ? theme.focus() : theme.surfaceBorder());
        }
        int innerWidth = Math.max(0, box.width() - INSET * 2);
        keepCaretVisible(draw, innerWidth);
        int textX = box.x() + INSET;
        int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
        draw.pushScissor(box.x() + 1, box.y() + 1, box.right() - INSET, box.bottom() - 1);
        if (text.isEmpty() && !focused) {
            draw.text(LegacyText.truncate(draw, placeholder, innerWidth), textX, textY, theme.textDisabled(), false);
        }
        String visible = focused ? text.substring(scroll) : LegacyText.truncate(draw, text, innerWidth);
        if (focused && selectionStart() != selectionEnd()) {
            int from = Math.max(selectionStart(), scroll);
            int to = Math.max(selectionEnd(), scroll);
            int left = textX + draw.textWidth(text.substring(scroll, from));
            int right = textX + draw.textWidth(text.substring(scroll, to));
            draw.fill(left, textY - 1, Math.min(right, box.right() - 1), textY + draw.lineHeight() - 1, theme.selection());
        }
        draw.text(visible, textX, textY, active ? theme.text() : theme.textDisabled(), false);
        if (focused && active && caretShown(frame.motion())) {
            int caretX = textX + draw.textWidth(text.substring(scroll, Math.max(scroll, caret)));
            draw.fill(caretX, textY - 1, caretX + 1, textY + draw.lineHeight() - 1, theme.caret());
        }
        draw.popScissor();
    }

    private boolean caretShown(UiMotion motion) {
        return motion.reduced() || (motion.now() - caretMovedAt) / BLINK_MILLIS % 2 == 0;
    }

    private void keepCaretVisible(UiDraw draw, int innerWidth) {
        scroll = Math.min(scroll, text.length());
        if (caret < scroll) {
            scroll = caret;
        }
        while (scroll < caret && draw.textWidth(text.substring(scroll, caret)) > innerWidth - 1) {
            scroll = next(scroll);
        }
        while (scroll > 0 && draw.textWidth(text.substring(previous(scroll))) <= innerWidth - 1) {
            scroll = previous(scroll);
        }
    }

    private void copy() {
        if (selectionStart() != selectionEnd()) {
            services().setClipboard(selectedText());
        }
    }

    private void edit(String value, int newCaret) {
        text = value;
        caret = snap(newCaret);
        anchor = caret;
        dirty = true;
        caretMovedAt = services().motion().now();
        onChange.accept(text);
    }

    private int previous(int index) {
        return index <= 0 ? 0 : text.offsetByCodePoints(index, -1);
    }

    private int next(int index) {
        return index >= text.length() ? text.length() : text.offsetByCodePoints(index, 1);
    }

    private int previousWord(int index) {
        int position = index;
        while (position > 0 && Character.isWhitespace(text.charAt(position - 1))) {
            position--;
        }
        while (position > 0 && !Character.isWhitespace(text.charAt(position - 1))) {
            position--;
        }
        return position;
    }

    private int nextWord(int index) {
        int position = index;
        while (position < text.length() && !Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }

    private int snap(int index) {
        if (index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) {
            return index - 1;
        }
        return index;
    }

    private String clip(String value) {
        return value.length() <= maxLength ? value : cut(value, maxLength);
    }

    private static String cut(String value, int length) {
        int end = Math.min(length, value.length());
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String sanitize(String value) {
        StringBuilder clean = new StringBuilder(value.length());
        value.codePoints().filter((int codePoint) -> codePoint >= 0x20 && codePoint != 0x7F)
                .forEach(clean::appendCodePoint);
        return clean.toString();
    }
}
