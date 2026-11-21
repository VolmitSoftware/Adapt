package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiNineSlice;

/**
 * Optional textured chrome. A resource pack that provides {@code adapt:textures/gui/kit/<name>.png} with its slice
 * file {@code <name>.json} (see {@link art.arcane.adapt.client.ui.UiAssetCheck}) replaces the flat drawing of that
 * widget. Names: {@code panel}; {@code button}, {@code button_primary}, {@code button_destructive}, each with optional
 * {@code _hover}, {@code _pressed} and {@code _disabled} variants; {@code tile} with {@code tile_hover} and
 * {@code tile_selected}; the tome's {@code spine}, {@code ribbon}, {@code bookmark}, {@code tooltip}, {@code field}, {@code chip} and the
 * {@code token} medallion rims ({@code token_learned}, {@code token_maxed}, {@code token_locked}, drawn at their own
 * size). A missing variant uses its base name; a missing base keeps the flat look. The client ships all of them.
 */
public final class UiChrome {
    public static final String PANEL = "panel";
    public static final String BUTTON = "button";
    public static final String TILE = "tile";
    private static final String ROOT = "adapt:textures/gui/kit/";

    private UiChrome() {
    }

    public static String textureId(String name) {
        return ROOT + name + ".png";
    }

    /** Draws {@code name} (or its {@code state} variant when there is one) over the box; false when no pack provides it. */
    public static boolean paint(UiDraw draw, String name, String state, UiRect box) {
        UiNineSlice slice = state == null ? null : draw.sprites().nineSlice(textureId(name + '_' + state));
        if (slice == null) {
            slice = draw.sprites().nineSlice(textureId(name));
        }
        if (slice == null) {
            return false;
        }
        slice.paint(draw, box.x(), box.y(), box.width(), box.height());
        return true;
    }
}
