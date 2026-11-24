package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiHost;

import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * The screens' own words, as language keys under {@code adapt.screen.} with English fallbacks. Catalog text (skill and
 * adaptation names, descriptions, stat lines) already arrives in the player's server language.
 */
public enum ScreenText {
    BACK("back", "Back"),
    SKILLS("skills", "Skills"),
    SEARCH("search", "Search"),
    PREVIOUS_PAGE("previous_page", "Previous page"),
    NEXT_PAGE("next_page", "Next page"),
    ADAPTATIONS("adaptations", "Adaptations"),
    SETTINGS("settings", "Settings"),
    REQUIREMENTS("requirements", "Requires"),
    POWER_TITLE("power_title", "Power"),
    POWER_TIP("power_tip", "Each learned adaptation uses 1 power. %s of %s is free."),
    POWER_USED("power_used", "%s power used"),
    KNOWLEDGE("knowledge", "knowledge"),
    KNOWLEDGE_TITLE("knowledge_title", "Knowledge"),
    KNOWLEDGE_TOTAL_TIP("knowledge_total_tip", "Unspent knowledge across all skills. Each skill spends its own."),
    KNOWLEDGE_SKILL_TIP("knowledge_skill_tip", "Unspent knowledge in this skill, earned by levelling it."),
    LEVEL("level", "Level %s"),
    LEVEL_SHORT("level_short", "Lv %s"),
    LEVEL_OF("level_of", "Level %s of %s"),
    XP_TO_LEVEL("xp_to_level", "%s XP to level %s"),
    NO_SKILLS("no_skills", "No skills yet"),
    NO_SKILLS_HINT("no_skills_hint", "Earn experience in any activity to discover skills."),
    NO_MATCH("no_match", "No skills match \"%s\""),
    WAITING("waiting", "Waiting for the server"),
    UNAVAILABLE("unavailable", "This page is no longer available"),
    TO_LEARN("to_learn", "%s to learn"),
    NO_ACCESS("no_access", "You do not have access to this skill"),
    NO_ACCESS_HINT("no_access_hint", "The server has not given you permission to use it."),
    DENIED("denied", "Not available to you"),
    OPEN_HINT("open_hint", "Click to open"),
    NO_ADAPTATIONS("no_adaptations", "No adaptations available"),
    RESET("reset", "Reset"),
    RESET_TIP("reset_tip", "Puts every preference here back to the server default."),
    SERVER_CONTROLLED("server_controlled", "Set by the server"),
    LEARN_FIRST("learn_first", "Learn this adaptation to change its preferences."),
    CURRENT("current", "Current: %s"),
    CHOICE_LEVEL("choice_level", "Needs adaptation level %s"),
    GROUP_ON("group_on", "%s of %s on"),
    MASTERED("mastered", "Mastered"),
    NOT_LEARNED("not_learned", "Not learned"),
    LEARN_FOR("learn_for", "Learn %s for %s knowledge"),
    LEARN_FREE("learn_free", "Learn %s for free"),
    FREE_COST("free_cost", "Free"),
    DEBUG("debug", "Debug mode"),
    DEBUG_TIP("debug_tip", "Learning costs nothing and unlearning refunds nothing while debug mode is on."),
    NEEDS_KNOWLEDGE("needs_knowledge", "Needs %s more knowledge"),
    NO_KNOWLEDGE("no_knowledge", "No knowledge in this skill yet"),
    NEEDS_POWER("needs_power", "Needs %s power, %s free"),
    REQUIRES("requires", "Requires %s %s"),
    DISABLED("disabled", "Disabled on this server"),
    PERMANENT("permanent", "Permanent"),
    PERMANENT_TIP("permanent_tip", "Once learned, this adaptation cannot be unlearned."),
    LEARN("learn", "Learn %s"),
    LEVEL_UP("level_up", "Level up %s"),
    LEARN_TO("learn_to", "Learn to %s"),
    UNLEARN("unlearn", "Unlearn %s"),
    UNLEARN_TO("unlearn_to", "Unlearn to %s"),
    UNLEARN_REFUND("unlearn_refund", "Unlearn to %s, refunds %s knowledge"),
    UNLEARN_NO_REFUND("unlearn_no_refund", "Unlearn to %s, returns no knowledge"),
    KNOWLEDGE_COST("knowledge_cost", "Costs %s knowledge"),
    POWER_COST("power_cost", "Uses %s power"),
    POWER_FREED("power_freed", "Frees %s power"),
    REFUND("refund", "Refunds %s knowledge"),
    NO_REFUND("no_refund", "Unlearning returns no knowledge"),
    LEARNED("learned", "Learned"),
    NEEDED_BY("needed_by", "Needed by %s"),
    NO_UNLEARN("no_unlearn", "Cannot be unlearned"),
    CANCEL("cancel", "Cancel"),
    LEARNING("learning", "Learning"),
    UNLEARNING("unlearning", "Unlearning"),
    SAVING("saving", "Saving"),
    NO_ANSWER("no_answer", "The server did not answer"),
    NOT_CONNECTED("not_connected", "Not connected to an Adapt server");

    public static final String PREFIX = "adapt.screen.";

    private static volatile UiHost host;

    private final String key;
    private final String english;

    ScreenText(String key, String english) {
        this.key = PREFIX + key;
        this.english = english;
    }

    /** Resolves text through {@code client}'s language from now on; until then the English text is used. */
    public static void install(UiHost client) {
        host = client;
    }

    public String key() {
        return key;
    }

    public String english() {
        return english;
    }

    /** The text in the client's language with {@code args} filled into its {@code %s} slots. */
    public String text(Object... args) {
        UiHost current = host;
        String translated = current == null ? key : current.translate(key);
        String pattern = translated == null || translated.equals(key) ? english : translated;
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException broken) {
            return String.format(Locale.ROOT, english, args);
        }
    }

    /** {@code value} with thousands separators. */
    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** {@code value} as a Roman numeral, as the chest menus name levels; plain digits past 3999. */
    public static String roman(int value) {
        if (value <= 0 || value >= 4000) {
            return Integer.toString(value);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int rest = value;
        for (int index = 0; index < values.length; index++) {
            while (rest >= values[index]) {
                out.append(numerals[index]);
                rest -= values[index];
            }
        }
        return out.toString();
    }
}
