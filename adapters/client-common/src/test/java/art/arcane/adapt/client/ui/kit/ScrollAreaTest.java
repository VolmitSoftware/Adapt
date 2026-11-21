package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiScrollEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollAreaTest {
    private final KitDraw draw = new KitDraw();

    private ScrollArea area(int lines, int height) {
        UiGroup content = new UiGroup("content", UiLayoutPolicy.column(1));
        for (int index = 0; index < lines; index++) {
            content.add(new Button("b" + index, "Item " + index, () -> {
            }));
        }
        ScrollArea area = new ScrollArea("scroll", content);
        area.place(new UiRect(0, 10, 100, height), draw);
        return area;
    }

    @Test
    void scrollIsClampedToTheContent() {
        ScrollArea area = area(10, 100);
        assertEquals(149, area.contentHeight());
        assertEquals(49, area.maxScroll());
        area.setScroll(-20);
        assertEquals(0, area.scroll());
        area.setScroll(500);
        assertEquals(49, area.scroll());
        assertEquals(10 - 49, area.content().bounds().y());
    }

    @Test
    void contentThatFitsDoesNotScrollOrShowABar() {
        ScrollArea area = area(2, 100);
        assertEquals(0, area.maxScroll());
        assertFalse(area.scrollbarShown());
        assertFalse(area.mouseScrolled(new UiScrollEvent(5, 15, 0, -3)));
        assertEquals(100, area.content().bounds().width());
    }

    @Test
    void overflowingContentLeavesRoomForTheBar() {
        ScrollArea area = area(10, 100);
        assertEquals(100 - ScrollArea.BAR_WIDTH - 1, area.content().bounds().width());
    }

    @Test
    void wheelAndKeysMoveTheContent() {
        ScrollArea area = area(10, 100);
        assertTrue(area.mouseScrolled(new UiScrollEvent(5, 15, 0, -2)));
        assertEquals(36, area.scroll());
        area.keyPressed(new UiKeyEvent(UiKey.PAGE_DOWN, UiModifiers.NONE));
        assertEquals(49, area.scroll());
        area.keyPressed(new UiKeyEvent(UiKey.HOME, UiModifiers.NONE));
        assertEquals(0, area.scroll());
        area.keyPressed(new UiKeyEvent(UiKey.END, UiModifiers.NONE));
        assertEquals(49, area.scroll());
    }

    @Test
    void scrollingKeepsLayoutButMovesChildren() {
        ScrollArea area = area(10, 100);
        UiNode third = area.content().children().get(2);
        int before = third.bounds().y();
        area.setScroll(30);
        assertEquals(before - 30, third.bounds().y());
        area.place(area.bounds(), draw);
        assertEquals(30, area.scroll());
        assertEquals(before - 30, third.bounds().y());
    }

    @Test
    void revealScrollsJustEnough() {
        ScrollArea area = area(10, 100);
        UiNode last = area.content().children().get(9);
        area.reveal(last.bounds());
        assertEquals(49, area.scroll());
        UiNode first = area.content().children().get(0);
        area.reveal(first.bounds());
        assertEquals(0, area.scroll());
    }

    @Test
    void draggingTheThumbScrollsProportionally() {
        ScrollArea area = area(10, 100);
        UiRect thumb = area.thumb();
        assertEquals(67, thumb.height());
        assertTrue(area.mousePressed(TestScreen.left(97, thumb.y() + 5)));
        area.mouseDragged(TestScreen.left(97, thumb.y() + 5 + 53), 0, 53);
        assertEquals(49, area.scroll());
        area.mouseReleased(TestScreen.left(97, 0));
        assertFalse(area.mouseDragged(TestScreen.left(97, 10), 0, 0));
        assertFalse(area.mousePressed(TestScreen.left(20, 20)));
    }
}
