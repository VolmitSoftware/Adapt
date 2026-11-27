package art.arcane.adapt.pack.bake;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything one built pack holds, for the server that draws with it: the space font's codepoints and advances, the text
 * row fonts, each kit layout's glyph font with where every glyph lands and the host items that animate it, the host slots
 * of every dressing mode, the icon item models (each with a greyed {@code #dim} copy), the fixed art's item models,
 * glyph fonts and tooltip styles, the paint glyphs the screens place by key (see core {@code PackArt}), each kit's
 * tooltip style and toast icon, the action bar font, the condensed font's advances and row fonts (null when the pack has
 * none), and what built it. Positions are container pixels; the title text starts at
 * {@link SlotGrid#TITLE_X}, {@link SlotGrid#TITLE_Y}.
 */
public record PackManifest(int schema, String spaceFont, Map<Integer, Integer> spaces, List<TextFont> textFonts, List<PageFont> pages,
                           Map<String, List<Integer>> hostSlots, List<Icon> icons, List<Art> art, List<Paint> paint, List<KitStyle> kits,
                           List<String> tooltipStyles, Hud hud, String contentHash, Condensed condensed, Build build) {
    public static final int SCHEMA = 2;
    public static final String PATH = "adapt_manifest.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public PackManifest {
        spaces = new TreeMap<>(spaces);
        textFonts = List.copyOf(textFonts);
        pages = List.copyOf(pages);
        TreeMap<String, List<Integer>> modes = new TreeMap<>();
        hostSlots.forEach((String mode, List<Integer> slots) -> modes.put(mode, List.copyOf(slots)));
        hostSlots = modes;
        icons = List.copyOf(icons);
        art = List.copyOf(art);
        paint = List.copyOf(paint);
        kits = List.copyOf(kits);
        tooltipStyles = List.copyOf(tooltipStyles);
    }

    /** A text font placing vanilla text with its top {@code dy} below the title text's top. */
    public record TextFont(String font, int dy) {
    }

    /**
     * One kit layout: its dressing mode ({@code A}, {@code Afallback} or {@code B}; null for fixed art), its font, where
     * the still layer's box starts, where the page's own top left sits, its glyphs, its host items (one per host slot it
     * uses, each in a slot of {@link #hostSlots} for its mode), and the moving regions the glyphs show still instead.
     */
    public record PageFont(String kit, String layout, String mode, String font, int left, int top, int pageX, int pageY, List<Glyph> glyphs,
                           List<Item> items, List<Unplaced> unplaced) {
        public PageFont {
            glyphs = List.copyOf(glyphs);
            items = List.copyOf(items);
            unplaced = List.copyOf(unplaced);
        }
    }

    /**
     * A moving region shown still: its box in container pixels and why. {@code reach} means no host of the mode reaches it
     * within the reach the bake assumes ({@code reachBefore} left and up, {@code reachAfter} right and down of the slot's
     * corner), so a wider measured reach can animate it by a re-bake with no other change; {@code screen} means the page
     * is for screens without host items.
     */
    public record Unplaced(int x, int y, int width, int height, String reason, int reachBefore, int reachAfter) {
    }

    /** One still glyph: its codepoint and texture, its box in container pixels, its ascent and advance. */
    public record Glyph(int codepoint, String texture, int x, int y, int width, int height, int ascent, int advance) {
    }

    /**
     * One host item: the slot it must be placed in, its item model (an item definition id), its regions, and the slots
     * whose 16x16 squares its art covers (an item in a later slot draws over it). It draws nothing on its own square apart
     * from the dressing and wants a hidden tooltip.
     */
    public record Item(int slot, String model, List<Region> regions, List<Integer> covers) {
        public Item {
            regions = List.copyOf(regions);
            covers = List.copyOf(covers);
        }
    }

    /**
     * One animated region: its frame strip, its box in container pixels, distinct frames, loop ticks, how it closes, and
     * the GUI transform drawing it from its host slot.
     */
    public record Region(String texture, int x, int y, int width, int height, int frames, int ticks, String closure, double scale, double shiftX,
                         double shiftY) {
    }

    /** An icon sprite ({@code adapt:gui/skill/agility}), its item model id and its texture path in the pack. */
    public record Icon(String sprite, String model, String texture) {
    }

    /** A fixed art piece: its name, its design role, its item model, and its box relative to its slot's corner. */
    public record Art(String name, String role, String model, int x, int y, int width, int height) {
    }

    /**
     * A paint glyph: its key, its font and codepoint, its left edge relative to where the screen draws it, its box's top
     * in container pixels and its size, and its advance.
     */
    public record Paint(String key, String font, int codepoint, int x, int y, int width, int height, int advance) {
    }

    /**
     * What a kit adds besides pages: its tooltip style, and its toast icon model with the skill icon
     * sprite it frames (both null without an icon).
     */
    public record KitStyle(String kit, String tooltipStyle, String toastSprite, String toastModel) {
    }

    /** The action bar font and its icon glyphs' codepoints by sprite id ({@code adapt:gui/skill/agility}). */
    public record Hud(String font, Map<String, Integer> icons) {
        public Hud {
            icons = new TreeMap<>(icons);
        }
    }

    /** The condensed font for names too long for the default one: its advance per code point and its font per text row. */
    public record Condensed(Map<Integer, Integer> advances, List<CondensedFont> fonts) {
        public Condensed {
            advances = new TreeMap<>(advances);
            fonts = List.copyOf(fonts);
        }
    }

    /** A condensed row font placing text with its top {@code dy} below the title text's top. */
    public record CondensedFont(int dy, String font) {
    }

    /**
     * What built the pack: the bake's {@link PackBake#VERSION}, the Adapt version, and the SHA-1 of every bake input (see
     * {@code PackBuilds}), so a server can tell whether a pack zip it is handed is the one it would bake itself.
     */
    public record Build(String bake, String adapt, String inputs) {
    }

    public String toJson() {
        return GSON.toJson(this) + "\n";
    }

    public static PackManifest fromJson(String json) {
        PackManifest manifest = GSON.fromJson(json, PackManifest.class);
        if (manifest == null || manifest.schema() != SCHEMA) {
            throw new IllegalArgumentException("not an Adapt pack manifest");
        }
        return manifest;
    }
}
