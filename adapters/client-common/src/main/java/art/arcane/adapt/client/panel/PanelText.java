package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.IllegalFormatException;
import java.util.Locale;

/** The native panel's own words, as language keys under {@code adapt.panel.} with English fallbacks. */
enum PanelText {
    BACK("back", "Back"),
    SEARCH("search", "Search"),
    PAGE("page", "Page %s of %s"),
    PAGE_SHORT("page_short", "%s / %s"),
    FIRST("first", "First page"),
    PREVIOUS("previous", "Previous page"),
    NEXT("next", "Next page"),
    LAST("last", "Last page"),
    EMPTY("empty", "Nothing here"),
    NO_MATCH("no_match", "Nothing matches \"%s\""),
    MORE("more", "More actions"),
    OPTIONS("options", "Choose a value"),
    INVALID_NUMBER("invalid_number", "Enter a number"),
    RANGE("range", "From %s to %s"),
    CLICK_LEFT("click.left", "Click"),
    CLICK_RIGHT("click.right", "Right click"),
    CLICK_SHIFT_LEFT("click.shift_left", "Shift + click"),
    CLICK_SHIFT_RIGHT("click.shift_right", "Shift + right click"),
    CLICK_MIDDLE("click.middle", "Middle click"),
    CLICK_NUMBER_KEY("click.number_key", "Hotbar key"),
    CLICK_DROP("click.drop", "Drop key"),
    CLICK_CONTROL_DROP("click.control_drop", "Ctrl + drop key"),
    CLICK_DOUBLE("click.double", "Double click"),
    CLICK_SWAP_OFFHAND("click.swap_offhand", "Offhand key"),
    CLICK_OTHER("click.other", "Other click");

    static final String PREFIX = "adapt.panel.";

    private final String key;
    private final String english;

    PanelText(String key, String english) {
        this.key = PREFIX + key;
        this.english = english;
    }

    String key() {
        return key;
    }

    String english() {
        return english;
    }

    /** The text in {@code host}'s language (English without a host) with {@code args} filled into its slots. */
    String text(UiHost host, Object... args) {
        String translated = host == null ? key : host.translate(key);
        String pattern = translated == null || translated.equals(key) ? english : translated;
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException broken) {
            return String.format(Locale.ROOT, english, args);
        }
    }

    /** The input that sends {@code kind} on a chest slot, as players know it. */
    static PanelText click(ClickKind kind) {
        return switch (kind) {
            case LEFT -> CLICK_LEFT;
            case RIGHT -> CLICK_RIGHT;
            case SHIFT_LEFT -> CLICK_SHIFT_LEFT;
            case SHIFT_RIGHT -> CLICK_SHIFT_RIGHT;
            case MIDDLE -> CLICK_MIDDLE;
            case NUMBER_KEY -> CLICK_NUMBER_KEY;
            case DROP -> CLICK_DROP;
            case CONTROL_DROP -> CLICK_CONTROL_DROP;
            case DOUBLE_CLICK -> CLICK_DOUBLE;
            case SWAP_OFFHAND -> CLICK_SWAP_OFFHAND;
            case OTHER -> CLICK_OTHER;
        };
    }
}
