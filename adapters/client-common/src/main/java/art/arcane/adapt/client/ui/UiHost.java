package art.arcane.adapt.client.ui;

/** Opens and closes Adapt screens and reaches the client services screens need. */
public interface UiHost {
    void open(UiScreen screen);

    /** Closes the open Adapt screen, if any. */
    void close();

    /** The open Adapt screen, or null. */
    UiScreen current();

    /** Plays the button click through the client's sound settings. */
    void playClick();

    String clipboard();

    void setClipboard(String text);

    /** The text for a translation key in the client's language, or the key when there is none. */
    String translate(String key);
}
