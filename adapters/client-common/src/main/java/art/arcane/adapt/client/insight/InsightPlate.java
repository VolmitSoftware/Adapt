package art.arcane.adapt.client.insight;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The target readout in plate units: a name and health line over a slim health bar, then attribute chips, effect icons
 * and ownership, each row only when it has something to say. Collapsed it is the bar alone.
 */
public final class InsightPlate {
    /** The tag of a creature Taming's Stable Hand biased, drawn as a saddle. */
    static final String STABLE_HAND = "adapt:stable_hand";
    public static final int PAD = 3;
    public static final int BAR_HEIGHT = 3;
    public static final int COLLAPSED_WIDTH = 28;
    public static final int MIN_WIDTH = 56;
    public static final int ROW_GAP = 2;
    public static final int GLYPH = 8;
    public static final int EFFECT = 9;
    public static final int CHIP_GAP = 5;

    private final String name;
    private final String healthText;
    private final String absorptionText;
    private final List<Chip> chips;
    private final List<ServerFrame.InsightTarget.Effect> effects;
    private final String owner;
    private final int ownerColor;
    private final int healthWidth;
    private final int lineHeight;
    private final int width;
    private final int height;

    private InsightPlate(String name, String healthText, String absorptionText, List<Chip> chips,
                         List<ServerFrame.InsightTarget.Effect> effects, String owner, int ownerColor, int healthWidth,
                         int lineHeight, int width, int height) {
        this.name = name;
        this.healthText = healthText;
        this.absorptionText = absorptionText;
        this.chips = chips;
        this.effects = effects;
        this.owner = owner;
        this.ownerColor = ownerColor;
        this.healthWidth = healthWidth;
        this.lineHeight = lineHeight;
        this.width = width;
        this.height = height;
    }

    /** Measures the expanded plate; {@code textWidth} is the font's width in plate units. */
    public static InsightPlate measure(ServerFrame.InsightTarget view, double health, double absorption, InsightSettings settings,
                                      UiHost host, TextWidth textWidth, int lineHeight) {
        String name = displayName(view, host);
        String healthText = number(health) + "/" + number(view.maxHealth());
        String absorptionText = absorption > 0D ? " +" + number(absorption) : "";
        List<Chip> chips = new ArrayList<>();
        if (settings.showStats()) {
            if (view.armor() > 0D) {
                chips.add(new Chip("minecraft:iron_chestplate", number(view.armor())));
            }
            for (ServerFrame.InsightTarget.Stat stat : view.stats()) {
                if (stat.value() != 0D) {
                    chips.add(new Chip(glyph(stat), stat(stat)));
                }
            }
            if (view.tags().contains(STABLE_HAND)) {
                chips.add(new Chip("minecraft:saddle", ""));
            }
        }
        List<ServerFrame.InsightTarget.Effect> effects = settings.showEffects() ? view.effects() : List.of();
        String owner = settings.showOwner() ? owner(view, host) : "";
        int ownerColor = view.bond() == InsightBond.YOURS ? InsightColors.YOURS : InsightColors.DIM;
        int nameWidth = textWidth.width(name);
        int healthWidth = textWidth.width(healthText + absorptionText);
        int content = Math.max(MIN_WIDTH, nameWidth + 6 + healthWidth);
        int chipsWidth = 0;
        for (Chip chip : chips) {
            chipsWidth += chipWidth(chip, textWidth) + CHIP_GAP;
        }
        content = Math.max(content, chipsWidth - CHIP_GAP);
        content = Math.max(content, effects.size() * (EFFECT + 1) - 1);
        content = Math.max(content, owner.isEmpty() ? 0 : textWidth.width(owner));
        int height = PAD + lineHeight + ROW_GAP + BAR_HEIGHT;
        if (!chips.isEmpty()) {
            height += ROW_GAP + 1 + lineHeight;
        }
        if (!effects.isEmpty()) {
            height += ROW_GAP + 1 + EFFECT;
        }
        if (!owner.isEmpty()) {
            height += ROW_GAP + 1 + lineHeight;
        }
        height += PAD;
        return new InsightPlate(name, healthText, absorptionText, List.copyOf(chips), effects, owner, ownerColor,
                healthWidth, lineHeight, content + PAD * 2, height);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Top of the health bar in the expanded plate. */
    public int barTop() {
        return PAD + lineHeight + ROW_GAP;
    }

    public int barWidth() {
        return width - PAD * 2;
    }

    public String name() {
        return name;
    }

    public List<Chip> chips() {
        return chips;
    }

    /** Draws the rows below and beside the bar at {@code alpha} (0..1); items and icons only once nearly opaque. */
    public void drawDetails(UiDraw draw, InsightState state, double alpha) {
        int text = InsightColors.alpha(InsightColors.TEXT, alpha);
        if (text == 0) {
            return;
        }
        draw.text(name, PAD, PAD, text, true);
        int healthX = width - PAD - healthWidth;
        draw.text(healthText, healthX, PAD, text, true);
        if (!absorptionText.isEmpty()) {
            draw.text(absorptionText, healthX + draw.textWidth(healthText), PAD,
                    InsightColors.alpha(InsightColors.ABSORPTION, alpha), true);
        }
        boolean solid = alpha >= 0.7D;
        int y = barTop() + BAR_HEIGHT + ROW_GAP + 1;
        if (!chips.isEmpty()) {
            int x = PAD;
            for (Chip chip : chips) {
                if (solid) {
                    draw.pushPose();
                    draw.translate(x, y);
                    draw.scale(0.5F, 0.5F);
                    draw.item(chip.itemId(), null, 0, 0);
                    draw.popPose();
                }
                if (!chip.value().isEmpty()) {
                    draw.text(chip.value(), x + GLYPH + 1, y, InsightColors.alpha(InsightColors.DIM, alpha), true);
                }
                x += GLYPH + 1 + (chip.value().isEmpty() ? 0 : draw.textWidth(chip.value())) + CHIP_GAP;
            }
            y += lineHeight + ROW_GAP + 1;
        }
        if (!effects.isEmpty()) {
            int x = PAD;
            for (ServerFrame.InsightTarget.Effect effect : effects) {
                drawEffect(draw, state, effect, x, y, alpha, solid);
                x += EFFECT + 1;
            }
            y += EFFECT + ROW_GAP + 1;
        }
        if (!owner.isEmpty()) {
            draw.text(owner, PAD, y, InsightColors.alpha(ownerColor, alpha), true);
        }
    }

    private static void drawEffect(UiDraw draw, InsightState state, ServerFrame.InsightTarget.Effect effect, int x, int y, double alpha,
                                   boolean solid) {
        String id = effectTexture(effect.effectId());
        UiTexture texture = id == null ? null : draw.sprites().texture(id);
        int ticks = state.remainingTicks(effect.durationTicks());
        boolean ending = ticks >= 0 && ticks < 200 && (ticks / 5) % 2 == 0;
        if (texture != null && solid && !ending) {
            draw.blit(texture, x, y, EFFECT, EFFECT, 0, 0, texture.width(), texture.height());
        } else if (texture == null) {
            draw.fill(x + 2, y + 2, x + EFFECT - 2, y + EFFECT - 2,
                    InsightColors.alpha(effect.harmful() ? InsightColors.HARMFUL : InsightColors.BENEFICIAL, alpha));
        }
        if (effect.harmful()) {
            draw.fill(x, y + EFFECT, x + EFFECT, y + EFFECT + 1, InsightColors.alpha(InsightColors.HARMFUL, alpha));
        }
    }

    static String effectTexture(String effectId) {
        int colon = effectId.indexOf(':');
        if (colon <= 0 || colon == effectId.length() - 1) {
            return null;
        }
        return effectId.substring(0, colon) + ":textures/mob_effect/" + effectId.substring(colon + 1) + ".png";
    }

    static String displayName(ServerFrame.InsightTarget view, UiHost host) {
        if (!view.name().isEmpty()) {
            return view.name();
        }
        int colon = view.typeId().indexOf(':');
        String namespace = colon < 0 ? "minecraft" : view.typeId().substring(0, colon);
        String path = colon < 0 ? view.typeId() : view.typeId().substring(colon + 1);
        String key = "entity." + namespace + "." + path.replace('/', '.');
        String translated = host.translate(key);
        if (translated != null && !translated.equals(key)) {
            return translated;
        }
        StringBuilder words = new StringBuilder(path.length());
        for (String word : path.split("[_/]")) {
            if (!word.isEmpty()) {
                if (!words.isEmpty()) {
                    words.append(' ');
                }
                words.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return words.toString();
    }

    static String owner(ServerFrame.InsightTarget view, UiHost host) {
        return switch (view.bond()) {
            case NONE -> "";
            case WILD -> host.translate("adapt.hud.insight.wild");
            case YOURS -> host.translate("adapt.hud.insight.yours");
            case OWNED -> view.ownerName().isEmpty() ? host.translate("adapt.hud.insight.tamed")
                    : host.translate("adapt.hud.insight.tamed_by").replace("%s", view.ownerName());
        };
    }

    static int accent(InsightKind kind) {
        return switch (kind) {
            case PLAYER -> InsightColors.PLAYER;
            case HOSTILE -> InsightColors.HOSTILE;
            case PASSIVE -> InsightColors.PASSIVE;
        };
    }

    static String number(double value) {
        double rounded = Math.round(value * 10D) / 10D;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String stat(ServerFrame.InsightTarget.Stat stat) {
        return switch (stat.attribute()) {
            case "minecraft:movement_speed", "minecraft:jump_strength", "minecraft:knockback_resistance" ->
                    String.format(Locale.ROOT, "%.2f", stat.value());
            default -> number(stat.value());
        };
    }

    private static String glyph(ServerFrame.InsightTarget.Stat stat) {
        return switch (stat.attribute()) {
            case "minecraft:attack_damage" -> "minecraft:iron_sword";
            case "minecraft:movement_speed" -> "minecraft:sugar";
            case "minecraft:jump_strength" -> "minecraft:rabbit_foot";
            case "minecraft:armor_toughness" -> "minecraft:diamond_chestplate";
            case "minecraft:knockback_resistance" -> "minecraft:anvil";
            case "minecraft:follow_range" -> "minecraft:ender_eye";
            default -> "minecraft:paper";
        };
    }

    private static int chipWidth(Chip chip, TextWidth textWidth) {
        return GLYPH + 1 + (chip.value().isEmpty() ? 0 : textWidth.width(chip.value()));
    }

    /** One attribute: an item glyph and its value. */
    public record Chip(String itemId, String value) {
    }

    @FunctionalInterface
    public interface TextWidth {
        int width(String text);
    }
}
