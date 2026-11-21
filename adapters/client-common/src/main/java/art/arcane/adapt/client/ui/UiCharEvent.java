package art.arcane.adapt.client.ui;

public record UiCharEvent(int codepoint, UiModifiers modifiers) {
    public String text() {
        return new String(Character.toChars(codepoint));
    }
}
