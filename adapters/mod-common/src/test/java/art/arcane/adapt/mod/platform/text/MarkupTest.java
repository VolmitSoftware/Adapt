package art.arcane.adapt.mod.platform.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkupTest {
    @Test
    void plainAndMiniMessagePassThrough() {
        assertEquals("hello <gradient:red:blue>world</gradient>", Markup.toMiniMessage("hello <gradient:red:blue>world</gradient>"));
    }

    @Test
    void sectionColoursResetAndDecorationsDoNot() {
        assertEquals("<reset><green>a<bold>b", Markup.toMiniMessage("§a" + "a" + "§l" + "b"));
    }

    @Test
    void ampersandCodesTranslate() {
        assertEquals("<reset><red>x<reset>", Markup.toMiniMessage("&cx&r"));
    }

    @Test
    void escapedAmpersandStaysLiteral() {
        assertEquals("R&D", Markup.toMiniMessage("R\\&D"));
    }

    @Test
    void ampersandWithoutCodeStaysLiteral() {
        assertEquals("Q&Z & more", Markup.toMiniMessage("Q&Z & more"));
    }

    @Test
    void hexFormsBecomeHexTags() {
        assertEquals("<reset><#12ab34>a", Markup.toMiniMessage("&#12AB34a"));
        assertEquals("<reset><#12ab34>a", Markup.toMiniMessage("&x&1&2&a&b&3&4a"));
        assertEquals("<reset><#12ab34>a", Markup.toMiniMessage("[12ab34]a"));
        assertEquals("<reset><#12ab34>a", Markup.toMiniMessage("§x§1§2§a§b§3§4a"));
    }

    @Test
    void bracketWithoutHexStaysLiteral() {
        assertEquals("[option] [12ab34]", Markup.toMiniMessage("[option] \\[12ab34]"));
    }

    @Test
    void unknownSectionCodeIsKept() {
        assertEquals("§zq", Markup.toMiniMessage("§zq"));
    }

    @Test
    void nullAndEmptyBecomeEmpty() {
        assertEquals("", Markup.toMiniMessage(null));
        assertEquals("", Markup.toMiniMessage(""));
    }
}
