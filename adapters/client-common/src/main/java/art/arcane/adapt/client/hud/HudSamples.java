package art.arcane.adapt.client.hud;

import art.arcane.adapt.engine.notification.HudSlot;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Realistic sample content: a frozen snapshot for the HUD editor, and a live feed for the
 * {@code adapt.client.preview=hud} development preview.
 */
public final class HudSamples {
    public static final String PREVIEW_PROPERTY = "adapt.client.preview";
    /** With the preview on, keeps this many glyphs active at once, for checking crowded layouts. */
    public static final String PREVIEW_GLYPHS_PROPERTY = "adapt.client.preview.glyphs";
    private static final String[] GLYPH_ITEMS = {"minecraft:ender_pearl", "minecraft:shield", "minecraft:iron_sword",
            "minecraft:bow", "minecraft:blaze_powder", "minecraft:feather", "minecraft:bone", "minecraft:clock",
            "minecraft:bricks", "minecraft:prismarine_shard", "minecraft:slime_ball", "minecraft:amethyst_shard"};
    private static final String[][] SKILLS = {
            {"agility", "Agility", "minecraft:feather", "9AE65C"},
            {"pickaxe", "Pickaxe", "minecraft:iron_pickaxe", "7FB3FF"},
            {"swords", "Swords", "minecraft:iron_sword", "FF6B6B"},
            {"herbalism", "Herbalism", "minecraft:wheat", "C9E265"},
            {"ranged", "Ranged", "minecraft:bow", "FFB15C"},
            {"architect", "Architect", "minecraft:bricks", "D9A066"},
            {"tragoul", "Tragoul", "minecraft:bone", "C24A4A"},
            {"stealth", "Stealth", "minecraft:leather_boots", "8E7CC3"}
    };
    private static final int[] LEVELS = {14, 22, 9, 31, 6, 12, 18, 7};
    private static final double[] PROGRESS = {0.62, 0.18, 0.85, 0.4, 0.27, 0.5, 0.33, 0.7};
    private static final long[] KNOWLEDGE = {4, 11, 2, 17, 1, 3, 6, 2};
    private static final int PREVIEW_XP_SKILLS = 5;
    private static final long PREVIEW_XP_MILLIS = 1400L;
    private static final long PREVIEW_TOAST_MILLIS = 9000L;
    private static final long PREVIEW_STATUS_MILLIS = 7000L;
    /** A skill discovery's body as long as real skill descriptions run, so the editor shows a wrapped card. */
    static final String LONG_TOAST = "Agility is the ability to move quick and fluidly in the face of obstacles. "
            + "Level up by sprinting, swimming, jumping, and climbing.";
    static final String DISCOVERY_QUIP = "Walking is for people with time";

    private long nextXp;
    private long nextToast;
    private long nextStatus;
    private long nextFlood;
    private int step;
    private final int glyphs = Math.max(0, Integer.getInteger(PREVIEW_GLYPHS_PROPERTY, 0));

    public static boolean previewRequested() {
        return "hud".equals(System.getProperty(PREVIEW_PROPERTY));
    }

    /** A model on a stopped clock, filled so that every widget shows settled sample content. */
    public static HudModel editorModel() {
        StoppedClock clock = new StoppedClock();
        HudModel model = new HudModel(clock);
        fillViews(model);
        String[] amounts = {"agility", "pickaxe", "swords"};
        double[] gains = {12.0, 38.0, 6.5};
        for (int index = amounts.length - 1; index >= 0; index--) {
            int skill = indexOf(amounts[index]);
            model.xp(new ServerFrame.XpEarned(amounts[index], gains[index], 1000.0, LEVELS[skill], PROGRESS[skill]));
        }
        long epoch = clock.epochMillis();
        model.status(glyph("architect-foundation.cooldown", HudGlyph.COOLDOWN, "architect", "minecraft:bricks", "Foundation",
                epoch - 1500L, 5000L, 0, 0, false));
        model.status(glyph("agility-super-jump.cooldown", HudGlyph.COOLDOWN, "agility", "minecraft:rabbit_foot", "Super Jump",
                epoch - 200L, 6000L, 0, 0, false));
        model.status(glyph("tragoul-skeletal-servant.charges", HudGlyph.CHARGES, "tragoul", "minecraft:skeleton_skull",
                "Skeletal Servant", 0L, 0L, 2, 3, false));
        model.status(glyph("stealth-core.toggle", HudGlyph.TOGGLE, "stealth", "minecraft:leather_boots", "Stealth", 0L, 0L,
                0, 0, true));
        model.publish(new ServerFrame.HudPublish("adapt:status:sixth_sense", 30, 5000L, List.of(HudSlot.CENTER),
                "\u00a7bSixth Sense \u00a77- \u00a7fore nearby"));
        HudOptions.Toasts toasts = (HudOptions.Toasts) HudDefaults.options(HudWidgetKind.TOASTS);
        model.toasts().configure(toasts.durationMillis(), HudOptions.Toasts.MAX_VISIBLE, Double.MAX_VALUE, false);
        model.toast(new ServerFrame.Toast(ToastKind.DISCOVERY, IconRef.item("minecraft:feather"), "Agility", DISCOVERY_QUIP,
                "agility"));
        model.toast(new ServerFrame.Toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:feather"), "Agility level 14",
                "Knowledge +1", "agility"));
        model.toast(new ServerFrame.Toast(ToastKind.UNLOCK, IconRef.item("minecraft:iron_pickaxe"), "Vein Miner unlocked",
                "Pickaxes", "pickaxe"));
        model.expire(clock.millis());
        clock.advance(1200L);
        model.expire(clock.millis());
        return model;
    }

    /** Fills a live model with views and starts the sample feed. */
    public void start(HudModel model) {
        fillViews(model);
        long now = model.clock().millis();
        nextXp = now;
        nextToast = now + 1500L;
        nextStatus = now + 500L;
        step = 0;
    }

    /** Emits sample events on the live clock; called every client tick while the preview runs. */
    public void tick(HudModel model) {
        long now = model.clock().millis();
        if (now >= nextXp) {
            nextXp = now + PREVIEW_XP_MILLIS;
            int skill = step % PREVIEW_XP_SKILLS;
            HudSkillState state = model.skillState(SKILLS[skill][0]);
            int level = state == null ? LEVELS[skill] : state.level();
            double progress = (state == null ? PROGRESS[skill] : state.progress()) + 0.21;
            if (progress >= 1.0) {
                progress -= 1.0;
                level++;
            }
            model.xp(new ServerFrame.XpEarned(SKILLS[skill][0], 4.0 + (step * 7) % 23, 1000.0, level, progress));
            step++;
        }
        if (now >= nextToast) {
            nextToast = now + PREVIEW_TOAST_MILLIS;
            ToastKind kind = ToastKind.values()[step % ToastKind.values().length];
            model.toast(new ServerFrame.Toast(kind, IconRef.item(SKILLS[step % SKILLS.length][2]), kind.name().charAt(0)
                    + kind.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' '), step % 2 == 0 ? LONG_TOAST : "Sample toast",
                    SKILLS[step % SKILLS.length][0]));
        }
        if (glyphs > 0 && now >= nextFlood) {
            nextFlood = now + 4000L;
            flood(model, glyphs, model.clock().epochMillis());
        }
        if (glyphs == 0 && now >= nextStatus) {
            nextStatus = now + PREVIEW_STATUS_MILLIS;
            long epoch = model.clock().epochMillis();
            model.status(glyph("architect-foundation.cooldown", HudGlyph.COOLDOWN, "architect", "minecraft:bricks", "Foundation",
                    epoch, 5000L, 0, 0, false));
            model.status(glyph("tragoul-skeletal-servant.charges", HudGlyph.CHARGES, "tragoul", "minecraft:skeleton_skull",
                    "Skeletal Servant", 0L, 0L, 1 + step % 3, 3, false));
            model.publish(new ServerFrame.HudPublish("adapt:status:sixth_sense", 30, 3000L, List.of(HudSlot.CENTER),
                    "\u00a7bSixth Sense \u00a77- \u00a7fsample notice"));
        }
    }

    /**
     * Publishes {@code count} glyphs at once, as when everything is on cooldown: mostly cooldowns of 4 to 60 s, with
     * charges, a running effect and a stance in every ten, spread over the sample skills. Glyphs still running keep
     * their place; ones that ended come back.
     */
    public static void flood(HudModel model, int count, long epoch) {
        for (int index = 0; index < count; index++) {
            String[] skill = SKILLS[index % SKILLS.length];
            String item = GLYPH_ITEMS[index % GLYPH_ITEMS.length];
            String purpose = "flood-" + index;
            int kind = index % 10;
            ServerFrame.HudStatus status;
            if (kind < 6) {
                status = glyph(purpose + ".cooldown", HudGlyph.COOLDOWN, skill[0], item, "Ability " + index, epoch,
                        4000L + (index * 7919L) % 56000L, 0, 0, false);
            } else if (kind < 8) {
                status = glyph(purpose + ".charges", HudGlyph.CHARGES, skill[0], item, "Charges " + index, 0L, 0L,
                        1 + index % 4, 4, false);
            } else if (kind == 8) {
                status = glyph(purpose + ".duration", HudGlyph.DURATION, skill[0], item, "Effect " + index, epoch, 20000L, 0, 0, false);
            } else {
                status = glyph(purpose + ".toggle", HudGlyph.TOGGLE, skill[0], item, "Stance " + index, 0L, 0L, 0, 0, true);
            }
            if (!active(model, status.purpose())) {
                model.status(status);
            }
        }
    }

    private static boolean active(HudModel model, String purpose) {
        for (HudStatusEntry entry : model.statuses()) {
            if (entry.purpose().equals(purpose)) {
                return true;
            }
        }
        return false;
    }

    private static ServerFrame.HudStatus glyph(String purpose, HudGlyph kind, String skillId, String item, String label,
                                               long startedAt, long duration, int stacks, int maxStacks, boolean lit) {
        return new ServerFrame.HudStatus("adapt:status:" + purpose, kind, skillId, IconRef.item(item), label, startedAt, duration,
                stacks, maxStacks, lit);
    }

    private static void fillViews(HudModel model) {
        List<SkillView> skills = new ArrayList<>(SKILLS.length);
        List<SkillLineView> lines = new ArrayList<>(SKILLS.length);
        long knowledge = 0L;
        for (int index = 0; index < SKILLS.length; index++) {
            String[] skill = SKILLS[index];
            skills.add(new SkillView(skill[0], skill[1], "", IconRef.item(skill[2]), Integer.parseInt(skill[3], 16),
                    true, List.of(), List.of(), List.of()));
            lines.add(new SkillLineView(skill[0], 1000.0 * LEVELS[index], LEVELS[index], PROGRESS[index], 250.0, KNOWLEDGE[index]));
            knowledge += KNOWLEDGE[index];
        }
        model.catalog(new CatalogView(1L, "en_us", skills));
        model.player(new PlayerView(new PlayerHeader(12, 0.45, 9, 14, 3 + knowledge, true), lines, Map.of(), List.of(),
                PlayerAccess.NONE));
    }

    private static int indexOf(String skillId) {
        for (int index = 0; index < SKILLS.length; index++) {
            if (SKILLS[index][0].equals(skillId)) {
                return index;
            }
        }
        return 0;
    }

    private static final class StoppedClock implements HudClock {
        private static final long EPOCH = 1_700_000_000_000L;
        private long millis = 1_000_000L;

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public long epochMillis() {
            return EPOCH + millis;
        }

        void advance(long amount) {
            millis += amount;
        }
    }
}
