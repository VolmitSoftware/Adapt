package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotCycle;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackFrameTest {
  private static final MenuIcon STONE = MenuIcon.of(Id.minecraft("stone"), "Stone", List.of());
  private static final MenuIcon DIRT = MenuIcon.of(Id.minecraft("dirt"), "Dirt", List.of());
  private static final MenuIcon SAND = MenuIcon.of(Id.minecraft("sand"), "Sand", List.of());
  private static final SlotLook LOOK = SlotLook.of(Id.adapt("ui/card"), "adapt:parchment", 0);

  @Test
  void chestAndLowerButtonsLandOnWindowSlotsWithTheirLooks() {
    ChestMenu menu = ChestMenu.builder("Test", 3).set(4, MenuButton.display(STONE)).build();
    PackSurface surface = PackSurface.builder()
        .lower(0, MenuButton.display(DIRT))
        .lower(27, MenuButton.display(SAND))
        .look(PackSlot.top(4), LOOK)
        .build();

    Map<Integer, PackFrame.Shown> frame = PackFrame.of(menu, surface, true, 0L);

    assertThat(frame).containsOnlyKeys(4, 27, 54);
    assertThat(frame.get(4)).isEqualTo(new PackFrame.Shown(STONE, LOOK));
    assertThat(frame.get(27).icon()).isEqualTo(DIRT);
    assertThat(frame.get(54).icon()).isEqualTo(SAND);
  }

  @Test
  void lowerButtonsAndLowerCyclesStayOffWindowsWithoutPlayerSlots() {
    ChestMenu menu = ChestMenu.builder("Test", 1).build();
    PackSurface surface = PackSurface.builder()
        .lower(3, MenuButton.display(DIRT))
        .cycle(new SlotCycle(PackSlot.lower(4), 5, List.of(STONE, DIRT)))
        .build();

    assertThat(PackFrame.of(menu, surface, false, 0L)).isEmpty();
    assertThat(PackFrame.of(menu, surface, true, 0L)).containsOnlyKeys(12, 13);
  }

  @Test
  void cyclesStepByPeriodAndOverrideTheButtonIcon() {
    ChestMenu menu = ChestMenu.builder("Test", 1).set(2, MenuButton.display(SAND)).build();
    PackSurface surface = PackSurface.builder().cycle(new SlotCycle(PackSlot.top(2), 4, List.of(STONE, DIRT))).build();

    assertThat(PackFrame.of(menu, surface, true, 0L).get(2).icon()).isEqualTo(STONE);
    assertThat(PackFrame.of(menu, surface, true, 3L).get(2).icon()).isEqualTo(STONE);
    assertThat(PackFrame.of(menu, surface, true, 4L).get(2).icon()).isEqualTo(DIRT);
    assertThat(PackFrame.of(menu, surface, true, 8L).get(2).icon()).isEqualTo(STONE);
  }

  @Test
  void topCyclesOutsideTheChestAreIgnored() {
    ChestMenu menu = ChestMenu.builder("Test", 1).build();
    PackSurface surface = PackSurface.builder().cycle(new SlotCycle(PackSlot.top(20), 4, List.of(STONE, DIRT))).build();

    assertThat(PackFrame.of(menu, surface, true, 0L)).isEmpty();
  }

  @Test
  void diffListsChangedAddedAndRemovedSlotsOnly() {
    Map<Integer, PackFrame.Shown> before = Map.of(
        1, new PackFrame.Shown(STONE, null),
        2, new PackFrame.Shown(DIRT, null),
        3, new PackFrame.Shown(SAND, LOOK));
    Map<Integer, PackFrame.Shown> after = Map.of(
        1, new PackFrame.Shown(STONE, null),
        3, new PackFrame.Shown(SAND, null),
        9, new PackFrame.Shown(DIRT, null));

    assertThat(PackFrame.changed(before, after)).containsExactly(2, 3, 9);
    assertThat(PackFrame.changed(after, after)).isEmpty();
  }

  @Test
  void nextFrameIsTheSoonestCycleBoundary() {
    List<SlotCycle> cycles = List.of(
        new SlotCycle(PackSlot.top(0), 10, List.of(STONE, DIRT)),
        new SlotCycle(PackSlot.top(1), 4, List.of(STONE, DIRT)));

    assertThat(PackFrame.untilNextFrame(cycles, 0L)).isEqualTo(4L);
    assertThat(PackFrame.untilNextFrame(cycles, 9L)).isEqualTo(1L);
    assertThat(PackFrame.untilNextFrame(cycles, 12L)).isEqualTo(4L);
    assertThat(PackFrame.untilNextFrame(List.of(), 7L)).isEqualTo(-1L);
  }

  @Test
  void canvasIsChosenUnlessTheSurfaceOrMenuNeedsTheLiveInventory() {
    ChestMenu plain = ChestMenu.builder("Test", 6).build();
    ChestMenu free = ChestMenu.builder("Test", 6).freePlayerInventory(true).build();
    PackSurface full = PackSurface.builder().title(List.of(new TitleRun("adapt:page", "a", TitleRun.PLAIN))).build();
    PackSurface topOnly = PackSurface.builder().topRowsOnly(true).build();

    assertThat(PackFrame.wantsCanvas(plain, full)).isTrue();
    assertThat(PackFrame.wantsCanvas(plain, topOnly)).isFalse();
    assertThat(PackFrame.wantsCanvas(free, full)).isFalse();
  }

  @Test
  void droppingLowerButtonsKeepsTitleLooksAndCycles() {
    PackSurface surface = PackSurface.builder()
        .title(List.of(new TitleRun("adapt:page", "a", TitleRun.PLAIN)))
        .lower(1, MenuButton.display(DIRT))
        .look(PackSlot.top(0), LOOK)
        .cycle(new SlotCycle(PackSlot.top(0), 4, List.of(STONE, DIRT)))
        .build();

    PackSurface stripped = PackFrame.withoutLower(surface);

    assertThat(stripped.lower()).isEmpty();
    assertThat(stripped.title()).isEqualTo(surface.title());
    assertThat(stripped.looks()).isEqualTo(surface.looks());
    assertThat(stripped.cycles()).isEqualTo(surface.cycles());
  }

  @Test
  void freeInventoryMenusRefuseLowerButtons() {
    ChestMenu free = ChestMenu.builder("Test", 2).freePlayerInventory(true).build();
    PackSurface lower = PackSurface.builder().lower(0, MenuButton.display(DIRT)).build();

    assertThatThrownBy(() -> PackFrame.check(free, lower)).isInstanceOf(IllegalArgumentException.class);
    PackFrame.check(free, PackSurface.builder().build());
  }
}
