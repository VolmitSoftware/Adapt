package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastWidgetTest {
    private static final HudText TEXT = new HudText((String key) -> HudFakes.ENGLISH.getOrDefault(key, key));
    private static final int PAPER_RGB = HudPaint.PAPER & 0xFFFFFF;
    private static final int CHALLENGE_RGB = ToastLook.CHALLENGE_PAPER & 0xFFFFFF;
    private static final int[][] WINDOWS = {{1920, 1080}, {1280, 720}, {854, 480}, {640, 480}, {2560, 1440}};
    private static final Map<String, String[]> DESCRIPTIONS = Map.of(
            "en", new String[] {"Agility", HudSamples.LONG_TOAST},
            "de", new String[] {"Chronos", "Ziehe die Uhr des Universums auf und erlebe den Fluss. Steige auf, indem du dich bewegst, "
                    + "schläfst, den Lauf der Zeit überstehst und Schnelligkeit teilst. Trage eine Uhr für mehr Chronos-Erfahrung; "
                    + "in der Zweithand ist der Bonus am stärksten."},
            "ja", new String[] {"クロノス", "宇宙の時計を巻き、時の流れを体感せよ。移動、睡眠、時の経過を生き延びること、移動速度上昇の共有でレベルが上がる。"
                    + "時計を持ち歩くとChronosの経験値が増え、オフハンドに持つとボーナスが最大になる。"},
            "he", new String[] {"כרונוס", "כוון את שעון היקום, חווה את הזרימה. עלה ברמה על ידי תנועה, שינה, הישרדות לאורך הזמן ושיתוף מהירות. "
                    + "שא שעון כדי לקבל יותר ניסיון Chronos, עם הבונוס החזק ביותר ביד המשנית."});

    private static HudToast toast(String title, String body) {
        return new HudToast(ToastKind.UNLOCK, IconRef.item("minecraft:feather"), title, body, "");
    }

    private static void layout(HudToast toast, int widest, int bodyLines) {
        ToastWidget.layout(new HudFakes.Draw(960, 540, 2.0, false), TEXT, toast, widest, widest, bodyLines);
    }

    private static int twoLineHeight() {
        return ToastWidget.TEXT_TOP + 18 + ToastWidget.BODY_GAP + ToastWidget.TEXT_BOTTOM;
    }

    @Test
    void longDescriptionsWrapWithoutAnEllipsisAtEveryWidest() {
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        for (Map.Entry<String, String[]> language : DESCRIPTIONS.entrySet()) {
            for (int widest = ToastWidget.MIN_WIDTH + 40; widest <= ToastWidget.MAX_WIDTH; widest += 20) {
                String where = language.getKey() + " at " + widest;
                HudToast toast = toast(language.getValue()[0], language.getValue()[1]);
                ToastWidget.layout(draw, TEXT, toast, widest, 640, ToastWidget.BODY_LINES);
                assertEquals(language.getValue()[1], toast.shownBody(), where);
                assertEquals(language.getValue()[0], toast.shownTitle(), where);
                assertTrue(toast.bodyLines >= 2 && toast.bodyLines <= ToastWidget.BODY_LINES, where + ": " + toast.bodyLines);
                assertTrue(toast.width() >= ToastWidget.MIN_WIDTH && toast.width() <= 640, where + ": " + toast.width());
                assertTrue(toast.width() <= widest || toast.bodyLines == ToastWidget.BODY_LINES, where + ": " + toast.width());
                assertTrue(toast.bodyWrap <= toast.width() - ToastWidget.TEXT_LEFT - ToastWidget.TEXT_RIGHT, where);
                assertEquals(toast.bodyLines * 9, draw.wrappedHeight(toast.shownBody(), toast.bodyWrap), where);
                assertEquals(ToastWidget.TEXT_TOP + toast.lines() * 9 + ToastWidget.BODY_GAP + ToastWidget.TEXT_BOTTOM,
                        toast.cardHeight, where);
            }
        }
    }

    @Test
    void aCardIsAsWideAsItsTextBetweenTheMinimumAndItsWidest() {
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        HudToast shortToast = toast("Agility 14", "+1");
        layout(shortToast, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertEquals(ToastWidget.MIN_WIDTH, shortToast.width());
        assertEquals(twoLineHeight(), shortToast.cardHeight);
        HudToast titleOnly = toast("Agility 14", "");
        layout(titleOnly, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertEquals(ToastWidget.CARD_HEIGHT, titleOnly.cardHeight);
        String title = "Vein Miner learned for Pickaxe";
        HudToast titled = toast(title, "Knowledge +1");
        layout(titled, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertEquals(1, titled.titleLines);
        assertEquals(ToastWidget.TEXT_LEFT + draw.textWidth(title) + ToastWidget.TEXT_RIGHT, titled.width());
        HudToast longToast = toast("Agility", HudSamples.LONG_TOAST);
        layout(longToast, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertTrue(longToast.width() > titled.width() && longToast.width() <= ToastWidget.MAX_WIDTH, Integer.toString(longToast.width()));
        assertTrue(longToast.cardHeight > twoLineHeight());
        HudToast discovery = new HudToast(ToastKind.DISCOVERY, IconRef.item("minecraft:feather"), "Agility 14", "+1", "");
        layout(discovery, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertEquals(twoLineHeight() + ToastWidget.DIVIDER_GAP - ToastWidget.BODY_GAP, discovery.cardHeight);
    }

    @Test
    void aLongTitleTakesTwoLinesOnANarrowCard() {
        String title = "Sprinte einen (buchstäblichen) Marathon!";
        HudToast toast = toast(title, "Sprinte über 42.195 Blöcke!");
        layout(toast, ToastWidget.MIN_WIDTH + 60, ToastWidget.BODY_LINES);
        assertEquals(title, toast.shownTitle());
        assertEquals(2, toast.titleLines);
        HudToast wide = toast(title, "Sprinte über 42.195 Blöcke!");
        layout(wide, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertEquals(1, wide.titleLines);
    }

    @Test
    void onlyTextPastTheLastLineIsCutAndThenAtAWord() {
        String body = (HudSamples.LONG_TOAST + " ").repeat(8).trim();
        HudToast toast = toast("Agility", body);
        layout(toast, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertTrue(toast.width() <= ToastWidget.MAX_WIDTH);
        assertTrue(toast.shownBody().endsWith(HudPaint.ELLIPSIS), toast.shownBody());
        String kept = toast.shownBody().substring(0, toast.shownBody().length() - HudPaint.ELLIPSIS.length());
        assertTrue(body.startsWith(kept));
        assertEquals(' ', body.charAt(kept.length()), kept);
        assertEquals(ToastWidget.BODY_LINES, toast.bodyLines);
        assertEquals("Agility", toast.shownTitle());
        String unspaced = "時計を持ち歩くとChronosの経験値が増え".repeat(20);
        HudToast japanese = toast("クロノス", unspaced);
        layout(japanese, ToastWidget.MAX_WIDTH, ToastWidget.BODY_LINES);
        assertTrue(japanese.shownBody().length() > 100 && japanese.shownBody().endsWith(HudPaint.ELLIPSIS), japanese.shownBody());
        assertEquals(ToastWidget.BODY_LINES, japanese.bodyLines);
    }

    @Test
    void aCardWidensPastItsWidestBeforeCuttingToFewerLines() {
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        HudToast toast = toast("Agility", HudSamples.LONG_TOAST);
        ToastWidget.layout(draw, TEXT, toast, ToastWidget.MIN_WIDTH, 600, 2);
        assertEquals(HudSamples.LONG_TOAST, toast.shownBody());
        assertEquals(2, toast.bodyLines);
        assertTrue(toast.width() > ToastWidget.MIN_WIDTH && toast.width() <= 600, Integer.toString(toast.width()));
        HudToast capped = toast("Agility", HudSamples.LONG_TOAST);
        ToastWidget.layout(draw, TEXT, capped, ToastWidget.MIN_WIDTH, ToastWidget.MIN_WIDTH, 2);
        assertTrue(capped.shownBody().endsWith(HudPaint.ELLIPSIS), capped.shownBody());
        assertEquals(ToastWidget.MIN_WIDTH, capped.width());
    }

    @Test
    void theWidestCardFollowsTheScreenAndStaysOnIt() {
        HudWidget.Frame frame = new HudWidget.Frame();
        frame.unitScale = 1.0;
        int previous = Integer.MAX_VALUE;
        for (int scale = 1; scale <= 4; scale++) {
            frame.screenWidth = 1920.0 / scale;
            int widest = ToastWidget.widest(frame, true);
            assertTrue(widest <= previous, "gui " + scale);
            assertTrue(widest <= frame.screenWidth - 16, "gui " + scale + ": " + widest);
            assertTrue(widest >= ToastWidget.MIN_WIDTH, "gui " + scale + ": " + widest);
            previous = widest;
        }
        frame.screenWidth = 960;
        assertEquals(ToastWidget.MAX_WIDTH, ToastWidget.widest(frame, true));
        frame.screenWidth = 480;
        assertEquals(240, ToastWidget.widest(frame, true));
        frame.screenWidth = 150;
        assertEquals(134, ToastWidget.widest(frame, true));
        assertEquals(134, ToastWidget.stretch(frame, true));
        frame.screenWidth = 960;
        frame.unitScale = 2.0;
        assertEquals(240, ToastWidget.widest(frame, true));
    }

    @Test
    void cardsFitTheWindowAtEveryGuiScale() {
        for (int[] window : WINDOWS) {
            for (int scale = 1; scale <= 4; scale++) {
                HudFakes.Clock clock = new HudFakes.Clock();
                HudModel model = new HudModel(clock);
                model.toast(new ServerFrame.Toast(ToastKind.DISCOVERY, IconRef.item("minecraft:feather"), "Agility", HudSamples.LONG_TOAST, ""));
                model.toast(new ServerFrame.Toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:feather"), "Agility level 14", "", ""));
                HudRenderer renderer = new HudRenderer(model, TEXT);
                HudFakes.Draw draw = HudFakes.Draw.window(window[0], window[1], scale);
                String where = window[0] + "x" + window[1] + " gui " + scale;
                for (int frame = 0; frame < 30; frame++) {
                    renderer.render(draw, HudDefaults.layout());
                    clock.advance(16L);
                }
                HudBox box = renderer.placed("toasts").box();
                assertTrue(box.x >= 0.0 && box.x + box.width <= draw.width() + 1e-6, where + " " + box.rect());
                assertTrue(box.y >= 0.0 && box.y + box.height <= draw.height() + 1e-6, where + " " + box.rect());
                if (renderer.placed("toasts").compact()) {
                    continue;
                }
                HudToast shown = model.toasts().shown().get(0);
                boolean whole = HudSamples.LONG_TOAST.equals(shown.shownBody());
                assertTrue(whole || shown.bodyLines == shown.laidOutLines && shown.laidOutLines < ToastWidget.BODY_LINES
                        && shown.shownBody().endsWith(HudPaint.ELLIPSIS), where + ": " + shown.shownBody());
                assertTrue(whole || draw.height() < HudFit.FULL_HEIGHT, where);
                assertTrue(shown.width() <= draw.width() - 16, where + ": " + shown.width());
            }
        }
    }

    @Test
    void stackedCardsOfMixedHeightsNeverOverlapAtAnyAnchor() {
        for (HudAnchor anchor : HudAnchor.values()) {
            HudFakes.Clock clock = new HudFakes.Clock();
            HudModel model = new HudModel(clock);
            model.toast(new ServerFrame.Toast(ToastKind.DISCOVERY, IconRef.item("minecraft:feather"), "Agility", HudSamples.LONG_TOAST, ""));
            model.toast(new ServerFrame.Toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:iron_pickaxe"), "Pickaxe level 3", "", ""));
            model.toast(new ServerFrame.Toast(ToastKind.CHALLENGE, IconRef.item("minecraft:clock"),
                    "Sprinte einen (buchstäblichen) Marathon!", "", ""));
            HudLayout layout = layout(anchor, 3);
            HudRenderer renderer = new HudRenderer(model, TEXT);
            HudFakes.Draw draw = null;
            for (int frame = 0; frame < 60; frame++) {
                draw = new HudFakes.Draw(960, 540, 2.0, true);
                renderer.render(draw, layout);
                clock.advance(16L);
            }
            assertNotNull(draw);
            assertEquals(3, model.toasts().shown().size(), anchor.name());
            List<int[]> cards = papers(draw);
            assertEquals(3, cards.size(), anchor.name());
            List<Integer> heights = new ArrayList<>();
            for (int first = 0; first < cards.size(); first++) {
                heights.add(cards.get(first)[3]);
                for (int second = first + 1; second < cards.size(); second++) {
                    assertFalse(overlap(cards.get(first), cards.get(second)), anchor.name());
                }
            }
            assertEquals(3, heights.stream().distinct().count(), anchor.name() + " " + heights);
            HudBox box = renderer.placed("toasts").box();
            for (int[] card : cards) {
                assertTrue(card[1] >= 0 && card[1] + card[3] <= Math.round(box.height / box.scale), anchor.name());
                assertTrue(card[0] >= 0 && card[0] + card[2] <= Math.round(box.width / box.scale), anchor.name());
            }
            int oldest = cards.get(0)[1];
            int newest = cards.get(2)[1];
            assertTrue(anchor.bottom() ? oldest > newest : oldest < newest, anchor.name() + " " + oldest + " " + newest);
            assertTrue(box.y >= 0.0 && box.y + box.height <= 540.0, anchor.name());
        }
    }

    @Test
    void toastsStepClearOfSkillRows() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        for (String skill : new String[] {"agility", "pickaxe", "swords", "herbalism"}) {
            model.xp(new ServerFrame.XpEarned(skill, 12.0, 1000.0, 14, 0.5));
        }
        model.toast(new ServerFrame.Toast(ToastKind.DISCOVERY, IconRef.item("minecraft:feather"), "Agility", HudSamples.LONG_TOAST, ""));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = HudFakes.Draw.window(1920, 1080, 4);
        renderer.render(draw, HudDefaults.layout());
        clock.advance(HudToastQueue.ENTER_MILLIS);
        renderer.render(draw, HudDefaults.layout());
        HudBox toasts = renderer.placed("toasts").box();
        HudBox skills = renderer.placed("skills").box();
        assertTrue(renderer.placed("skills").drawn());
        assertFalse(HudVanilla.hit(toasts.x, toasts.y, toasts.width, toasts.height, skills.x, skills.y, skills.width, skills.height),
                toasts.rect() + " " + skills.rect());
        assertTrue(toasts.y + toasts.height <= draw.height());
    }

    @Test
    void aCardSlidesInFromItsEdgeAndHoldsStillWithReducedMotion() {
        for (boolean reduced : new boolean[] {false, true}) {
            HudFakes.Clock clock = new HudFakes.Clock();
            HudModel model = new HudModel(clock);
            model.toast(new ServerFrame.Toast(ToastKind.UNLOCK, IconRef.item("minecraft:bow"), "Quick Draw", "", ""));
            HudRenderer renderer = new HudRenderer(model, TEXT);
            HudLayout layout = layout(HudAnchor.TOP_LEFT, 3);
            layout = new HudLayout(layout.active(), reduced, layout.profiles());
            renderer.render(new HudFakes.Draw(960, 540, 2.0, true), layout);
            clock.advance(HudToastQueue.ENTER_MILLIS / 3);
            HudFakes.Draw middle = new HudFakes.Draw(960, 540, 2.0, true);
            renderer.render(middle, layout);
            clock.advance(HudToastQueue.ENTER_MILLIS);
            HudFakes.Draw settled = new HudFakes.Draw(960, 540, 2.0, true);
            renderer.render(settled, layout);
            int midX = papers(middle).get(0)[0];
            int restX = papers(settled).get(0)[0];
            assertTrue(reduced ? midX == restX : midX < restX, "reduced " + reduced + ": " + midX + " " + restX);
        }
    }

    @Test
    void theDurationDefaultsToThreeSecondsWithinItsRange() {
        HudOptions.Toasts low = new HudOptions.Toasts(0L, 1);
        assertEquals(HudOptions.Toasts.MIN_DURATION, low.durationMillis());
        HudOptions.Toasts high = new HudOptions.Toasts(60_000L, 1);
        assertEquals(HudOptions.Toasts.MAX_DURATION, high.durationMillis());
        HudOptions.Toasts defaults = (HudOptions.Toasts) HudDefaults.options(HudWidgetKind.TOASTS);
        assertEquals(3000L, defaults.durationMillis());
        assertEquals(1, defaults.maxVisible());
    }

    @Test
    void theEditorPreviewShowsTheDiscoveryQuipWhole() {
        HudModel sample = HudSamples.editorModel();
        HudRenderer renderer = new HudRenderer(sample, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, true);
        renderer.renderEditing(draw, HudDefaults.layout(), null);
        HudToast shown = sample.toasts().shown().get(0);
        assertEquals(ToastKind.DISCOVERY, shown.kind());
        assertEquals(HudSamples.DISCOVERY_QUIP, shown.shownBody());
        assertTrue(draw.texts().contains(HudSamples.DISCOVERY_QUIP), draw.texts().toString());
    }

    static HudLayout layout(HudAnchor anchor, int maxVisible) {
        HudProfile profile = HudDefaults.layout().activeProfile();
        List<HudWidgetConfig> widgets = new ArrayList<>();
        for (HudWidgetConfig config : profile.widgets()) {
            if (config.kind() == HudWidgetKind.TOASTS) {
                double offsetX = anchor.column() == 0 ? 4 : anchor.column() == 2 ? -4 : 0;
                double offsetY = anchor.row() == 0 ? 54 : anchor.row() == 2 ? -40 : 0;
                widgets.add(new HudWidgetConfig(config.id(), config.kind(), config.placement().withAnchor(anchor, offsetX, offsetY),
                        new HudOptions.Toasts(3000L, maxVisible)));
            } else {
                widgets.add(config);
            }
        }
        HudProfile edited = new HudProfile(profile.name(), profile.scale(), profile.autoFit(), profile.protect(), widgets);
        return new HudLayout(edited.name(), false, List.of(edited));
    }

    /** Parchment card or pill rectangles drawn, as x, y, width, height in the widget's units, in drawing order. */
    static List<int[]> papers(HudFakes.Draw draw) {
        List<int[]> rects = new ArrayList<>();
        for (String call : draw.calls) {
            if (!call.startsWith("fill ")) {
                continue;
            }
            int space = call.indexOf(' ', 5);
            int rgb = (int) (Long.parseLong(call.substring(space + 1), 16) & 0xFFFFFF);
            if (rgb != PAPER_RGB && rgb != CHALLENGE_RGB) {
                continue;
            }
            String[] edges = call.substring(5, space).split(",");
            int left = Integer.parseInt(edges[0]) - 1;
            int top = Integer.parseInt(edges[1]) - 1;
            rects.add(new int[] {left, top, Integer.parseInt(edges[2]) + 1 - left, Integer.parseInt(edges[3]) + 1 - top});
        }
        return rects;
    }

    static boolean overlap(int[] first, int[] second) {
        return first[0] < second[0] + second[2] && second[0] < first[0] + first[2]
                && first[1] < second[1] + second[3] && second[1] < first[1] + first[3];
    }
}
