package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphLayoutTest {
    private static final HudText TEXT = new HudText((String key) -> HudFakes.ENGLISH.getOrDefault(key, key));
    private static final int[][] WINDOWS = {{1280, 720}, {1920, 1080}, {1024, 768}, {2560, 1440}, {3440, 1440}, {1366, 768},
            {854, 480}, {2560, 1080}};
    private static final int[] COUNTS = {5, 12, 30, 60, 90};

    private static HudLayout layout(GlyphArrangement arrangement) {
        HudProfile profile = HudDefaults.profile(HudDefaults.PROFILE_NAME);
        HudWidgetConfig base = profile.widget("abilities");
        HudOptions.Abilities options = ((HudOptions.Abilities) base.options()).with(arrangement);
        return new HudLayout(HudDefaults.PROFILE_NAME, false, List.of(profile.with(new HudWidgetConfig(base.id(), base.kind(),
                arrangement.preset(base.placement()), options))));
    }

    private static HudModel crowded(int count) {
        HudModel model = HudSamples.editorModel();
        HudSamples.flood(model, count, model.clock().epochMillis());
        return model;
    }

    private static boolean intersects(HudRect first, HudRect second) {
        return first.x() < second.right() - 1e-6 && second.x() < first.right() - 1e-6
                && first.y() < second.bottom() - 1e-6 && second.y() < first.bottom() - 1e-6;
    }

    @Test
    void noGlyphEverEntersTheProtectedZoneOrAnythingElseAtAnySizeCountOrArrangement() {
        for (GlyphArrangement arrangement : GlyphArrangement.values()) {
            HudLayout layout = layout(arrangement);
            for (int count : COUNTS) {
                HudRenderer renderer = new HudRenderer(crowded(count), TEXT);
                for (int[] window : WINDOWS) {
                    int maxScale = HudFakes.Draw.autoScale(window[0], window[1]);
                    for (int guiScale = 1; guiScale <= maxScale; guiScale++) {
                        HudFakes.Draw draw = HudFakes.Draw.window(window[0], window[1], guiScale);
                        renderer.render(draw, layout);
                        String where = arrangement + " x" + count + " " + window[0] + "x" + window[1] + "@" + guiScale;
                        check(renderer, draw, where);
                    }
                }
            }
        }
    }

    private static void check(HudRenderer renderer, HudFakes.Draw draw, String where) {
        HudRenderer.Placed glyphs = renderer.placed("abilities");
        HudBox zone = renderer.zone();
        HudRect protectedZone = new HudRect(zone.x(), zone.y(), zone.width(), zone.height());
        HudRect crosshair = new HudRect(draw.width() / 2.0 - 8, draw.height() / 2.0 - 8, 16, 16);
        List<HudRect> others = new ArrayList<>();
        for (int index = 0; index < renderer.placedCount(); index++) {
            HudRenderer.Placed other = renderer.placed(index);
            if (other != glyphs && other.drawn()) {
                others.add(other.box().rect());
            }
        }
        HudOptions.Abilities options = (HudOptions.Abilities) glyphs.config().options();
        HudWidget.Frame frame = new HudWidget.Frame();
        frame.screenWidth = draw.width();
        frame.screenHeight = draw.height();
        double capWidth = GlyphLayout.capWidth(frame, options);
        double capHeight = GlyphLayout.capHeight(frame, options);
        List<HudRect> cells = new ArrayList<>();
        for (int index = 0; index < glyphs.cellCount(); index++) {
            HudRect cell = glyphs.cell(index);
            String what = where + " cell " + index + " " + cell;
            assertFalse(intersects(cell, protectedZone), what + " in zone " + protectedZone);
            assertFalse(intersects(cell, crosshair), what + " on crosshair");
            assertTrue(cell.x() >= -1e-6 && cell.right() <= draw.width() + 1e-6, what + " off screen");
            assertTrue(cell.y() >= -1e-6 && cell.bottom() <= draw.height() + 1e-6, what + " off screen");
            assertFalse(HudVanilla.overlaps(cell.x(), cell.y(), cell.width() - 1e-6, cell.height() - 1e-6, draw.width(), draw.height()),
                    what + " on vanilla HUD");
            for (HudRect other : others) {
                assertFalse(intersects(cell, other), what + " on widget " + other);
            }
            for (HudRect previous : cells) {
                assertFalse(intersects(cell, previous), what + " on glyph " + previous);
            }
            cells.add(cell);
        }
        if (glyphs.cellCount() > 0) {
            HudRect box = glyphs.box().rect();
            assertTrue(box.width() <= capWidth + 1e-6, where + " wider than the cap " + box);
            if (options.arrangement() != GlyphArrangement.ARC) {
                assertTrue(box.height() <= capHeight + 1e-6, where + " taller than the cap " + box);
            }
        }
    }

    @Test
    void fewGlyphsMakeOneRowUnderTheZoneAndManyShrinkThenFoldIntoACount() {
        HudLayout layout = HudDefaults.layout();
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        HudRenderer few = new HudRenderer(crowded(5), TEXT);
        few.render(draw, layout);
        GlyphLayout five = layoutOf(few);
        assertEquals(14, five.iconSize);
        assertEquals(0, five.hidden);
        assertEquals(20.0, few.placed("abilities").box().height(), 1e-6);

        int previousSize = five.iconSize;
        for (int count : COUNTS) {
            HudRenderer renderer = new HudRenderer(crowded(count), TEXT);
            renderer.render(draw, layout);
            GlyphLayout glyphs = layoutOf(renderer);
            assertTrue(glyphs.iconSize <= previousSize, count + " grew the icons");
            previousSize = glyphs.iconSize;
            if (glyphs.hidden > 0) {
                assertEquals(AbilityWidget.iconSize(((HudOptions.Abilities) HudDefaults.options(HudWidgetKind.ABILITIES)).minIconSize()),
                        glyphs.iconSize,
                        count + " folded before shrinking fully");
                assertEquals(renderer.model().statuses().size(), glyphs.shown.size() + glyphs.hidden);
            }
        }
    }

    @Test
    void whenFoldingChargesAndStancesStayAndTheSoonestCooldownsWin() {
        HudFakes.Draw draw = new HudFakes.Draw(427, 240, 3.0, false);
        HudRenderer renderer = new HudRenderer(crowded(90), TEXT);
        renderer.render(draw, layout(GlyphArrangement.ROW));
        GlyphLayout glyphs = layoutOf(renderer);
        assertTrue(glyphs.hidden > 0);
        long latestShown = Long.MIN_VALUE;
        int states = 0;
        for (HudStatusEntry entry : glyphs.shown) {
            if (entry.glyph() == HudGlyph.CHARGES || entry.glyph() == HudGlyph.TOGGLE) {
                states++;
            } else {
                latestShown = Math.max(latestShown, entry.endsAt());
            }
        }
        int cooldownsShown = glyphs.shown.size() - states;
        for (HudStatusEntry entry : renderer.model().statuses()) {
            boolean state = entry.glyph() == HudGlyph.CHARGES || entry.glyph() == HudGlyph.TOGGLE;
            if (!glyphs.shown.contains(entry)) {
                assertTrue(cooldownsShown == 0 || !state, "a charge or stance was folded while cooldowns showed");
                assertTrue(state || entry.endsAt() >= latestShown, "a sooner cooldown was folded");
            }
        }
    }

    @Test
    void foldingBySkillShowsOneGlyphPerSkillWithItsCount() {
        HudProfile profile = HudDefaults.profile(HudDefaults.PROFILE_NAME);
        HudWidgetConfig base = profile.widget("abilities");
        HudOptions.Abilities options = ((HudOptions.Abilities) base.options()).withFlags(true, false);
        HudLayout layout = new HudLayout("P", false, List.of(profile.with(base.withOptions(options)).withName("P")));
        HudRenderer renderer = new HudRenderer(crowded(90), TEXT);
        renderer.render(new HudFakes.Draw(427, 240, 3.0, false), layout);
        GlyphLayout glyphs = layoutOf(renderer);
        int total = glyphs.hidden;
        List<String> skills = new ArrayList<>();
        for (int index = 0; index < glyphs.shown.size(); index++) {
            total += glyphs.groupCount[index];
            String skill = glyphs.shown.get(index).skillId();
            assertFalse(skills.contains(skill), "two glyphs for " + skill);
            skills.add(skill);
        }
        assertEquals(renderer.model().statuses().size(), total);
    }

    @Test
    void existingGlyphsKeepTheirOrderWhenOthersArriveOrLeave() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        for (String name : List.of("b", "d", "f", "h")) {
            model.status(cooldown(name, "agility", clock.epoch, 20000L));
        }
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        renderer.render(draw, HudDefaults.layout());
        List<String> before = purposes(layoutOf(renderer));
        model.status(cooldown("e", "agility", clock.epoch, 1000L));
        model.status(cooldown("a", "agility", clock.epoch, 50000L));
        clock.advance(16L);
        renderer.render(draw, HudDefaults.layout());
        List<String> after = purposes(layoutOf(renderer));
        assertEquals(List.of("a", "b", "d", "e", "f", "h"), after);
        List<String> kept = new ArrayList<>(after);
        kept.retainAll(before);
        assertEquals(before, kept);
        model.clear("d");
        renderer.render(draw, HudDefaults.layout());
        assertEquals(List.of("a", "b", "e", "f", "h"), purposes(layoutOf(renderer)));
    }

    @Test
    void aSteadyLayoutIsNotRebuilt() {
        HudRenderer renderer = new HudRenderer(crowded(30), TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        renderer.render(draw, HudDefaults.layout());
        GlyphLayout glyphs = layoutOf(renderer);
        HudStatusEntry first = glyphs.shown.get(0);
        renderer.render(draw, HudDefaults.layout());
        assertTrue(glyphs.shown.get(0) == first);
    }

    @Test
    void addingAGlyphWidgetMovesChargesAndStancesOffTheBaseSoNothingShowsTwice() {
        HudProfile profile = HudEditorOptions.addGlyphs(HudDefaults.profile("P"));
        HudOptions.Abilities base = (HudOptions.Abilities) profile.widget("abilities").options();
        HudOptions.Abilities extra = (HudOptions.Abilities) profile.widget("abilities_2").options();
        assertTrue(base.cooldowns() && base.durations() && !base.charges() && !base.toggles());
        assertTrue(!extra.cooldowns() && !extra.durations() && extra.charges() && extra.toggles());
        HudModel model = crowded(20);
        HudRenderer renderer = new HudRenderer(model, TEXT);
        renderer.render(new HudFakes.Draw(960, 540, 2.0, false), new HudLayout("P", false, List.of(profile)));
        assertEquals(model.statuses().size(), renderer.placed("abilities").cellCount() + renderer.placed("abilities_2").cellCount());
    }

    @Test
    void eachGlyphWidgetShowsOnlyItsKindsAndSkills() {
        HudProfile profile = HudEditorOptions.addGlyphs(HudDefaults.profile("P"));
        HudWidgetConfig base = profile.widget("abilities");
        HudOptions.Abilities cooldownsOnly = ((HudOptions.Abilities) base.options()).withKinds(true, false, true, false);
        profile = profile.with(base.withOptions(cooldownsOnly));
        HudWidgetConfig extra = profile.widget("abilities_2");
        HudOptions.Abilities agilityOnly = ((HudOptions.Abilities) extra.options()).withSkills(List.of("agility"));
        HudProfile agility = profile.with(extra.withOptions(agilityOnly));
        HudModel model = crowded(20);
        HudRenderer renderer = new HudRenderer(model, TEXT);
        renderer.render(new HudFakes.Draw(960, 540, 2.0, false), new HudLayout("P", false, List.of(profile)));
        int timed = 0;
        int states = 0;
        for (HudStatusEntry entry : model.statuses()) {
            if (entry.glyph() == HudGlyph.CHARGES || entry.glyph() == HudGlyph.TOGGLE) {
                states++;
            } else {
                timed++;
            }
        }
        assertEquals(timed, renderer.placed("abilities").cellCount());
        assertEquals(states, renderer.placed("abilities_2").cellCount());
        HudRect first = renderer.placed("abilities").box().rect();
        HudRect second = renderer.placed("abilities_2").box().rect();
        assertFalse(intersects(first, second));

        renderer.render(new HudFakes.Draw(960, 540, 2.0, false), new HudLayout("P", false, List.of(agility)));
        for (int index = 0; index < layoutOf(renderer, "abilities_2").shown.size(); index++) {
            assertEquals("agility", layoutOf(renderer, "abilities_2").shown.get(index).skillId());
        }
    }

    @Test
    void turningProtectionOffStillKeepsTheCrosshairClear() {
        HudProfile profile = HudDefaults.profile("P").withProtect(0.0);
        HudRenderer renderer = new HudRenderer(crowded(12), TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, false);
        renderer.render(draw, new HudLayout("P", false, List.of(profile)));
        HudRenderer.Placed glyphs = renderer.placed("abilities");
        assertTrue(glyphs.cellCount() > 0);
        for (int index = 0; index < glyphs.cellCount(); index++) {
            assertTrue(glyphs.cell(index).y() >= 188, glyphs.cell(index).toString());
        }
    }

    @Test
    void theEditorShowsTheProtectedZone() {
        HudRenderer renderer = new HudRenderer(HudSamples.editorModel(), TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.renderEditing(draw, HudDefaults.layout(), null);
        assertTrue(draw.texts().contains("Protected view"), draw.texts().toString());
    }

    private static ServerFrame.HudStatus cooldown(String purpose, String skill, long startedAt, long duration) {
        return new ServerFrame.HudStatus(purpose, HudGlyph.COOLDOWN, skill, IconRef.item("minecraft:stone"), purpose, startedAt,
                duration, 0, 0, false);
    }

    private static List<String> purposes(GlyphLayout layout) {
        List<String> purposes = new ArrayList<>();
        for (HudStatusEntry entry : layout.shown) {
            purposes.add(entry.purpose());
        }
        return purposes;
    }

    private static GlyphLayout layoutOf(HudRenderer renderer) {
        return layoutOf(renderer, "abilities");
    }

    private static GlyphLayout layoutOf(HudRenderer renderer, String id) {
        return renderer.glyphLayout(id);
    }
}
