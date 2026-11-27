package art.arcane.adapt.pack.surface;

import art.arcane.adapt.platform.menu.MenuIcon;

import java.util.List;
import java.util.Objects;

/**
 * A slot whose item the window steps through while it is open: frame {@code (ticksOpen / periodTicks) % frames}.
 * The slot's button and click handler stay as they are; only the shown item changes.
 */
public record SlotCycle(PackSlot slot, int periodTicks, List<MenuIcon> frames) {
  public SlotCycle {
    Objects.requireNonNull(slot, "slot");
    frames = List.copyOf(frames);
    if (periodTicks < 1 || frames.size() < 2) {
      throw new IllegalArgumentException("a cycle needs a positive period and two frames");
    }
  }

  public MenuIcon frame(long ticksOpen) {
    return frames.get((int) ((ticksOpen / periodTicks) % frames.size()));
  }
}
