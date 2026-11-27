package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotCycle;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuIcon;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** What every slot of a pack window shows at one moment, by window slot, and the differences between two moments. */
final class PackFrame {
  private PackFrame() {
  }

  /** The icon and look of a slot. */
  record Shown(MenuIcon icon, SlotLook look) {
  }

  /**
   * The slots of {@code menu} and {@code surface} at {@code ticksOpen}: chest buttons, lower buttons when
   * {@code lowerSlots}, cycling slots on their current frame.
   */
  static Map<Integer, Shown> of(ChestMenu menu, PackSurface surface, boolean lowerSlots, long ticksOpen) {
    Map<Integer, Shown> shown = new TreeMap<>();
    for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
      shown.put(entry.getKey(), new Shown(entry.getValue().icon(), surface.look(PackSlot.top(entry.getKey()))));
    }
    if (lowerSlots) {
      for (Map.Entry<Integer, MenuButton> entry : surface.lower().entrySet()) {
        shown.put(menu.size() + entry.getKey(), new Shown(entry.getValue().icon(), surface.look(PackSlot.lower(entry.getKey()))));
      }
    }
    for (SlotCycle cycle : surface.cycles()) {
      if (cycle.slot().area() == PackSlot.Area.LOWER && !lowerSlots) {
        continue;
      }
      int windowSlot = cycle.slot().clickSlot(menu.size());
      if (cycle.slot().area() == PackSlot.Area.TOP && windowSlot >= menu.size()) {
        continue;
      }
      shown.put(windowSlot, new Shown(cycle.frame(ticksOpen), surface.look(cycle.slot())));
    }
    return shown;
  }

  /** Window slots whose content differs between {@code before} and {@code after}. */
  static TreeSet<Integer> changed(Map<Integer, Shown> before, Map<Integer, Shown> after) {
    TreeSet<Integer> slots = new TreeSet<>();
    for (Map.Entry<Integer, Shown> entry : after.entrySet()) {
      if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
        slots.add(entry.getKey());
      }
    }
    for (Integer slot : before.keySet()) {
      if (!after.containsKey(slot)) {
        slots.add(slot);
      }
    }
    return slots;
  }

  /** Ticks from {@code ticksOpen} until any cycle shows its next frame, or -1 when nothing cycles. */
  static long untilNextFrame(Iterable<SlotCycle> cycles, long ticksOpen) {
    long best = -1L;
    for (SlotCycle cycle : cycles) {
      long next = ((ticksOpen / cycle.periodTicks()) + 1L) * cycle.periodTicks() - ticksOpen;
      if (best < 0L || next < best) {
        best = next;
      }
    }
    return best;
  }

  /** The surface as a window without player slots shows it: lower buttons dropped. */
  static PackSurface withoutLower(PackSurface surface) {
    if (surface.lower().isEmpty()) {
      return surface;
    }
    return new PackSurface(surface.title(), Map.of(), surface.looks(), surface.cycles(), surface.topRowsOnly());
  }

  /** True when the menu and surface take a canvas window rather than a plain chest over the live inventory. */
  static boolean wantsCanvas(ChestMenu menu, PackSurface surface) {
    return !surface.topRowsOnly() && !menu.freePlayerInventory();
  }

  static void check(ChestMenu menu, PackSurface surface) {
    if (menu.freePlayerInventory() && !surface.lower().isEmpty()) {
      throw new IllegalArgumentException("a menu with a free player inventory has no lower buttons");
    }
  }
}
