package art.arcane.adapt.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The band scene timeline on game-sized books, tops and feet, for every skill that has one: deterministic, bounded, clear
 * of content and the screen edge, still when asked, and split at the spine.
 */
class BandSceneTest {
    /** Every skill with a designed top or foot, so a newly drawn skill is checked without editing this test. */
    private static final List<String> SCENES = ThemeFixtures.SKILLS.stream()
            .filter((String skill) -> ThemeFixtures.kit(skill).foot() != null || ThemeFixtures.kit(skill).top() != null).toList();
    private static final int[][] SCREENS = {{640, 360}, {480, 270}, {470, 266}, {427, 240}, {960, 540}};
    private static final int STEP_MILLIS = 50;

    private static List<PageTheme> themes() {
        List<PageTheme> themes = new ArrayList<>();
        for (String skill : SCENES) {
            themes.add(ThemeFixtures.theme(skill, null));
            for (String other : List.of("golemancy", "brewing", "chronos", "stealth")) {
                if (!other.equals(skill)) {
                    themes.add(ThemeFixtures.theme(skill, other));
                    themes.add(ThemeFixtures.theme(other, skill));
                }
            }
        }
        return themes;
    }

    private static List<int[]> frame(List<BandStage> stages, long millis) {
        RecordingDraw draw = new RecordingDraw();
        for (BandStage stage : stages) {
            stage.paint(draw, millis / 1000.0);
        }
        return draw.blits;
    }

    private static long loopMillis(BandStage stage) {
        return Math.round(stage.scene().period() * 1000);
    }

    @Test
    void theReferenceSkillsHaveBothBands() {
        for (String skill : List.of("golemancy", "rift", "herbalism")) {
            assertNotNull(ThemeFixtures.kit(skill).foot(), skill);
            assertNotNull(ThemeFixtures.kit(skill).top(), skill);
        }
        assertNull(ThemeFixtures.kit("chronos").top(), "Chronos keeps its own clockwork");
        assertNull(ThemeFixtures.kit("stealth").top(), "Stealth keeps its own smoke");
    }

    @Test
    void sameBookSameTimeSameFrame() {
        BookGeometry geometry = DressingRulesTest.book(0, 640, 360);
        for (PageTheme theme : themes()) {
            List<BandStage> first = BandStage.of(geometry, theme);
            List<BandStage> second = BandStage.of(geometry, theme);
            for (long millis = 0; millis < 40_000; millis += 730) {
                List<int[]> a = frame(first, millis);
                List<int[]> b = frame(second, millis);
                assertEquals(a.size(), b.size());
                for (int index = 0; index < a.size(); index++) {
                    assertTrue(Arrays.equals(a.get(index), b.get(index)), "frame at " + millis);
                }
            }
        }
    }

    @Test
    void aFrameIsBoundedByTheCast() {
        BookGeometry geometry = DressingRulesTest.book(0, 640, 360);
        for (PageTheme theme : themes()) {
            for (BandStage stage : BandStage.of(geometry, theme)) {
                assertTrue(stage.actors().size() <= BandScene.MAX_ELEMENTS);
                int most = stage.behind() ? strip(stage).size() : 0;
                for (BandStage.Actor actor : stage.actors()) {
                    int own = 2;
                    for (BandScene.Emit emit : actor.emit) {
                        own = Math.max(own, emit == null ? 2 : emit.count());
                    }
                    most += own;
                }
                for (long millis = 0; millis < loopMillis(stage) * 2; millis += STEP_MILLIS) {
                    assertTrue(frame(List.of(stage), millis).size() <= most, "blits at " + millis);
                }
            }
        }
    }

    /** The strip's blits, as the static layer records them. */
    private static List<int[]> strip(BandStage stage) {
        DrawList layer = new DrawList();
        stage.ground(layer);
        RecordingDraw draw = new RecordingDraw();
        layer.paint(draw);
        return draw.blits;
    }

    /** Which scene sprite, and which way round, sits at each atlas rectangle of a band; null for the strip's own pieces. */
    private static Map<Long, Object[]> spritesByRect(BandStage stage) {
        Map<Long, Object[]> out = new HashMap<>();
        BandScene scene = stage.scene();
        for (BandScene.Element element : scene.cast()) {
            BandScene.Sprite sprite = scene.sprite(element.sprite());
            for (int frame = 0; frame < sprite.frames.length / 2; frame++) {
                for (boolean flip : new boolean[]{false, true}) {
                    ThemeSprite rect = sprite.frame(frame, flip);
                    out.putIfAbsent(((long) rect.u() << 32) | rect.v(), new Object[]{sprite, flip});
                }
            }
            for (BandScene.Step step : element.steps()) {
                if (step.carry() != null) {
                    for (String carried : step.carry()) {
                        BandScene.Sprite item = scene.sprite(carried);
                        for (boolean flip : new boolean[]{false, true}) {
                            ThemeSprite rect = item.frame(0, flip);
                            out.putIfAbsent(((long) rect.u() << 32) | rect.v(), new Object[]{item, flip});
                        }
                    }
                }
            }
        }
        return out;
    }

    @Test
    void nothingCoversContentOrLeavesThePageOrItsHalf() {
        for (int[] screen : SCREENS) {
            for (int leaf = 0; leaf < 3; leaf++) {
                BookGeometry geometry = DressingRulesTest.book(leaf, screen[0], screen[1]);
                for (PageTheme theme : themes()) {
                    for (BandStage stage : BandStage.of(geometry, theme)) {
                        Map<Long, Object[]> rects = spritesByRect(stage);
                        for (long millis = 0; millis < loopMillis(stage) * 2; millis += STEP_MILLIS) {
                            for (int[] b : frame(List.of(stage), millis)) {
                                // a strip piece, whichever frame of its tile it shows
                                if (stage.behind() && strip(stage).stream().anyMatch((int[] piece) -> Arrays.equals(piece, 0, 4, b, 0, 4))) {
                                    continue;
                                }
                                String at = theme.lead().skill() + "/" + (theme.mutated() ? theme.partner().skill() : "") + " leaf " + leaf + " on "
                                        + screen[0] + "x" + screen[1] + " at " + millis + " ms: " + stage.kit().skill() + " blit at " + b[0] + "," + b[1];
                                assertTrue(b[0] >= 1 && b[1] >= 1 && b[0] + b[2] <= screen[0] - 1 && b[1] + b[3] <= screen[1] - 1, at + " leaves the screen");
                                assertTrue(b[0] >= geometry.cover().x0() - 12 && b[0] + b[2] <= geometry.cover().x1() + 12, at + " leaves the book's width and its corners");
                                Object[] found = rects.get(((long) b[4] << 32) | b[5]);
                                assertNotNull(found, at + " is not a scene sprite");
                                BandScene.Sprite sprite = (BandScene.Sprite) found[0];
                                boolean flip = (Boolean) found[1];
                                for (BookGeometry.Box box : geometry.keep()) {
                                    for (int y = 0; y < b[3]; y++) {
                                        for (int x = 0; x < b[2]; x++) {
                                            int px = b[0] + x;
                                            int py = b[1] + y;
                                            boolean inside = px >= box.x0() && px < box.x1() && py >= box.y0() && py < box.y1();
                                            assertFalse(inside && sprite.opaque(flip, x, y), at + " covers content");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void eachParentPlaysItsOwnHalfOfAMutatedSpread() {
        BookGeometry geometry = DressingRulesTest.book(0, 640, 360);
        int fold = geometry.fold();
        for (String lead : SCENES) {
            for (String partner : SCENES) {
                if (lead.equals(partner)) {
                    continue;
                }
                List<BandStage> stages = BandStage.of(geometry, ThemeFixtures.theme(lead, partner));
                for (BandStage stage : stages) {
                    boolean leftSide = stage.kit().skill().equals(lead);
                    int lap = stage.top() ? 3 : 0;
                    assertEquals(!leftSide, stage.mirror(), "the partner's band faces the spine from the right");
                    assertEquals(leftSide ? fold + lap : fold - lap, leftSide ? stage.groundTo() : stage.groundFrom(),
                            lead + "/" + partner + ": the strips meet at the spine with no gap");
                    for (BandStage.Actor actor : stage.actors()) {
                        if (actor.cross()) {
                            continue;
                        }
                        BandScene.Sprite sp = actor.sprite;
                        for (int i = 0; i < actor.steps; i++) {
                            if ((actor.clip[i] == null && actor.alt[i] == null) || actor.emit[i] != null) {
                                continue;
                            }
                            int ax = actor.flip[i] ? sp.width - 1 - sp.anchorX : sp.anchorX;
                            double from = Math.min(actor.x0[i], actor.x1[i]) - ax;
                            double to = Math.max(actor.x0[i], actor.x1[i]) - ax + sp.width;
                            String at = lead + "/" + partner + " " + actor.id() + " step " + i;
                            assertTrue(leftSide ? to <= stage.groundTo() + 0.5 : from >= stage.groundFrom() - 0.5, at + " crosses the spine");
                        }
                    }
                }
            }
        }
        for (BandStage stage : BandStage.of(geometry, ThemeFixtures.theme("chronos", "golemancy"))) {
            assertEquals("golemancy", stage.kit().skill(), "a parent without bands keeps its own top and foot");
            assertTrue(stage.mirror());
        }
    }

    @Test
    void nothingCrossesThePlainSpine() {
        BookGeometry spread = DressingRulesTest.book(0, 640, 360);
        for (String skill : SCENES) {
            for (BandStage stage : BandStage.of(spread, ThemeFixtures.theme(skill, null))) {
                assertTrue(stage.actors().stream().noneMatch(BandStage.Actor::cross), skill);
            }
        }
        assertTrue(BandStage.of(spread, ThemeFixtures.theme("golemancy", "brewing")).get(0).actors().stream().anyMatch(BandStage.Actor::cross),
                "Golemancy's snowball reaches across the spine of a mutation");
    }

    @Test
    void reducedMotionHoldsAFullStillPose() {
        for (String skill : SCENES) {
            List<BandStage> stages = BandStage.of(DressingRulesTest.book(0, 640, 360), ThemeFixtures.theme(skill, null));
            List<int[]> still = frame(stages, 0);
            assertTrue(still.size() >= 2, skill + " still pose shows " + still.size() + " sprites");
            List<int[]> again = frame(stages, 0);
            for (int index = 0; index < still.size(); index++) {
                assertTrue(Arrays.equals(still.get(index), again.get(index)));
            }
        }
    }
}
