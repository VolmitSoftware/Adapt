package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudRendererTest {
    private static final HudText TEXT = new HudText((String key) -> HudFakes.ENGLISH.getOrDefault(key, key));

    private static ServerFrame.HudStatus glyph(String purpose, HudGlyph kind, String skill, String item, long startedAt,
                                               long duration, int stacks, int maxStacks) {
        return new ServerFrame.HudStatus(purpose, kind, skill, IconRef.item(item), purpose, startedAt, duration, stacks, maxStacks, false);
    }

    private static HudRenderer sampleRenderer() {
        return new HudRenderer(HudSamples.editorModel(), TEXT);
    }

    private static List<HudRect> drawnRects(HudRenderer renderer) {
        List<HudRect> rects = new ArrayList<>();
        for (int index = 0; index < renderer.placedCount(); index++) {
            if (renderer.placed(index).drawn()) {
                rects.add(renderer.placed(index).box().rect());
            }
        }
        return rects;
    }

    private static boolean overlaps(HudRect first, HudRect second) {
        return first.x() < second.right() && second.x() < first.right() && first.y() < second.bottom() && second.y() < first.bottom();
    }

    @Test
    void anInactiveModelDrawsNothingAndPlacesNothing(@TempDir Path directory) {
        HudController controller = new HudController(new HudFakes.Host(), new HudLayoutStore(directory.resolve("hud.json")),
                new HudFakes.Clock(), false);
        HudFakes.Draw draw = new HudFakes.Draw(480, 270, 2.0, true);
        controller.render(draw, true);
        assertTrue(draw.calls.isEmpty());
        assertEquals(0, controller.renderer().placedCount());
    }

    @Test
    void withoutAutoFitEveryDefaultWidgetDrawsOnScreenAtEveryWindowAndGuiScale() {
        HudRenderer renderer = sampleRenderer();
        HudLayout layout = new HudLayout(HudDefaults.PROFILE_NAME, false, List.of(HudDefaults.profile(HudDefaults.PROFILE_NAME).withAutoFit(false)));
        for (int[] window : HudGeometryTest.WINDOWS) {
            for (int guiScale : HudGeometryTest.GUI_SCALES) {
                HudFakes.Draw draw = HudFakes.Draw.window(window[0], window[1], guiScale);
                renderer.render(draw, layout);
                assertEquals(0, draw.pose);
                assertEquals(HudWidgetKind.values().length, renderer.placedCount());
                for (int index = 0; index < renderer.placedCount(); index++) {
                    HudRenderer.Placed placed = renderer.placed(index);
                    String where = window[0] + "x" + window[1] + "@" + guiScale + " " + placed.config().id();
                    if (placed.config().kind() == HudWidgetKind.ABILITIES && !placed.drawn()) {
                        continue;
                    }
                    assertEquals(placed.config().placement().visible(), placed.drawn(), where);
                    if (!placed.drawn()) {
                        continue;
                    }
                    assertOnScreen(placed.box().rect(), draw, where);
                }
            }
        }
    }

    @Test
    void defaultWidgetsKeepClearOfEachOtherAndOfVanillaHudAtEveryCommonSize() {
        HudRenderer renderer = sampleRenderer();
        HudLayout layout = HudDefaults.layout();
        int[][] windows = {{1280, 720}, {1920, 1080}, {1024, 768}, {2560, 720}, {1366, 768}, {1600, 1200}, {2560, 1440},
                {3440, 1440}, {640, 480}};
        for (int[] window : windows) {
            for (int guiScale = 1; guiScale <= 4; guiScale++) {
                HudFakes.Draw draw = HudFakes.Draw.window(window[0], window[1], guiScale);
                renderer.render(draw, layout);
                String where = window[0] + "x" + window[1] + "@" + guiScale + " (" + draw.width() + "x" + draw.height() + ")";
                List<HudRect> rects = new ArrayList<>();
                for (int index = 0; index < renderer.placedCount(); index++) {
                    HudRenderer.Placed placed = renderer.placed(index);
                    if (placed.crowded()) {
                        assertTrue(draw.width() < 240, where + " crowded " + placed.config().id());
                        assertOnScreen(placed.box().rect(), draw, where);
                    } else if (placed.drawn()) {
                        rects.add(placed.box().rect());
                    }
                }
                for (HudRect rect : rects) {
                    assertOnScreen(rect, draw, where);
                    for (HudRect zone : vanillaZones(draw.width(), draw.height())) {
                        assertFalse(overlaps(rect, zone), where + " " + rect + " vs vanilla " + zone);
                    }
                }
                for (int first = 0; first < rects.size(); first++) {
                    for (int second = first + 1; second < rects.size(); second++) {
                        assertFalse(overlaps(rects.get(first), rects.get(second)), where + " " + rects.get(first) + " vs " + rects.get(second));
                    }
                }
            }
        }
    }

    @Test
    void roomyScreensKeepEveryShownWidgetAndSmallOnesDropTheLeastImportantFirst() {
        HudRenderer renderer = sampleRenderer();
        HudLayout layout = HudDefaults.layout();
        int shown = HudWidgetKind.values().length - 1;
        renderer.render(new HudFakes.Draw(640, 360, 2.0, false), layout);
        for (String id : List.of("skills", "abilities", "notices", "toasts")) {
            assertTrue(renderer.placed(id).drawn(), id);
        }
        assertEquals(shown, drawnRects(renderer).size());
        assertFalse(renderer.placed("resources").drawn());

        renderer.render(new HudFakes.Draw(427, 240, 3.0, false), layout);
        assertTrue(renderer.placed("skills").drawn());
        assertTrue(renderer.placed("abilities").drawn());

        renderer.render(new HudFakes.Draw(342, 256, 3.0, false), layout);
        assertTrue(renderer.placed("skills").drawn());
        assertTrue(renderer.placed("abilities").drawn());

        renderer.render(new HudFakes.Draw(320, 180, 4.0, false), layout);
        assertFalse(renderer.placed("skills").drawn());
        assertFalse(renderer.placed("abilities").drawn());

        HudLayout manual = new HudLayout("P", false, List.of(HudDefaults.profile("P").withAutoFit(false)));
        renderer.render(new HudFakes.Draw(320, 180, 4.0, false), manual);
        assertTrue(renderer.placed("skills").drawn());
        assertFalse(renderer.placed("abilities").drawn(), "no room between the protected zone and the hotbar");
        assertEquals(shown - 1, drawnRects(renderer).size());
    }

    @Test
    void theGlyphRowSitsJustUnderTheProtectedZone() {
        HudRenderer renderer = sampleRenderer();
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, false);
        renderer.render(draw, HudDefaults.layout());
        HudRect glyphs = renderer.placed("abilities").box().rect();
        HudBox zone = renderer.zone();
        assertEquals(96.0, zone.width(), 1e-6);
        assertEquals(54.0, zone.height(), 1e-6);
        assertEquals(320.0, glyphs.centerX(), 0.51);
        assertEquals(zone.y() + zone.height(), glyphs.y(), 1e-6);
        assertEquals(20.0, glyphs.height(), 1e-6);
    }

    /**
     * Vanilla HUD areas in scaled units: hotbar with status rows, held item name and action bar; chat left of the
     * hotbar; two boss bars; vanilla toasts and effect icons; a 13-line scoreboard; the crosshair.
     */
    private static List<HudRect> vanillaZones(double width, double height) {
        return List.of(
                new HudRect(width / 2 - 91, height - 72, 182, 72),
                new HudRect(0, height - 130, Math.min(330, width / 2 - 91), 90),
                new HudRect(width / 2 - 91, 0, 182, 50),
                new HudRect(width - 160, 0, 160, 52),
                new HudRect(width - 110, height / 2 - 60, 110, 120),
                new HudRect(width / 2 - 8, height / 2 - 8, 16, 16));
    }

    private static void assertOnScreen(HudRect rect, HudFakes.Draw draw, String where) {
        assertTrue(rect.x() >= 0 && rect.right() <= draw.width() + 1e-6, where + " " + rect);
        assertTrue(rect.y() >= 0 && rect.bottom() <= draw.height() + 1e-6, where + " " + rect);
    }

    @Test
    void hiddenWidgetsAreSkippedInGameAndGhostedInTheEditor() {
        HudRenderer renderer = sampleRenderer();
        HudProfile profile = HudDefaults.profile("P");
        HudWidgetConfig skills = profile.widget("skills");
        HudLayout layout = new HudLayout("P", false, List.of(profile.with(skills.withPlacement(skills.placement().withVisible(false)))));
        HudFakes.Draw draw = new HudFakes.Draw(480, 270, 2.0, true);
        renderer.render(draw, layout);
        assertFalse(renderer.placed("skills").drawn());
        assertFalse(draw.texts().contains("+12"));

        HudFakes.Draw editing = new HudFakes.Draw(480, 270, 2.0, true);
        renderer.renderEditing(editing, layout);
        assertTrue(renderer.placed("skills").drawn());
        String ghost = editing.calls.stream().filter((String call) -> call.startsWith("text ") && call.endsWith(" +12"))
                .findFirst().orElseThrow();
        assertTrue(ghost.split(" ")[2].startsWith("59"), ghost);
    }

    @Test
    void emptyWidgetsGetAStandInOnlyInTheEditor() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.publish(new ServerFrame.HudPublish("adapt:notice", 1, 1000L, List.of(), "x"));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(480, 270, 2.0, true);
        renderer.render(draw, HudDefaults.layout());
        assertFalse(renderer.placed("toasts").drawn());
        assertTrue(renderer.placed("notices").drawn());
        renderer.renderEditing(draw, HudDefaults.layout());
        assertTrue(renderer.placed("toasts").placeholder());
        assertEquals(HudRenderer.PLACEHOLDER_WIDTH, renderer.placed("toasts").box().width(), 1e-6);
    }

    @Test
    void steadyStateFramesDoNotAllocate() {
        HudRenderer renderer = sampleRenderer();
        HudLayout layout = HudDefaults.layout();
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 3.0, false);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        for (int frame = 0; frame < 200; frame++) {
            renderer.render(draw, layout);
        }
        long before = threads.getThreadAllocatedBytes(thread);
        for (int frame = 0; frame < 500; frame++) {
            renderer.render(draw, layout);
        }
        long allocated = threads.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated < 4096, allocated + " bytes over 500 frames");
    }

    @Test
    void aSkillRowAppearsOnItsFirstAwardAsOneLineAndShowsTheLevelOnlyWhenItChanges() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw empty = new HudFakes.Draw(640, 360, 2.0, true);
        model.player(new art.arcane.adapt.client.view.PlayerView(new art.arcane.adapt.client.view.PlayerHeader(1, 0.0, 0, 1, 0, true),
                List.of(new art.arcane.adapt.client.view.SkillLineView("agility", 400, 4, 0.4, 5, 0)), java.util.Map.of(), List.of(),
                art.arcane.adapt.client.view.PlayerAccess.NONE));
        renderer.render(empty, HudDefaults.layout());
        assertFalse(renderer.placed("skills").drawn());

        model.xp(new ServerFrame.XpEarned("agility", 12, 100, 4, 0.5));
        clock.advance(500L);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(draw, HudDefaults.layout());
        assertTrue(draw.texts().contains("+12"));
        assertFalse(draw.texts().contains("Lv 4"));
        assertFalse(draw.texts().contains("Agility"));
        assertEquals(SkillTrackerWidget.slotHeight(draw, (HudOptions.Skills) HudDefaults.options(HudWidgetKind.SKILLS)),
                renderer.placed("skills").box().height(), 1e-6);

        model.xp(new ServerFrame.XpEarned("agility", 30, 100, 5, 0.1));
        clock.advance(100L);
        HudFakes.Draw leveled = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(leveled, HudDefaults.layout());
        assertTrue(leveled.texts().contains("+42"));
        assertTrue(leveled.texts().contains("Lv 5"));

        clock.advance(SkillTrackerWidget.LEVEL_MILLIS);
        model.xp(new ServerFrame.XpEarned("agility", 1, 100, 5, 0.12));
        HudFakes.Draw later = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(later, HudDefaults.layout());
        assertFalse(later.texts().contains("Lv 5"));
    }

    @Test
    void twoSkillsShareTheWidgetInTheOrderTheyStartedThenBothVanishAfterTheLinger() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        HudRenderer renderer = new HudRenderer(model, TEXT);
        model.xp(new ServerFrame.XpEarned("agility", 3, 100, 4, 0.5));
        clock.advance(300L);
        model.xp(new ServerFrame.XpEarned("pickaxe", 5, 100, 2, 0.5));
        clock.advance(300L);
        model.xp(new ServerFrame.XpEarned("agility", 3, 100, 4, 0.6));
        clock.advance(300L);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(draw, HudDefaults.layout());
        List<String> texts = draw.texts();
        assertTrue(texts.indexOf("+6") < texts.indexOf("+5"), texts.toString());
        int slot = SkillTrackerWidget.slotHeight(draw, (HudOptions.Skills) HudDefaults.options(HudWidgetKind.SKILLS));
        assertEquals(slot * 2, renderer.placed("skills").box().height(), 1e-6);

        long linger = ((HudOptions.Skills) HudDefaults.options(HudWidgetKind.SKILLS)).lingerMillis();
        clock.advance(linger - 300L + HudModel.XP_FADE_MILLIS / 2);
        renderer.render(new HudFakes.Draw(640, 360, 2.0, false), HudDefaults.layout());
        double collapsing = renderer.placed("skills").box().height();
        assertTrue(collapsing > 0 && collapsing < slot * 2, Double.toString(collapsing));
        clock.advance(HudModel.XP_FADE_MILLIS);
        renderer.render(new HudFakes.Draw(640, 360, 2.0, false), HudDefaults.layout());
        assertFalse(renderer.placed("skills").drawn());
    }

    @Test
    void pinnedSkillsStayWithTheirLevel() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.player(new art.arcane.adapt.client.view.PlayerView(new art.arcane.adapt.client.view.PlayerHeader(1, 0.0, 0, 1, 0, true),
                List.of(new art.arcane.adapt.client.view.SkillLineView("agility", 400, 7, 0.4, 5, 0)), java.util.Map.of(), List.of(),
                art.arcane.adapt.client.view.PlayerAccess.NONE));
        HudProfile profile = HudDefaults.profile("P");
        HudWidgetConfig skills = profile.widget("skills");
        HudLayout layout = new HudLayout("P", false, List.of(profile.with(skills.withOptions(
                new HudOptions.Skills(List.of("agility"), 4, 3000L, false, 10)))));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(draw, layout);
        assertTrue(renderer.placed("skills").drawn());
        assertTrue(draw.texts().contains("7"));
    }

    @Test
    void glyphsDrawIconsWithoutTextGroupedBySkill() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.status(glyph("b", HudGlyph.COOLDOWN, "tragoul", "minecraft:shield", clock.epoch, 9000L, 0, 0));
        model.status(glyph("a", HudGlyph.COOLDOWN, "agility", "minecraft:ender_pearl", clock.epoch, 3000L, 0, 0));
        clock.advance(1000L);
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(480, 270, 2.0, true);
        renderer.render(draw, HudDefaults.layout());
        List<String> items = draw.calls.stream().filter((String call) -> call.startsWith("item ")).toList();
        assertEquals(2, items.size());
        assertTrue(items.get(0).contains("ender_pearl"));
        assertTrue(draw.texts().isEmpty(), draw.texts().toString());
        int glyph = 20;
        assertEquals(glyph * 2 + GlyphLayout.GAP, renderer.placed("abilities").box().width(), 1e-6);
    }

    @Test
    void glyphsCarryNoTextUnlessNamesAreOn() {
        HudRenderer renderer = sampleRenderer();
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.renderEditing(draw, HudDefaults.layout());
        assertFalse(draw.texts().contains("Foundation"), draw.texts().toString());
        HudProfile profile = HudDefaults.profile("P");
        HudWidgetConfig glyphs = profile.widget("abilities");
        HudOptions.Abilities named = ((HudOptions.Abilities) glyphs.options()).withFlags(false, true);
        HudFakes.Draw game = new HudFakes.Draw(960, 540, 2.0, true);
        renderer.render(game, new HudLayout("P", false, List.of(profile.with(glyphs.withOptions(named)))));
        assertTrue(game.texts().contains("Foundation"), game.texts().toString());
    }

    @Test
    void glyphsPastTheCapCollapseIntoACount() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        HudProfile profile = HudDefaults.profile(HudDefaults.PROFILE_NAME);
        HudWidgetConfig base = profile.widget("abilities");
        HudOptions.Abilities options = (HudOptions.Abilities) base.options();
        HudLayout layout = new HudLayout(HudDefaults.PROFILE_NAME, false, List.of(profile.with(base.withOptions(
                options.withLimits(8, options.maxRows(), options.maxColumns(), options.footprint())))));
        for (int index = 0; index < 11; index++) {
            model.status(glyph("g" + (char) ('a' + index), HudGlyph.COOLDOWN, "", "minecraft:stone", clock.epoch, 9000L, 0, 0));
        }
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(draw, layout);
        assertEquals(7, draw.calls.stream().filter((String call) -> call.startsWith("item ")).count());
        assertTrue(draw.texts().contains("+4"), draw.texts().toString());
    }

    @Test
    void toastsShowTitleAndBody() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.toast(new ServerFrame.Toast(ToastKind.UNLOCK, IconRef.item("minecraft:bow"), "Unlocked", "A very long body text that will not fit on the card"));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(480, 270, 2.0, false);
        renderer.render(draw, HudDefaults.layout());
        clock.advance(HudToastQueue.ENTER_MILLIS);
        HudFakes.Draw shown = new HudFakes.Draw(480, 270, 2.0, true);
        renderer.render(shown, HudDefaults.layout());
        assertTrue(shown.texts().contains("Unlocked"));
        assertTrue(shown.texts().stream().anyMatch((String text) -> text.startsWith("A very long") && text.endsWith("...")));
        HudRect rect = renderer.placed("toasts").box().rect();
        assertEquals(ToastWidget.CARD_WIDTH, rect.width(), 1e-6);
    }

    @Test
    void noticesFollowPriority() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.publish(new ServerFrame.HudPublish("adapt:status:a", 10, 5000L, List.of(), "ambient"));
        model.publish(new ServerFrame.HudPublish("adapt:title", 90, 5000L, List.of(), "title"));
        clock.advance(500L);
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(draw, HudDefaults.layout());
        List<String> texts = draw.texts();
        assertTrue(texts.indexOf("ambient") >= 0 && texts.indexOf("title") >= 0);
        assertEquals(NoticeWidget.LINE_HEIGHT + NoticeWidget.EMPHASIZED_HEIGHT, renderer.placed("notices").box().height(), 1e-6);
    }

    @Test
    void qaReportListsWidgetBounds() {
        HudRenderer renderer = sampleRenderer();
        renderer.render(new HudFakes.Draw(480, 270, 2.0, false), HudDefaults.layout());
        JsonObject report = JsonParser.parseString(HudQa.describe(renderer, HudDefaults.layout()).toString()).getAsJsonObject();
        assertEquals(480, report.get("screenWidth").getAsInt());
        assertEquals(HudWidgetKind.values().length, report.getAsJsonArray("widgets").size());
        JsonObject toasts = report.getAsJsonArray("widgets").get(HudWidgetKind.TOASTS.ordinal()).getAsJsonObject();
        assertEquals("toasts", toasts.get("id").getAsString());
        assertEquals(240, toasts.get("x").getAsDouble() + toasts.get("width").getAsDouble() / 2, 1e-6);
        assertTrue(toasts.get("drawn").getAsBoolean());
        assertTrue(report.get("autoFit").getAsBoolean());
    }

    @Test
    void uncappedCountsShowANumberAboveTheIconWhileCappedOnesShowOnlyTheRing() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.status(glyph("blocks", HudGlyph.CHARGES, "architect", "minecraft:bricks", 0L, 0L, 23, 0));
        model.status(glyph("power", HudGlyph.CHARGES, "architect", "minecraft:stone", 0L, 0L, 30, 35));
        model.status(glyph("pack", HudGlyph.CHARGES, "tragoul", "minecraft:skeleton_skull", 0L, 0L, 2, 3));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(draw, HudDefaults.layout());
        int item = draw.calls.indexOf(draw.calls.stream().filter((String call) -> call.startsWith("item ")).findFirst().orElseThrow());
        int count = draw.calls.indexOf(draw.calls.stream().filter((String call) -> call.startsWith("text ") && call.endsWith(" 23"))
                .findFirst().orElseThrow());
        assertTrue(count > item);
        int depth = draw.calls.subList(item, count).lastIndexOf("depth 200.0");
        assertTrue(depth >= 0, "the count is drawn above the item");
        assertFalse(draw.calls.subList(item + depth, count).stream().anyMatch((String call) -> call.startsWith("item ")));
        assertFalse(draw.texts().contains("2"));
        assertFalse(draw.texts().contains("30"));
        assertEquals(0, draw.pose);
    }

    @Test
    void challengeToastsReadLikeVanillaAndStayLonger() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.toast(new ServerFrame.Toast(ToastKind.CHALLENGE, IconRef.item("minecraft:elytra"), "Sky Walker", "Glide a mile"));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        renderer.render(new HudFakes.Draw(640, 360, 2.0, false), HudDefaults.layout());
        clock.advance(HudToastQueue.ENTER_MILLIS);
        HudFakes.Draw shown = new HudFakes.Draw(640, 360, 2.0, true);
        renderer.render(shown, HudDefaults.layout());
        assertTrue(shown.calls.stream().anyMatch((String call) -> call.startsWith("text ") && call.contains(" ff763078 Challenge Complete!")));
        assertTrue(shown.texts().contains("Sky Walker"));
        assertFalse(shown.texts().contains("Glide a mile"));

        HudToast toast = model.toasts().shown().get(0);
        clock.advance(5000L);
        model.expire(clock.millis);
        assertFalse(model.toasts().leaving(toast, clock.millis));
        clock.advance(5000L);
        assertTrue(model.toasts().leaving(toast, clock.millis));
    }

    @Test
    void skillNamesInDarkSkillColoursAreLiftedToReadOnThePanel() {
        int grey = HudPaint.readable(0xFF555555);
        assertEquals((grey >> 16) & 0xFF, grey & 0xFF);
        assertTrue((grey & 0xFF) >= 0x99, Integer.toHexString(grey));
        assertEquals(0xFFFFD45C, HudPaint.readable(0xFFFFD45C));
    }
}
