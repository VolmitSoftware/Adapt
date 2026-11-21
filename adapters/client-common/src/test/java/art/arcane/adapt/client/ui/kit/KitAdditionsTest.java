package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.FakeResources;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiModifiers;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitAdditionsTest {
    private final ManualClock clock = new ManualClock();
    private final FakeHost host = new FakeHost(427, 240);
    private final KitDraw draw = new KitDraw();

    private TestScreen show(UiNode node, UiRect bounds) {
        TestScreen screen = new TestScreen(clock.motion(), (UiRect area, UiDraw metrics) -> node.place(bounds, metrics));
        screen.root().add(node);
        host.open(screen);
        clock.advance(KitScreen.OPEN_MILLIS);
        screen.render(draw, 0, 0, 0.0F);
        return screen;
    }

    @Test
    void snackbarShowsForItsLifetimeAndWaitsUntilAnswered() {
        Snackbar snackbar = new Snackbar("message");
        TestScreen screen = show(snackbar, new UiRect(0, 200, 427, 40));
        assertFalse(snackbar.live());
        snackbar.waiting("Learning");
        clock.advance(Snackbar.LIFETIME_MILLIS * 3);
        assertTrue(snackbar.live());
        assertTrue(snackbar.pending());
        draw.calls().clear();
        screen.render(draw, 0, 0, 0.0F);
        assertTrue(draw.calls("text").stream().anyMatch((String call) -> call.contains("Learning.")));
        snackbar.show(Snackbar.Tone.SUCCESS, "Learned");
        assertFalse(snackbar.pending());
        clock.advance(Snackbar.LIFETIME_MILLIS - 1);
        assertTrue(snackbar.live());
        clock.advance(1);
        assertFalse(snackbar.live());
        draw.calls().clear();
        screen.render(draw, 0, 0, 0.0F);
        assertTrue(draw.calls("text").isEmpty());
        assertNull(screen.hit(200, 230));
    }

    @Test
    void snackbarPillStaysInsideItsBounds() {
        Snackbar snackbar = new Snackbar("message");
        TestScreen screen = show(snackbar, new UiRect(10, 100, 60, 20));
        snackbar.show(Snackbar.Tone.FAILURE, "A very long message that cannot fit in sixty units");
        draw.calls().clear();
        screen.render(draw, 0, 0, 0.0F);
        String plate = draw.calls("fill").get(0);
        String[] edges = plate.split(" ")[1].split(",");
        assertTrue(Integer.parseInt(edges[0]) >= 10);
        assertTrue(Integer.parseInt(edges[2]) <= 70);
    }

    @Test
    void breadcrumbsLinkEveryCrumbButTheLast() {
        List<Integer> selected = new ArrayList<>();
        Breadcrumbs crumbs = new Breadcrumbs("crumb", selected::add);
        crumbs.setCrumbs(List.of("Skills", "Rift", "Rift Blink"));
        TestScreen screen = show(crumbs, new UiRect(0, 0, 300, 20));
        UiNode first = crumbs.find("crumb:0");
        UiNode last = crumbs.find("crumb:2");
        assertTrue(first.focusable());
        assertFalse(last.focusable());
        screen.mouseClicked(TestScreen.center(first));
        screen.mouseClicked(TestScreen.center(last));
        screen.focus(crumbs.find("crumb:1"));
        screen.keyPressed(new UiKeyEvent(UiKey.ENTER, UiModifiers.NONE));
        assertEquals(List.of(0, 1), selected);
        assertEquals("Skills / Rift / Rift Blink", crumbs.label());
    }

    @Test
    void breadcrumbsDropWholeEarlierCrumbsInsteadOfCuttingThem() {
        Breadcrumbs crumbs = new Breadcrumbs("crumb", (int index) -> {
        });
        crumbs.setCrumbs(List.of("Skills", "Agility", "Wall Jump"));
        show(crumbs, new UiRect(0, 0, 120, 20));
        UiRect skills = crumbs.find("crumb:0").bounds();
        UiRect agility = crumbs.find("crumb:1").bounds();
        UiRect wallJump = crumbs.find("crumb:2").bounds();
        assertEquals(54, wallJump.width());
        assertTrue(skills.isEmpty());
        assertEquals(42, agility.width());
        assertTrue(wallJump.right() <= 120);
        assertFalse(agility.intersects(wallJump));
        assertEquals(54, crumbs.minimumWidth(new KitDraw()));
    }

    @Test
    void breadcrumbsKeepNodesWhenThePathIsUnchanged() {
        Breadcrumbs crumbs = new Breadcrumbs("crumb", (int index) -> {
        });
        crumbs.setCrumbs(List.of("Skills", "Agility"));
        UiNode node = crumbs.find("crumb:0");
        crumbs.setCrumbs(List.of("Skills", "Agility"));
        assertTrue(node == crumbs.find("crumb:0"));
        crumbs.setCrumbs(List.of("Skills"));
        assertNull(crumbs.find("crumb:1"));
    }

    @Test
    void statChipSizesToIconValueAndCaption() {
        StatChip chip = new StatChip("stat", IconRef.item("minecraft:redstone"), "12", "power");
        assertEquals(4 + 16 + 3 + 12 + 4 + 30 + 4, chip.preferredWidth(draw));
        TestScreen screen = show(chip, new UiRect(0, 0, 73, 16));
        assertTrue(draw.calls("item").get(0).startsWith("item minecraft:redstone"));
        assertTrue(screen.hit(10, 10) == chip);
        assertFalse(chip.focusable());
    }

    @Test
    void iconsUseTheSpriteWhenTheyNameOne() {
        draw.withSprites(new UiSpriteCache(FakeResources.of("adapt:textures/gui/sprite.png", FakeResources.png(32, 32))));
        draw.calls().clear();
        UiIcons.paint(draw, new IconRef("minecraft:paper", 0, null, "adapt:textures/gui/sprite.png"), 4, 4, 32, false);
        UiIcons.paint(draw, IconRef.item("minecraft:paper"), 4, 4, 32, true);
        assertEquals("blit adapt:textures/gui/sprite.png 4,4 32x32", draw.calls().get(0));
        assertTrue(draw.calls().contains("scale 2.0,2.0"));
        assertTrue(draw.calls().contains("item minecraft:paper cmd=0 glint=true 0,0"));
    }

    @Test
    void flowLayoutWrapsWhenALineIsFull() {
        UiGroup group = new UiGroup("flow", UiLayoutPolicy.flow(4));
        List<StatChip> chips = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            chips.add(group.add(new StatChip("chip" + index, null, "12345", "")));
        }
        show(group, new UiRect(0, 0, 100, 60));
        assertEquals(38, chips.get(0).bounds().width());
        assertEquals(0, chips.get(1).bounds().y());
        assertEquals(42, chips.get(1).bounds().x());
        assertEquals(18, chips.get(2).bounds().y());
        assertEquals(0, chips.get(2).bounds().x());
        assertEquals(32, group.preferredHeight(100, draw));
    }
}
