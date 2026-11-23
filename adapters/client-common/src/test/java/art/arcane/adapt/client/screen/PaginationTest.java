package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.UiNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a flow of whole blocks fills the book's pages. */
class PaginationTest {
    private static final UiDraw DRAW = new KitDraw();
    private static final int GAP = AdaptScreen.PAGE_GAP;

    private static final class Box extends UiNode {
        private final int height;

        private Box(String id, int height) {
            super(id);
            this.height = height;
        }

        @Override
        public String kind() {
            return "box";
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return height;
        }
    }

    private static Pagination.Poured pour(AdaptScreen.Flow flow, int height, int stretch) {
        return Pagination.pour(flow, 100, height, DRAW, stretch, flow.spread() ? 2 : 1);
    }

    private static List<String> ids(List<AdaptScreen.Placed> page) {
        List<String> ids = new ArrayList<>();
        for (AdaptScreen.Placed placed : page) {
            ids.add(placed.node().id() + "@" + placed.y() + "+" + placed.height());
        }
        return ids;
    }

    @Test
    void blocksFillAPageAndTheOneThatDoesNotFitStartsTheNext() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 50, 0);
        for (int index = 0; index < 4; index++) {
            flow.add(new Box("b" + index, 20));
        }
        List<List<AdaptScreen.Placed>> pages = pour(flow, 50, 0).pages();
        assertEquals(List.of(List.of("b0@0+20", "b1@" + (20 + GAP) + "+20"), List.of("b2@0+20", "b3@" + (20 + GAP) + "+20")),
                List.of(ids(pages.get(0)), ids(pages.get(1))));
    }

    @Test
    void aHeadingNeverEndsAPage() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 50, 0);
        flow.add(new Box("body", 30));
        flow.heading(new Box("heading", 9));
        flow.add(new Box("entry", 9));
        List<List<AdaptScreen.Placed>> pages = pour(flow, 50, 0).pages();
        assertEquals(List.of("body@0+30"), ids(pages.get(0)));
        assertEquals(List.of("heading@0+9", "entry@" + (9 + GAP) + "+9"), ids(pages.get(1)));
    }

    @Test
    void aRuleStaysWithWhatFollowsAndNeverOpensAPage() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 50, 0);
        flow.add(new Box("body", 30));
        flow.rule(new Box("rule", 1), 4);
        flow.add(new Box("entry", 20));
        List<List<AdaptScreen.Placed>> pages = pour(flow, 50, 0).pages();
        assertEquals(List.of("body@0+30"), ids(pages.get(0)));
        assertEquals(List.of("entry@0+20"), ids(pages.get(1)));
    }

    @Test
    void aPageBreakStartsANewPageButNeverAnEmptyOne() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 100, 0);
        flow.nextPage();
        flow.add(new Box("a", 10));
        flow.nextPage();
        flow.nextPage();
        flow.add(new Box("b", 10));
        assertEquals(List.of(List.of("a@0+10"), List.of("b@0+10")), List.of(ids(pour(flow, 100, 0).pages().get(0)),
                ids(pour(flow, 100, 0).pages().get(1))));
    }

    @Test
    void footBlocksKeepToTheBottomAndPushContentThatWouldMeetThem() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 100, 0);
        flow.add(new Box("a", 40));
        flow.foot(new Box("reason", 10));
        flow.foot(new Box("actions", 20));
        List<List<AdaptScreen.Placed>> pages = pour(flow, 100, 0).pages();
        assertEquals(List.of("a@0+40", "reason@" + (100 - 20 - GAP - 10) + "+10", "actions@80+20"), ids(pages.get(0)));

        AdaptScreen.Flow full = new AdaptScreen.Flow(false, 100, 0);
        full.foot(new Box("actions", 20));
        full.add(new Box("a", 60));
        full.add(new Box("b", 30));
        pages = pour(full, 100, 0).pages();
        assertEquals(List.of("a@0+60", "actions@80+20"), ids(pages.get(0)));
        assertEquals(List.of("b@0+30"), ids(pages.get(1)));
    }

    @Test
    void runningTextGivesUpHeightDownToItsLeastAndTakesBackWhatThePageLeaves() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 100, 0);
        flow.add(new Box("name", 20));
        flow.text(new Box("description", 200), 27);
        flow.foot(new Box("actions", 20));
        List<List<AdaptScreen.Placed>> pages = pour(flow, 100, 0).pages();
        assertEquals(1, pages.size());
        assertEquals(List.of("name@0+20", "description@" + (20 + GAP) + "+" + (100 - 20 - GAP - 20 - GAP), "actions@80+20"), ids(pages.get(0)));

        AdaptScreen.Flow small = new AdaptScreen.Flow(false, 100, 0);
        small.text(new Box("description", 18), 27);
        assertEquals(List.of("description@0+18"), ids(pour(small, 100, 0).pages().get(0)));
    }

    @Test
    void twoPiecesOfRunningTextShareWhatThePageLeavesALineAtATime() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 100, 0);
        flow.text(new Box("description", 90), 18);
        flow.text(new Box("stats", 40), 18);
        List<List<AdaptScreen.Placed>> pages = pour(flow, 100, 0).pages();
        assertEquals(1, pages.size());
        assertEquals(List.of("description@0+57", "stats@60+40"), ids(pages.get(0)));
    }

    @Test
    void theLastSpreadSharesItsRowsBetweenItsTwoPages() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(true, 100, 0);
        for (int index = 0; index < 5; index++) {
            flow.row(new Box("r" + index, 10), 1);
        }
        flow.balance();
        List<List<AdaptScreen.Placed>> pages = pour(flow, 100, 0).pages();
        assertEquals(List.of(List.of("r0@0+10", "r1@11+10", "r2@22+10"), List.of("r3@0+10", "r4@11+10")),
                List.of(ids(pages.get(0)), ids(pages.get(1))));
    }

    @Test
    void rowsSpreadByOneAmountNoFurtherThanTheFullestPageAndTheLimitAllow() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(false, 50, 0);
        for (int index = 0; index < 6; index++) {
            flow.row(new Box("r" + index, 10), 1);
        }
        List<List<AdaptScreen.Placed>> pages = pour(flow, 50, 8).pages();
        assertEquals(List.of("r0@0+10", "r1@13+10", "r2@26+10", "r3@39+10"), ids(pages.get(0)));
        assertEquals(List.of("r4@0+10", "r5@13+10"), ids(pages.get(1)));

        AdaptScreen.Flow roomy = new AdaptScreen.Flow(false, 500, 0);
        roomy.row(new Box("a", 10), 1);
        roomy.row(new Box("b", 10), 1);
        assertEquals(List.of("a@0+10", "b@19+10"), ids(pour(roomy, 500, 8).pages().get(0)));
    }

    @Test
    void aPinnedFirstPageStaysWhileTheRestTurnsOnTheRight() {
        AdaptScreen.Flow flow = new AdaptScreen.Flow(true, 50, 0);
        flow.pinFirstPage();
        flow.add(new Box("identity", 30));
        flow.nextPage();
        for (int index = 0; index < 3; index++) {
            flow.add(new Box("setting" + index, 20));
        }
        Pagination.Poured poured = pour(flow, 50, 0);
        assertTrue(poured.pinned());
        assertEquals(3, poured.pages().size());
        assertEquals(List.of("identity@0+30"), ids(poured.pages().get(0)));
        assertEquals(List.of("setting0@0+20", "setting1@" + (20 + GAP) + "+20"), ids(poured.pages().get(1)));
        assertEquals(List.of("setting2@0+20"), ids(poured.pages().get(2)));

        AdaptScreen.Flow tall = new AdaptScreen.Flow(true, 50, 0);
        tall.pinFirstPage();
        tall.add(new Box("a", 40));
        tall.add(new Box("b", 40));
        tall.nextPage();
        tall.add(new Box("c", 10));
        assertFalse(pour(tall, 50, 0).pinned(), "a first page that cannot stay on one page runs on instead");
    }
}
