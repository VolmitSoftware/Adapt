package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.ui.PngLuma;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceGroupView;
import art.arcane.adapt.client.view.SkillView;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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
    private static final Map<String, List<IconSprites.Layer>> LAYERS = new HashMap<>();

    private UiIcons() {
    }

    public static IconRef skill(SkillView skill) {
        if (skill == null) {
            return null;
        }
        IconRef known = MEMO.get(skill);
        return known != null ? known
                : remember(skill, IconSprites.withSprite(skill.icon(), IconSprites.skill(owner(skill.id()))));
    }

    public static IconRef adaptation(AdaptationView adaptation) {
        if (adaptation == null) {
            return null;
        }
        IconRef known = MEMO.get(adaptation);
        return known != null ? known
                : remember(adaptation, IconSprites.withSprite(adaptation.icon(),
                        IconSprites.adaptation(owner(adaptation.id()))));
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

    /** A preference choice with the shared {@link IconSprites#commonChoice} art; see {@link #choiceSprite}. */
    public static IconRef choice(PreferenceChoiceView choice) {
        IconRef known = MEMO.get(choice);
        return known != null ? known : remember(choice, IconSprites.withSprite(choice.icon(), IconSprites.commonChoice(choice.value())));
    }

    /** The owner's own art for a preference choice, unchecked, drawn ahead of {@link #choice}. */
    public static UiTexture choiceSprite(String ownerId, String preferenceId, PreferenceChoiceView choice) {
        return new UiTexture(IconSprites.preferenceChoice(owner(ownerId), preferenceId, choice.value()), SIZE, SIZE);
    }

    public static IconRef groupMember(String ownerId, PreferenceGroupView.Entry entry) {
        IconRef known = MEMO.get(entry);
        return known != null ? known
                : remember(entry, IconSprites.withSprite(entry.icon(),
                        IconSprites.preferenceMember(owner(ownerId), entry.preferenceId())));
    }

    public static IconRef groupHeading(String ownerId, PreferenceGroupView group) {
        IconRef known = MEMO.get(group);
        return known != null ? known
                : remember(group, IconSprites.withSprite(group.icon(),
                        IconSprites.preferenceGroup(owner(ownerId), group.id())));
    }

    /** The content a view names by id or line key; a key that is neither is kept as a path in {@code adapt}. */
    private static Id owner(String key) {
        Id id = IconSprites.contentId(key);
        return id != null ? id : Id.adapt(IconSprites.pathSegment(key));
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

    /**
     * The bottom texture of the sprite {@code icon} names when resource packs provide every layer of it, else null; see
     * {@link IconSprites#layers}.
     */
    public static UiTexture resolve(UiSprites sprites, IconRef icon) {
        if (icon == null || icon.spriteId() == null) {
            return null;
        }
        List<IconSprites.Layer> layers = layers(icon.spriteId());
        UiTexture bottom = sprites.texture(layers.get(0).spriteId());
        for (int index = 1; index < layers.size() && bottom != null; index++) {
            if (sprites.texture(layers.get(index).spriteId()) == null) {
                return null;
            }
        }
        return bottom;
    }

    private static List<IconSprites.Layer> layers(String spriteId) {
        List<IconSprites.Layer> known = LAYERS.get(spriteId);
        if (known == null) {
            known = IconSprites.layers(spriteId);
            if (LAYERS.size() >= MEMO_LIMIT) {
                LAYERS.clear();
            }
            LAYERS.put(spriteId, known);
        }
        return known;
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
            return sprites.luminance(layers(icon.spriteId()).get(0).spriteId());
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

    /**
     * Draws the mark {@code spriteId}, such as {@link IconSprites#mark}, at (x, y) and {@code size} units square; false
     * when no resource pack provides it, so the caller draws its own fallback.
     */
    public static boolean mark(UiDraw draw, String spriteId, int x, int y, int size) {
        UiTexture texture = spriteId == null ? null : draw.sprites().texture(spriteId);
        if (texture == null) {
            return false;
        }
        draw.blit(texture, x, y, size, size);
        return true;
    }

    /** Draws {@code icon} with its top left at (x, y), {@code size} units square; sizes other than 16 scale the item. */
    public static void paint(UiDraw draw, IconRef icon, int x, int y, int size, boolean glint) {
        if (icon == null || size <= 0) {
            return;
        }
        if (resolve(draw.sprites(), icon) != null) {
            for (IconSprites.Layer layer : layers(icon.spriteId())) {
                UiTexture texture = draw.sprites().texture(layer.spriteId());
                if (layer.tint() == IconSprites.NO_TINT) {
                    draw.blit(texture, x, y, size, size);
                } else {
                    draw.blit(texture, x, y, size, size, 0, 0, texture.width(), texture.height(), 0xFF000000 | layer.tint());
                }
            }
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
