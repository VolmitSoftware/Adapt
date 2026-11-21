package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFieldTest {
    private static final UiModifiers NONE = UiModifiers.NONE;
    private static final UiModifiers SHIFT = new UiModifiers(true, false, false, false);
    private static final UiModifiers WORD = new UiModifiers(false, true, false, false);
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
    private static final UiModifiers SHORTCUT = MAC ? new UiModifiers(false, false, false, true) : new UiModifiers(false, true, false, false);

    private final ManualClock clock = new ManualClock();
    private final FakeHost host = new FakeHost(427, 240);
    private final List<String> commits = new ArrayList<>();
    private final List<String> changes = new ArrayList<>();
    private TextField field;
    private TestScreen screen;

    private void open(int maxLength) {
        field = new TextField("field", maxLength);
        field.setOnCommit(commits::add).setOnChange(changes::add);
        screen = new TestScreen(clock.motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                field.place(new UiRect(10, 10, 108, 20), metrics));
        screen.root().add(field);
        host.open(screen);
        screen.render(new KitDraw(), 0, 0, 0.0F);
        screen.focus(field);
    }

    private void type(String text) {
        text.codePoints().forEach((int codePoint) -> screen.charTyped(new UiCharEvent(codePoint, NONE)));
    }

    private void press(UiKey key, UiModifiers modifiers) {
        screen.keyPressed(new UiKeyEvent(key, modifiers));
    }

    @Test
    void typingInsertsAtTheCaretAndEnterCommits() {
        open(32);
        type("hello");
        press(UiKey.LEFT, NONE);
        press(UiKey.LEFT, NONE);
        type("XY");
        assertEquals("helXYlo", field.text());
        assertEquals(5, field.caret());
        assertTrue(field.dirty());
        press(UiKey.ENTER, NONE);
        assertEquals(List.of("helXYlo"), commits);
        assertFalse(field.dirty());
        assertEquals("helXYlo", changes.get(changes.size() - 1));
    }

    @Test
    void shiftArrowsSelectAndTypingReplacesTheSelection() {
        open(32);
        type("abcdef");
        press(UiKey.HOME, NONE);
        press(UiKey.RIGHT, SHIFT);
        press(UiKey.RIGHT, SHIFT);
        assertEquals("ab", field.selectedText());
        type("Z");
        assertEquals("Zcdef", field.text());
        press(UiKey.END, SHIFT);
        assertEquals("cdef", field.selectedText());
        press(UiKey.LEFT, NONE);
        assertEquals(1, field.caret());
        assertEquals("", field.selectedText());
    }

    @Test
    void wordJumpsAndWordDeletes() {
        open(64);
        type("one two  three");
        press(UiKey.LEFT, WORD);
        assertEquals(9, field.caret());
        press(UiKey.BACKSPACE, WORD);
        assertEquals("one three", field.text());
        press(UiKey.HOME, NONE);
        press(UiKey.DELETE, WORD);
        assertEquals("three", field.text());
        press(UiKey.RIGHT, WORD);
        assertEquals(5, field.caret());
    }

    @Test
    void clipboardCopyCutPasteAndSelectAll() {
        open(32);
        type("copy me");
        press(UiKey.A, SHORTCUT);
        assertEquals("copy me", field.selectedText());
        press(UiKey.C, SHORTCUT);
        assertEquals("copy me", host.clipboard());
        press(UiKey.X, SHORTCUT);
        assertEquals("", field.text());
        press(UiKey.V, SHORTCUT);
        press(UiKey.V, SHORTCUT);
        assertEquals("copy mecopy me", field.text());
        screen.charTyped(new UiCharEvent('v', SHORTCUT));
        assertEquals("copy mecopy me", field.text());
    }

    @Test
    void maxLengthAndControlCharactersAreEnforced() {
        open(5);
        type("abc");
        host.setClipboard("de\nfgh");
        press(UiKey.V, SHORTCUT);
        assertEquals("abcde", field.text());
        type("z");
        assertEquals("abcde", field.text());
        field.setText("0123456789");
        assertEquals("01234", field.text());
        assertFalse(field.dirty());
    }

    @Test
    void surrogatePairsMoveAndDeleteAsOneCharacter() {
        open(10);
        type("a😀b");
        press(UiKey.LEFT, NONE);
        press(UiKey.LEFT, NONE);
        assertEquals(1, field.caret());
        press(UiKey.DELETE, NONE);
        assertEquals("ab", field.text());
        field.setMaxLength(2);
        field.setText("x😀");
        assertEquals("x", field.text());
    }

    @Test
    void lettersAndSpaceAreSwallowedWhileEditingButEscapeAndTabAreNot() {
        open(10);
        assertTrue(field.keyPressed(new UiKeyEvent(UiKey.Q, NONE)));
        assertTrue(field.keyPressed(new UiKeyEvent(UiKey.SPACE, NONE)));
        assertFalse(field.keyPressed(new UiKeyEvent(UiKey.ESCAPE, NONE)));
        assertFalse(field.keyPressed(new UiKeyEvent(UiKey.TAB, NONE)));
        assertTrue(screen.editingText());
    }

    @Test
    void clickingPlacesTheCaretByTextWidth() {
        open(32);
        type("abcdef");
        screen.mouseClicked(TestScreen.left(10 + 4 + 13, 15));
        assertEquals(2, field.caret());
        screen.mouseDragged(TestScreen.left(10 + 4 + 31, 15), 18, 0);
        assertEquals("cde", field.selectedText());
    }

    @Test
    void longTextScrollsToKeepTheCaretVisible() {
        open(64);
        type("abcdefghijklmnopqrstuvwxyz");
        KitDraw draw = new KitDraw();
        screen.render(draw, 0, 0, 0.0F);
        assertTrue(draw.calls().stream().anyMatch((String call) -> call.startsWith("text 14,") && call.endsWith("klmnopqrstuvwxyz")),
                draw.calls().toString());
    }
}
