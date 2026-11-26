package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudToastQueueTest {
    private static final HudText TEXT = new HudText((String key) -> HudFakes.ENGLISH.getOrDefault(key, key));
    private static final long FRAME_MILLIS = 20L;
    private static final double[][] SCREENS = {{960, 540, 2}, {640, 360, 2}, {427, 240, 3}, {320, 240, 2}, {320, 180, 4}};
    private static final HudAnchor[] ANCHORS = {HudAnchor.TOP_CENTER, HudAnchor.TOP_RIGHT, HudAnchor.BOTTOM_LEFT};

    private final HudFakes.Clock clock = new HudFakes.Clock();
    private final HudModel model = new HudModel(clock);

    private static ServerFrame.Toast toast(ToastKind kind, String icon, String title, String body) {
        return new ServerFrame.Toast(kind, IconRef.item(icon), title, body, "");
    }

    private static String title(int index) {
        return "Card " + (char) ('A' + index);
    }

    private static HudLayout layout(HudAnchor anchor, int maxVisible, boolean reducedMotion) {
        HudProfile profile = HudDefaults.layout().activeProfile();
        List<HudWidgetConfig> widgets = new ArrayList<>();
        for (HudWidgetConfig config : profile.widgets()) {
            if (config.kind() == HudWidgetKind.TOASTS) {
                double offsetX = anchor.column() == 0 ? 4 : anchor.column() == 2 ? -4 : 0;
                double offsetY = anchor == HudAnchor.BOTTOM_LEFT ? -40 : 54;
                widgets.add(new HudWidgetConfig(config.id(), config.kind(), config.placement().withAnchor(anchor, offsetX, offsetY),
                        new HudOptions.Toasts(3000L, maxVisible)));
            } else {
                widgets.add(config);
            }
        }
        HudProfile edited = new HudProfile(profile.name(), profile.scale(), profile.autoFit(), profile.protect(), widgets);
        return new HudLayout(edited.name(), reducedMotion, List.of(edited));
    }

    /** Titles of the cards or pills drawn this frame. */
    private static void seen(HudFakes.Draw draw, int toastCount, Set<String> seen) {
        for (String text : draw.texts()) {
            for (int index = 0; index < toastCount; index++) {
                if (text.equals(title(index)) || text.startsWith(title(index) + " ")) {
                    seen.add(title(index));
                }
            }
        }
    }

    private void burst(HudLayout layout, double[] screen, int count) {
        HudFakes.Clock frameClock = new HudFakes.Clock();
        HudModel burstModel = new HudModel(frameClock);
        HudRenderer renderer = new HudRenderer(burstModel, TEXT);
        int width = (int) screen[0];
        int height = (int) screen[1];
        int cap = layout.activeProfile().widgets().stream().filter((HudWidgetConfig config) -> config.kind() == HudWidgetKind.TOASTS)
                .map((HudWidgetConfig config) -> ((HudOptions.Toasts) config.options()).maxVisible()).findFirst().orElseThrow();
        String where = width + "x" + height + " gui " + screen[2] + " " + layout.activeProfile().widgets().stream()
                .filter((HudWidgetConfig config) -> config.kind() == HudWidgetKind.TOASTS).findFirst().orElseThrow();
        Set<String> seen = new HashSet<>();
        int sent = 0;
        for (int frame = 0; frame < 40_000; frame++) {
            if (sent < count && frame % 3 == 0) {
                ToastKind kind = sent == 1 ? ToastKind.CHALLENGE : ToastKind.UNLOCK;
                String body = sent % 3 == 0 ? HudSamples.LONG_TOAST : sent % 3 == 1 ? "" : "detail";
                burstModel.toast(toast(kind, "minecraft:item_" + sent, title(sent), body));
                sent++;
            }
            HudFakes.Draw draw = new HudFakes.Draw(width, height, screen[2], true);
            renderer.render(draw, layout);
            seen(draw, count, seen);
            List<int[]> rects = ToastWidgetTest.papers(draw);
            int limit = width >= HudFit.FULL_WIDTH && height >= HudFit.FULL_HEIGHT ? cap : 1;
            assertTrue(rects.size() <= limit, where + " frame " + frame + ": " + rects.size() + " cards");
            for (int first = 0; first < rects.size(); first++) {
                for (int second = first + 1; second < rects.size(); second++) {
                    int[] a = rects.get(first);
                    int[] b = rects.get(second);
                    assertFalse(ToastWidgetTest.overlap(a, b), where + " frame " + frame + ": " + a[0] + "," + a[1] + " and " + b[0] + "," + b[1]);
                }
            }
            if (sent == count && burstModel.toasts().shown().isEmpty() && burstModel.toasts().queuedCount() == 0) {
                break;
            }
            frameClock.advance(FRAME_MILLIS);
        }
        assertEquals(count, seen.size(), where + " saw " + seen);
    }

    @Test
    void aBurstOfToastsNeverDrawsTwoCardsInTheSameSpot() {
        for (double[] screen : SCREENS) {
            for (HudAnchor anchor : ANCHORS) {
                for (int maxVisible : new int[] {1, 3}) {
                    for (boolean reducedMotion : new boolean[] {false, true}) {
                        burst(layout(anchor, maxVisible, reducedMotion), screen, 5);
                    }
                }
            }
        }
    }

    @Test
    void aLongBurstPlaysEveryToastWithoutOverlap() {
        burst(layout(HudAnchor.TOP_CENTER, 3, false), new double[] {960, 540, 2}, 14);
        burst(layout(HudAnchor.TOP_CENTER, 1, false), new double[] {320, 180, 4}, 14);
    }

    @Test
    void cardsKeepTheirSlotUntilFullyGoneAndNeverShareOne() {
        Random random = new Random(7L);
        for (int run = 0; run < 200; run++) {
            HudFakes.Clock runClock = new HudFakes.Clock();
            HudModel runModel = new HudModel(runClock);
            int cap = 1 + random.nextInt(4);
            int count = 2 + random.nextInt(12);
            long duration = 1000L + random.nextInt(2) * 4000L;
            int sent = 0;
            for (int frame = 0; frame < 20_000; frame++) {
                runModel.toasts().configure(duration, cap, 300.0, false);
                while (sent < count && random.nextInt(4) == 0) {
                    ToastKind kind = random.nextInt(5) == 0 ? ToastKind.CHALLENGE : ToastKind.GENERIC;
                    runModel.toast(toast(kind, "minecraft:item_" + sent, title(sent), ""));
                    sent++;
                }
                for (HudToast toast : runModel.toasts().shown()) {
                    toast.height = 32 + Math.floorMod(toast.title().hashCode(), 5) * 9;
                }
                if (runModel.toasts().waiting() != null) {
                    runModel.toasts().waiting().height = 32 + Math.floorMod(runModel.toasts().waiting().title().hashCode(), 5) * 9;
                }
                runModel.expire(runClock.millis);
                List<HudToast> shown = runModel.toasts().shown();
                assertTrue(shown.size() <= cap, "run " + run + " frame " + frame);
                double bottom = 0.0;
                for (int index = 0; index < shown.size(); index++) {
                    HudToast card = shown.get(index);
                    assertTrue(card.offset() >= -1e-9, "run " + run + " offset " + card.offset());
                    if (index > 0) {
                        assertTrue(card.offset() >= bottom + HudToastQueue.GAP - 1e-9,
                                "run " + run + " frame " + frame + " offsets " + shown.get(index - 1).offset() + ", " + card.offset());
                    }
                    bottom = card.offset() + card.height();
                }
                assertTrue(shown.size() <= 1 || bottom <= 300.0 + 1e-9, "run " + run + " frame " + frame + " stack " + bottom);
                if (sent == count && shown.isEmpty() && runModel.toasts().queuedCount() == 0) {
                    break;
                }
                runClock.advance(10L + random.nextInt(30));
            }
            assertTrue(runModel.toasts().shown().isEmpty() && runModel.toasts().queuedCount() == 0, "run " + run + " never drained");
        }
    }

    @Test
    void aLeavingCardHoldsItsSlotUntilItHasSlidOut() {
        model.toasts().configure(1000L, 1, Double.MAX_VALUE, false);
        model.toast(toast(ToastKind.GENERIC, "minecraft:stone", "a", ""));
        model.toast(toast(ToastKind.GENERIC, "minecraft:dirt", "b", ""));
        model.expire(clock.millis);
        HudToast first = model.toasts().shown().get(0);
        clock.advance(model.toasts().duration(first));
        model.expire(clock.millis);
        assertTrue(model.toasts().leaving(first, clock.millis));
        assertEquals(List.of(first), model.toasts().shown());
        assertEquals(1, model.toasts().queuedCount());
        clock.advance(HudToastQueue.EXIT_MILLIS);
        model.expire(clock.millis);
        assertEquals("b", model.toasts().shown().get(0).title());
        assertEquals(0.0, model.toasts().shown().get(0).offset());
    }

    @Test
    void aLoneCardIsOnScreenForTheWholeDurationAndNoLonger() {
        model.toasts().configure(3000L, 3, Double.MAX_VALUE, false);
        model.toast(toast(ToastKind.CHALLENGE, "minecraft:clock", "Marathon", HudSamples.LONG_TOAST));
        model.expire(clock.millis);
        HudToast card = model.toasts().shown().get(0);
        assertEquals(3000L, model.toasts().duration(card) + HudToastQueue.EXIT_MILLIS);
        clock.advance(2999L);
        model.expire(clock.millis);
        assertEquals(List.of(card), model.toasts().shown());
        clock.advance(1L);
        model.expire(clock.millis);
        assertTrue(model.toasts().shown().isEmpty());
    }

    @Test
    void aBacklogShortensEveryCardAndDrainsQuickly() {
        model.toasts().configure(3000L, 1, Double.MAX_VALUE, false);
        model.expire(clock.millis);
        for (int index = 0; index < 5; index++) {
            model.toast(toast(ToastKind.UNLOCK, "minecraft:item_" + index, title(index), ""));
        }
        model.expire(clock.millis);
        HudToast first = model.toasts().shown().get(0);
        assertEquals(HudToastQueue.MIN_STAY_MILLIS - HudToastQueue.EXIT_MILLIS, model.toasts().duration(first));
        long start = clock.millis;
        List<Long> stays = new ArrayList<>();
        Set<String> played = new HashSet<>();
        for (int frame = 0; frame < 2000 && (model.toasts().queuedCount() > 0 || !model.toasts().shown().isEmpty()); frame++) {
            for (HudToast shown : model.toasts().shown()) {
                if (played.add(shown.title())) {
                    stays.add(model.toasts().duration(shown));
                }
            }
            clock.advance(16L);
            model.expire(clock.millis);
        }
        assertEquals(5, played.size());
        for (int index = 1; index < stays.size(); index++) {
            assertTrue(stays.get(index) >= stays.get(index - 1), stays.toString());
        }
        assertEquals(3000L - HudToastQueue.EXIT_MILLIS, stays.get(stays.size() - 1));
        assertTrue(clock.millis - start < 5 * 3000L * 2 / 3, Long.toString(clock.millis - start));
    }

    @Test
    void aCardAlreadyUpShortensWhenABacklogArrivesButNeverJumpsOut() {
        model.toasts().configure(3000L, 1, Double.MAX_VALUE, false);
        model.toast(toast(ToastKind.UNLOCK, "minecraft:bow", "First", ""));
        model.expire(clock.millis);
        HudToast first = model.toasts().shown().get(0);
        assertEquals(3000L - HudToastQueue.EXIT_MILLIS, model.toasts().duration(first));
        clock.advance(1500L);
        for (int index = 0; index < 4; index++) {
            model.toast(toast(ToastKind.UNLOCK, "minecraft:item_" + index, title(index), ""));
        }
        model.expire(clock.millis);
        assertEquals(1500L, model.toasts().duration(first));
        assertTrue(model.toasts().leaving(first, clock.millis));
        assertEquals(List.of(first), model.toasts().shown());
    }

    @Test
    void aNewerLevelUpForTheSameSkillReplacesTheWaitingOne() {
        model.toasts().configure(5000L, 1, Double.MAX_VALUE, false);
        model.toast(toast(ToastKind.UNLOCK, "minecraft:bow", "Archery unlocked", ""));
        model.expire(clock.millis);
        for (int level = 3; level <= 6; level++) {
            model.toast(toast(ToastKind.LEVEL_UP, "minecraft:feather", "Agility " + level, ""));
            model.toast(toast(ToastKind.LEVEL_UP, "minecraft:iron_pickaxe", "Pickaxe " + level, ""));
        }
        model.toast(toast(ToastKind.UNLOCK, "minecraft:bow", "Archery unlocked", ""));
        model.toast(toast(ToastKind.UNLOCK, "minecraft:bow", "Quick draw unlocked", ""));
        assertEquals(3, model.toasts().queuedCount());
        List<String> played = new ArrayList<>();
        for (int frame = 0; frame < 2000 && (model.toasts().queuedCount() > 0 || !model.toasts().shown().isEmpty()); frame++) {
            for (HudToast shown : model.toasts().shown()) {
                if (!played.contains(shown.title())) {
                    played.add(shown.title());
                }
            }
            clock.advance(50L);
            model.expire(clock.millis);
        }
        assertEquals(List.of("Archery unlocked", "Agility 6", "Pickaxe 6", "Quick draw unlocked"), played);
    }

    @Test
    void aNewerLevelUpUpdatesTheCardOnScreenInPlace() {
        model.toasts().configure(5000L, 3, Double.MAX_VALUE, false);
        model.toast(toast(ToastKind.LEVEL_UP, "minecraft:feather", "Agility 3", ""));
        model.toast(toast(ToastKind.LEVEL_UP, "minecraft:stone", "Master 2", ""));
        model.expire(clock.millis);
        HudToast master = model.toasts().shown().get(1);
        clock.advance(2000L);
        model.toast(toast(ToastKind.LEVEL_UP, "minecraft:feather", "Agility 4", ""));
        model.expire(clock.millis);
        assertEquals(2, model.toasts().shown().size());
        assertEquals("Agility 4", model.toasts().shown().get(0).title());
        assertSame(master, model.toasts().shown().get(1));
        assertEquals(0, model.toasts().queuedCount());
        clock.advance(4000L);
        model.expire(clock.millis);
        assertFalse(model.toasts().leaving(model.toasts().shown().get(0), clock.millis));
    }

    @Test
    void toastsThatWaitedOutAnOpenScreenAreDroppedWhenItCloses() {
        model.toasts().configure(5000L, 1, Double.MAX_VALUE, false);
        model.toast(toast(ToastKind.UNLOCK, "minecraft:bow", "Before", ""));
        model.expire(clock.millis);
        model.toast(toast(ToastKind.UNLOCK, "minecraft:dirt", "Early", ""));
        model.toast(toast(ToastKind.LEVEL_UP, "minecraft:feather", "Agility 3", ""));
        clock.advance(4000L);
        model.toast(toast(ToastKind.LEVEL_UP, "minecraft:feather", "Agility 5", ""));
        model.toast(toast(ToastKind.UNLOCK, "minecraft:stone", "Late", ""));
        clock.advance(2500L);
        model.expire(clock.millis);
        assertEquals("Agility 5", model.toasts().shown().get(0).title());
        assertEquals(1, model.toasts().queuedCount());
        List<String> played = new ArrayList<>();
        for (int frame = 0; frame < 1000 && (model.toasts().queuedCount() > 0 || !model.toasts().shown().isEmpty()); frame++) {
            for (HudToast shown : model.toasts().shown()) {
                if (!played.contains(shown.title())) {
                    played.add(shown.title());
                }
            }
            clock.advance(16L);
            model.expire(clock.millis);
        }
        assertEquals(List.of("Agility 5", "Late"), played);
    }

    @Test
    void toastsWaitingBehindALongBurstAreNotDroppedWhileTheHudKeepsDrawing() {
        model.toasts().configure(5000L, 1, Double.MAX_VALUE, false);
        model.expire(clock.millis);
        for (int index = 0; index < 6; index++) {
            model.toast(toast(ToastKind.UNLOCK, "minecraft:item_" + index, title(index), ""));
        }
        Set<String> played = new HashSet<>();
        for (int frame = 0; frame < 5000 && (model.toasts().queuedCount() > 0 || !model.toasts().shown().isEmpty()); frame++) {
            for (HudToast shown : model.toasts().shown()) {
                played.add(shown.title());
            }
            clock.advance(16L);
            model.expire(clock.millis);
        }
        assertEquals(6, played.size());
    }

    @Test
    void theEditorSampleShowsNoMoreCardsThanTheWidgetAllows() {
        HudModel sample = HudSamples.editorModel();
        HudRenderer renderer = new HudRenderer(sample, TEXT);
        renderer.renderEditing(new HudFakes.Draw(960, 540, 2.0, false), layout(HudAnchor.TOP_CENTER, 1, false), null);
        assertEquals(1, sample.toasts().shown().size());
        renderer.renderEditing(new HudFakes.Draw(960, 540, 2.0, false), layout(HudAnchor.TOP_CENTER, 3, false), null);
        assertEquals(3, sample.toasts().shown().size());
        renderer.renderEditing(new HudFakes.Draw(960, 540, 2.0, false), layout(HudAnchor.TOP_CENTER, 3, false), null);
        HudToast first = sample.toasts().shown().get(0);
        assertTrue(sample.toasts().shown().get(1).offset() >= first.offset() + first.height() + HudToastQueue.GAP);
    }
}
