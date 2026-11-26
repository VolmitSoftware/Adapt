package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.ClientFrame;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerInfo;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationWords;
import art.arcane.adapt.client.view.ToolView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutateScreenTest {
    private static final int SPREAD = ClientProtocol.SCREENS | ClientProtocol.PANEL | ClientProtocol.MUTATE | ClientProtocol.MUTATE_SPREAD;
    private static final IconRef ICON = IconRef.item("minecraft:feather");
    private static final MutationWords WORDS = new MutationWords("Mutate", "Lead", "Partner", "Gains", "Loses", "Hold to mutate",
            "This cannot be undone", "Swap lead", "Level {level}");

    @Test
    void theSkillPageOpensTheSpreadWhenTheServerHasOne() {
        ScreenFixture fixture = fixture();
        fixture.state.acceptMutateOffers(List.of("agility"));
        fixture.open(new Route.Skill("agility"));

        fixture.click(assertInstanceOf(Button.class, fixture.find(SkillScreen.MUTATE)));

        MutateScreen screen = assertInstanceOf(MutateScreen.class, fixture.current());
        assertEquals(new Route.Mutate("agility"), screen.route());
        assertEquals(new ClientFrame.MutationRequest("agility", "", false), fixture.sent.getLast());
    }

    @Test
    void ineligibleSkillsAreShownDimmedWithTheirReasonAndCannotBePicked() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = open(fixture);
        answer(fixture, "agility", "");

        UiNode hunter = fixture.find("candidates:hunter");
        assertNotNull(hunter);
        assertEquals("false", described(hunter).get("eligible"));
        assertTrue(hunter.tooltip().lines().stream().anyMatch((String line) -> line.contains("Needs level 15")));
        int sent = fixture.sent.size();
        fixture.click(hunter);
        assertEquals(sent, fixture.sent.size());
        assertEquals("", state(screen).get("partner"));
        assertFalse(fixture.find("hold").enabled());
    }

    @Test
    void pickingAPartnerFillsItsSideAtOnceAndAsksForThePair() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = open(fixture);
        answer(fixture, "agility", "");

        fixture.click(fixture.find("candidates:rift"));

        assertEquals("rift", state(screen).get("partner"));
        assertEquals("Rift", fixture.find("parent:partner").label());
        assertEquals(new ClientFrame.MutationRequest("agility", "rift", false), fixture.sent.getLast());
        assertFalse(fixture.find("hold").enabled());

        answer(fixture, "agility", "rift");
        assertEquals("Swift Rift", fixture.find("banner").label());
        assertTrue(fixture.find("hold").enabled());
        assertEquals("mutated-agility-rift", screen.themeSkill());
        assertNotNull(fixture.find("banner:upgraded-rift"));
    }

    @Test
    void thePairsResultShowsEverythingItGivesOnceAndGainsKeepOnlyTheKeptOriginals() {
        ScreenFixture fixture = fixture();
        open(fixture);
        answer(fixture, "agility", "");
        assertNotNull(fixture.find("gains:lead:upgraded-agility"));
        assertNotNull(fixture.find("gains:lead:tool-agility"));

        fixture.click(fixture.find("candidates:rift"));
        answer(fixture, "agility", "rift");

        Map<String, String> banner = described(fixture.find("banner"));
        assertEquals("5", banner.get("mutated"));
        assertEquals("6", banner.get("upgrades"));
        assertEquals("2", banner.get("tools"));
        for (String id : List.of("mutated-agility-rift-graft", "upgraded-agility", "upgraded-agility-3", "tool-agility", "tool-rift",
                "upgraded-rift", "upgraded-rift-3")) {
            assertTrue(fixture.shows("banner:" + id), id);
        }
        UiRect lead = fixture.find("banner:upgraded-agility").bounds();
        UiRect tool = fixture.find("banner:tool-agility").bounds();
        UiRect partner = fixture.find("banner:upgraded-rift").bounds();
        UiRect own = fixture.find("banner:mutated-agility-rift-graft").bounds();
        assertTrue(lead.y() == tool.y() && tool.y() == partner.y() && lead.y() > own.bottom());
        assertTrue(lead.right() < tool.x() && tool.right() < partner.x());
        fixture.state.acceptTools(List.of(new ToolView("agility-wall-jump", "tool-agility", "Tool of agility", ICON, 1,
                List.of(new ToolView.Ingredient("minecraft:string", 3)))));
        fixture.render();
        assertEquals(List.of("§7Tool", "§73 × String"), fixture.find("banner:tool-agility").tooltip().lines());
        for (String side : List.of("lead", "partner")) {
            assertNotNull(fixture.find("gains:" + side + ":" + (side.equals("lead") ? "agility" : "rift") + "-kept"));
            assertEquals(null, fixture.find("gains:" + side + ":upgraded-" + (side.equals("lead") ? "agility" : "rift")));
            assertEquals(null, fixture.find("gains:" + side + ":tool-" + (side.equals("lead") ? "agility" : "rift")));
        }
    }

    @Test
    void aNarrowResultFoldsToolsIntoACountThatNamesThem() {
        ScreenFixture fixture = fixture(320, 240, 4.0);
        open(fixture);
        answer(fixture, "agility", "");
        fixture.click(fixture.find("candidates:rift"));
        answer(fixture, "agility", "rift");

        UiRect banner = fixture.find("banner").bounds();
        for (UiNode token : fixture.find("banner").children()) {
            assertTrue(token.bounds().x() >= banner.x() && token.bounds().right() <= banner.right(), token.id());
        }
        UiNode more = fixture.find("banner:more");
        int shown = 0;
        for (UiNode token : fixture.find("banner").children()) {
            if (!token.id().startsWith("banner:mutated-") && !token.id().equals("banner:more")) {
                shown++;
            }
        }
        assertEquals(8, shown + (more == null ? 0 : more.tooltip().lines().size()));
    }

    @Test
    void swappingExchangesLeadAndPartnerWithAWispEachWay() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = paired(fixture);

        fixture.click(fixture.find("swap"));

        assertEquals("rift", state(screen).get("lead"));
        assertEquals("agility", state(screen).get("partner"));
        assertEquals(new ClientFrame.MutationRequest("rift", "agility", false), fixture.sent.getLast());
        assertEquals("mutated-rift-agility", screen.themeSkill());
        assertEquals("Rift", fixture.find("parent:lead").label());
        assertFalse(fixture.find("hold").enabled());
        answer(fixture, "rift", "agility");
        assertTrue(fixture.find("hold").enabled());
    }

    @Test
    void holdingForTheWholeTimeConfirmsOnceAndLettingGoEarlyCancels() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = paired(fixture);
        UiNode hold = fixture.find("hold");

        press(fixture, hold);
        fixture.clock.advance(1000L);
        renderOver(fixture, hold);
        release(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);
        assertFalse(confirmed(fixture));

        press(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS - 100L);
        renderOver(fixture, hold);
        assertFalse(confirmed(fixture));
        fixture.clock.advance(100L);
        renderOver(fixture, hold);
        fixture.clock.advance(1000L);
        renderOver(fixture, hold);

        assertEquals(1, fixture.sent.stream().filter(MutateScreenTest::isConfirm).count());
        assertEquals(new ClientFrame.MutationRequest("agility", "rift", true), fixture.sent.getLast());
        assertTrue(screen.pending());
    }

    @Test
    void movingThePointerOffTheControlCancelsAMouseHold() {
        ScreenFixture fixture = fixture();
        paired(fixture);
        UiNode hold = fixture.find("hold");

        press(fixture, hold);
        fixture.clock.advance(500L);
        fixture.render();
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);

        assertFalse(confirmed(fixture));
    }

    @Test
    void theKeyboardHoldsWithEnterAndCancelsOnRelease() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = paired(fixture);
        UiNode hold = fixture.find("hold");
        screen.focus(hold);

        fixture.key(UiKey.ENTER);
        fixture.clock.advance(700L);
        fixture.key(UiKey.ENTER);
        screen.keyReleased(new UiKeyEvent(UiKey.ENTER, UiModifiers.NONE));
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        fixture.render();
        assertFalse(confirmed(fixture));

        fixture.key(UiKey.SPACE);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        fixture.render();
        assertTrue(confirmed(fixture));
    }

    @Test
    void aRefusedConfirmLetsThePlayerHoldAgain() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = paired(fixture);
        holdThrough(fixture, fixture.find("hold"));
        answer(fixture, "agility", "rift");
        screen.feedback(FeedbackKind.FAILURE, "Both skills need level 15.");
        fixture.render();

        assertEquals("0.00", described(fixture.find("hold")).get("fill"));
        assertTrue(fixture.find("hold").enabled());
    }

    @Test
    void aMergeTheServerConfirmsPlaysOutAndOpensTheNewSkill() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = paired(fixture);
        holdThrough(fixture, fixture.find("hold"));

        fixture.state.acceptSpread(view("agility", "", "mutated-agility-rift"));
        fixture.render();
        assertEquals("true", state(screen).get("merging"));
        assertTrue(fixture.current() instanceof MutateScreen);

        fixture.clock.advance(SpreadVeil.MERGE_MILLIS);
        fixture.render();
        assertEquals(new Route.Skill("mutated-agility-rift"), fixture.current().route());
    }

    @Test
    void aSinglePageShowsTheLeadThenThePartnerWithTheNameAndControlsOnBoth() {
        ScreenFixture fixture = fixture(320, 240, 4.0);
        MutateScreen screen = open(fixture);
        answer(fixture, "agility", "");
        fixture.click(fixture.find("candidates:rift"));
        answer(fixture, "agility", "rift");

        assertFalse(screen.spread());
        assertEquals(2, screen.leaves());
        for (int leaf = 0; leaf < 2; leaf++) {
            screen.turnTo(leaf);
            fixture.render();
            assertTrue(fixture.shows("banner"));
            assertTrue(fixture.shows("swap"));
            assertTrue(fixture.shows("hold"));
            assertEquals(leaf == 0, fixture.shows("parent:lead"));
            assertEquals(leaf == 1, fixture.shows("parent:partner"));
        }
    }

    @Test
    void everyGuiScaleFitsTheSpreadOnOneLeafWithNothingPastItsPage() {
        int[][] screens = {{1920, 1080, 1}, {1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}, {1280, 960, 4}, {854, 480, 2},
                {1100, 720, 3}, {700, 480, 2}};
        for (int[] size : screens) {
            ScreenFixture fixture = fixture(size[0] / size[2], size[1] / size[2], size[2]);
            MutateScreen screen = paired(fixture);
            String at = size[0] + "x" + size[1] + " at " + size[2];

            assertEquals(screen.spread() ? 1 : 2, screen.leaves(), at);
            for (int leaf = 0; leaf < screen.leaves(); leaf++) {
                screen.turnTo(leaf);
                fixture.render();
                UiRect banner = fixture.find("banner").bounds();
                UiRect top = SpreadSwap.plated(fixture.find("swap").bounds());
                for (String id : List.of("parent:lead", "gains:lead", "loses:lead", "surplus:lead", "parent:partner", "gains:partner",
                        "loses:partner", "surplus:partner")) {
                    if (!fixture.shows(id)) {
                        continue;
                    }
                    UiRect node = fixture.find(id).bounds();
                    assertTrue(node.y() >= banner.bottom() && node.bottom() <= top.y(), id + " " + at);
                }
            }
        }
    }

    @Test
    void theSwapSitsOnTheSpineAboveTheHoldAtEveryScale() {
        int[][] screens = {{1920, 1080, 2}, {1920, 1080, 3}, {1920, 1080, 4}, {1100, 720, 2}};
        for (int[] size : screens) {
            ScreenFixture fixture = fixture(size[0] / size[2], size[1] / size[2], size[2]);
            MutateScreen screen = paired(fixture);
            String at = size[0] + "x" + size[1] + " at " + size[2];
            assertTrue(screen.spread(), at);

            int fold = (fixture.find("parent:lead").bounds().right() + fixture.find("parent:partner").bounds().x()) / 2;
            UiRect swap = fixture.find("swap").bounds();
            UiRect hold = fixture.find("hold").bounds();
            assertEquals(fold, swap.centerX(), at);
            assertEquals(fold, hold.centerX(), at);
            assertTrue(SpreadSwap.plated(swap).bottom() < hold.y(), at);
            assertTrue(SpreadSwap.plated(swap).width() > AdaptScreen.SPREAD_GAP, "the plate covers the spine " + at);
        }
    }

    @Test
    void theSwapComesBeforeTheHoldInTabOrder() {
        ScreenFixture fixture = fixture();
        MutateScreen screen = paired(fixture);

        screen.focus(fixture.find("swap"));
        fixture.key(UiKey.TAB);

        assertEquals("hold", screen.focused().id());
        screen.keyPressed(new UiKeyEvent(UiKey.TAB, new UiModifiers(true, false, false, false)));
        assertEquals("swap", screen.focused().id());
        assertEquals("Swap lead", fixture.find("swap").label());
        assertEquals(List.of("Swap lead"), List.of(fixture.find("swap").tooltip().title()));
    }

    @Test
    void theHoldSaysItCannotBeUndoneAndKeepsSayingItWhileHeld() {
        ScreenFixture fixture = fixture();
        paired(fixture);
        UiNode hold = fixture.find("hold");
        assertEquals("This cannot be undone", described(hold).get("warning"));
        assertEquals(1, warnings(fixture, () -> renderOver(fixture, hold)));

        press(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS / 2);

        assertTrue(warnings(fixture, () -> renderOver(fixture, hold)) > 0);
        assertEquals("true", described(hold).get("holding"));
    }

    /** How many times one render draws the warning: once at rest, again over each filled end while held. */
    private static long warnings(ScreenFixture fixture, Runnable render) {
        int before = fixture.draw.calls().size();
        render.run();
        return fixture.draw.calls().subList(before, fixture.draw.calls().size()).stream()
                .filter((String call) -> call.startsWith("text ") && call.endsWith(" This cannot be undone"))
                .count();
    }

    private static Map<String, String> state(MutateScreen screen) {
        Map<String, String> state = new LinkedHashMap<>();
        screen.describe(state);
        return state;
    }

    private static Map<String, String> described(UiNode node) {
        Map<String, String> state = new LinkedHashMap<>();
        node.describe(state);
        return state;
    }

    private static boolean isConfirm(ClientFrame frame) {
        return frame instanceof ClientFrame.MutationRequest request && request.confirm();
    }

    private static boolean confirmed(ScreenFixture fixture) {
        return fixture.sent.stream().anyMatch(MutateScreenTest::isConfirm);
    }

    private static void holdThrough(ScreenFixture fixture, UiNode hold) {
        press(fixture, hold);
        fixture.clock.advance(HoldToConfirm.HOLD_MILLIS);
        renderOver(fixture, hold);
        release(fixture, hold);
    }

    private static void press(ScreenFixture fixture, UiNode node) {
        renderOver(fixture, node);
        fixture.current().mouseClicked(mouse(node));
        renderOver(fixture, node);
    }

    private static void release(ScreenFixture fixture, UiNode node) {
        fixture.current().mouseReleased(mouse(node));
        renderOver(fixture, node);
    }

    private static void renderOver(ScreenFixture fixture, UiNode node) {
        fixture.current().render(fixture.draw, node.bounds().centerX(), node.bounds().centerY(), 0.0F);
    }

    private static UiMouseEvent mouse(UiNode node) {
        return new UiMouseEvent(node.bounds().centerX(), node.bounds().centerY(), UiMouseButton.LEFT, UiModifiers.NONE);
    }

    private static ScreenFixture fixture() {
        return fixture(427, 240, 2.0);
    }

    private static ScreenFixture fixture(int width, int height, double scale) {
        ScreenFixture fixture = new ScreenFixture(width, height, scale);
        fixture.state.negotiated(ClientProtocol.VERSION, new ServerInfo("test", "26.3", SPREAD));
        return fixture;
    }

    private static MutateScreen open(ScreenFixture fixture) {
        return assertInstanceOf(MutateScreen.class, fixture.open(new Route.Mutate("agility")));
    }

    private static MutateScreen paired(ScreenFixture fixture) {
        MutateScreen screen = open(fixture);
        answer(fixture, "agility", "");
        fixture.click(fixture.find("candidates:rift"));
        answer(fixture, "agility", "rift");
        return screen;
    }

    private static void answer(ScreenFixture fixture, String lead, String partner) {
        fixture.state.acceptSpread(view(lead, partner, ""));
        fixture.render();
    }

    private static MutationSpreadView view(String lead, String partner, String mutated) {
        boolean paired = !partner.isEmpty();
        List<MutationCandidate> candidates = new ArrayList<>();
        for (String skill : List.of("agility", "rift", "stealth", "hunter", "herbalism")) {
            if (skill.equals(lead)) {
                continue;
            }
            MutationCandidate.State state = switch (skill) {
                case "hunter" -> MutationCandidate.State.LEVEL_TOO_LOW;
                case "herbalism" -> MutationCandidate.State.ALREADY_MUTATED;
                default -> MutationCandidate.State.ELIGIBLE;
            };
            String reason = switch (state) {
                case LEVEL_TOO_LOW -> "Needs level 15";
                case ALREADY_MUTATED -> "Already mutated";
                default -> "";
            };
            candidates.add(new MutationCandidate(skill, 20, state, reason));
        }
        MutationSide leadSide = side(lead, false);
        MutationSide partnerSide = paired ? side(partner, true) : MutationSide.NONE;
        List<MutationEntry> own = new ArrayList<>();
        if (paired) {
            for (String recipe : List.of("graft", "echo", "barter", "attunement", "convergence")) {
                own.add(new MutationEntry("mutated-" + lead + "-" + partner + "-" + recipe, ICON, recipe, "", 0));
            }
        }
        String name = !paired ? "" : lead.equals("agility") ? "Swift Rift" : "Warped Runner";
        return new MutationSpreadView(lead, partner, name, paired ? "Level 20 · 41 knowledge" : "", own, leadSide, partnerSide,
                candidates, WORDS, mutated);
    }

    private static MutationSide side(String skill, boolean higher) {
        List<MutationEntry> upgrades = List.of(new MutationEntry("upgraded-" + skill, ICON, "Upgrade", "Strong.\n13 / 13 / 16 knowledge", 0),
                new MutationEntry("upgraded-" + skill + "-2", ICON, "Upgrade Two", "", 0),
                new MutationEntry("upgraded-" + skill + "-3", ICON, "Upgrade Three", "", 0));
        List<MutationEntry> kept = List.of(new MutationEntry(skill + "-kept", ICON, "Kept I", "", 1));
        List<MutationEntry> lost = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            lost.add(new MutationEntry(skill + "-lost-" + index, ICON, "Lost " + index, "", index == 0 ? 2 : 0));
        }
        List<MutationEntry> tools = List.of(new MutationEntry("tool-" + skill, ICON, "Tool of " + skill, "Tool", 0));
        return new MutationSide(skill, 20, upgrades, kept, tools, lost, higher ? "5 levels · 12 knowledge" : "");
    }
}
