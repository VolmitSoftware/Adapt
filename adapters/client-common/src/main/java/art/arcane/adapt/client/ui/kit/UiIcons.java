package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.ui.PngLuma;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.client.view.SkillView;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Draws an {@link IconRef}: the texture its {@code spriteId} names when a resource pack provides it, otherwise the
 * item with its model and custom model data. A sprite id is a texture file id such as
 * {@code adapt:textures/gui/skill/agility.png}; {@link #skill}, {@link #adaptation}, {@link #toast} and {@link #status}
 * fill in the {@link IconSprites} convention path unless the server already named a sprite, which then replaces it.
 */
public final class UiIcons {
    public static final int SIZE = 16;
    private static final int MEMO_LIMIT = 1024;
    private static final Map<Object, IconRef> MEMO = new IdentityHashMap<>();
    private static final Map<String, String[]> ITEM_TEXTURES = new HashMap<>();

    private UiIcons() {
    }

    public static IconRef skill(SkillView skill) {
        if (skill == null) {
            return null;
        }
        IconRef known = MEMO.get(skill);
        return known != null ? known : remember(skill, IconSprites.withSprite(skill.icon(), IconSprites.skill(skill.id())));
    }

    public static IconRef adaptation(AdaptationView adaptation) {
        if (adaptation == null) {
            return null;
        }
        IconRef known = MEMO.get(adaptation);
        return known != null ? known
                : remember(adaptation, IconSprites.withSprite(adaptation.icon(), IconSprites.adaptation(adaptation.id())));
    }

    public static IconRef toast(ToastKind kind, IconRef icon) {
        if (icon == null || icon.spriteId() != null) {
            return icon;
        }
        IconRef known = MEMO.get(icon);
        return known != null ? known : remember(icon, IconSprites.withSprite(icon, IconSprites.toast(kind.name().toLowerCase(Locale.ROOT))));
    }

    /** A HUD status icon; {@code purpose} is the status purpose such as {@code adapt:status:rift_gate}. */
    public static IconRef status(String purpose, IconRef icon) {
        if (icon == null || icon.spriteId() != null) {
            return icon;
        }
        IconRef known = MEMO.get(icon);
        return known != null ? known
                : remember(icon, IconSprites.withSprite(icon, IconSprites.hud(purpose.substring(purpose.lastIndexOf(':') + 1))));
    }

    /** Views and frames are immutable, so their derived icon is kept by identity; render thread only. */
    private static IconRef remember(Object source, IconRef icon) {
        if (MEMO.size() >= MEMO_LIMIT) {
            MEMO.clear();
        }
        MEMO.put(source, icon);
        return icon;
    }

    /** The sprite texture {@code icon} names, unchecked, or null when it is drawn as an item. */
    public static UiTexture sprite(IconRef icon) {
        return icon == null || icon.spriteId() == null ? null : new UiTexture(icon.spriteId(), SIZE, SIZE);
    }

    /** The sprite texture {@code icon} names when a resource pack provides a usable file, else null. */
    public static UiTexture resolve(UiSprites sprites, IconRef icon) {
        return icon == null || icon.spriteId() == null ? null : sprites.texture(icon.spriteId());
    }

    /**
     * The mean luminance of the art {@code icon} draws (its sprite, else the item's flat item or block texture), or
     * {@link PngLuma#UNKNOWN} when that cannot be told, such as for items with custom models.
     */
    public static float luminance(UiSprites sprites, IconRef icon) {
        if (icon == null) {
            return PngLuma.UNKNOWN;
        }
        if (resolve(sprites, icon) != null) {
            return sprites.luminance(icon.spriteId());
        }
        if (icon.customModelData() != 0 || icon.itemModelId() != null) {
            return PngLuma.UNKNOWN;
        }
        for (String texture : itemTextures(icon.itemId())) {
            float luminance = sprites.luminance(texture);
            if (luminance >= 0.0F) {
                return luminance;
            }
        }
        return PngLuma.UNKNOWN;
    }

    private static String[] itemTextures(String itemId) {
        String[] known = ITEM_TEXTURES.get(itemId);
        if (known == null) {
            int colon = itemId.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : itemId.substring(0, colon);
            String path = itemId.substring(colon + 1);
            known = new String[] {namespace + ":textures/item/" + path + ".png", namespace + ":textures/block/" + path + ".png"};
            if (ITEM_TEXTURES.size() >= MEMO_LIMIT) {
                ITEM_TEXTURES.clear();
            }
            ITEM_TEXTURES.put(itemId, known);
        }
        return known;
    }

    /**
     * Draws {@code icon} like {@link #paint}, on a soft plate one unit larger than the icon when its art is too pale to
     * read on {@code backdrop} (the opaque colour under it); see {@link UiContrast#iconPlate}.
     */
    public static void paintOn(UiDraw draw, IconRef icon, int x, int y, int size, boolean glint, int backdrop) {
        int plate = UiContrast.iconPlate(luminance(draw.sprites(), icon), backdrop);
        if (plate != 0) {
            UiTome.soft(draw, x - 1, y - 1, x + size + 1, y + size + 1, plate);
        }
        paint(draw, icon, x, y, size, glint);
    }

    /** Draws {@code icon} with its top left at (x, y), {@code size} units square; sizes other than 16 scale the item. */
    public static void paint(UiDraw draw, IconRef icon, int x, int y, int size, boolean glint) {
        if (icon == null || size <= 0) {
            return;
        }
        UiTexture sprite = resolve(draw.sprites(), icon);
        if (sprite != null) {
            draw.blit(sprite, x, y, size, size);
            return;
        }
        if (size == SIZE) {
            draw.item(icon.itemId(), icon.itemModelId(), icon.customModelData(), glint, x, y);
            return;
        }
        float factor = size / (float) SIZE;
        draw.pushPose();
        draw.translate(x, y);
        draw.scale(factor, factor);
        draw.item(icon.itemId(), icon.itemModelId(), icon.customModelData(), glint, 0, 0);
        draw.popPose();
    }
}
