package art.arcane.adapt.pack.surface;

import art.arcane.adapt.platform.menu.MenuButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What a pack window shows beyond its {@code ChestMenu}: the title as font runs (page art, shifts and placed text),
 * buttons for the player slots below the chest, the pack look of any slot, and slots that cycle on a timer.
 * {@code topRowsOnly} marks a surface laid out without the player slots, for windows that cannot take them; such a
 * surface has no lower buttons. A click on lower slot {@code i} reaches its button as slot {@code menu.size() + i}.
 */
public record PackSurface(List<TitleRun> title, Map<Integer, MenuButton> lower, Map<PackSlot, SlotLook> looks,
                          List<SlotCycle> cycles, boolean topRowsOnly) {
  public PackSurface {
    title = List.copyOf(title);
    lower = Collections.unmodifiableMap(new TreeMap<>(lower));
    looks = Collections.unmodifiableMap(new TreeMap<>(looks));
    cycles = List.copyOf(cycles);
    for (Integer index : lower.keySet()) {
      if (index < 0 || index >= PackSlot.LOWER_SLOTS) {
        throw new IllegalArgumentException("lower slot out of range: " + index);
      }
    }
    if (topRowsOnly && !lower.isEmpty()) {
      throw new IllegalArgumentException("a top-rows-only surface has no lower buttons");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The click slot of lower slot {@code lowerIndex} in a window of {@code rows} chest rows. */
  public static int windowSlot(int rows, int lowerIndex) {
    return (rows * 9) + lowerIndex;
  }

  /** The lower slot a click on {@code windowSlot} hit in a window of {@code rows} chest rows, or -1 for a chest slot. */
  public static int lowerIndex(int rows, int windowSlot) {
    int index = windowSlot - (rows * 9);
    return index < 0 || index >= PackSlot.LOWER_SLOTS ? -1 : index;
  }

  /** The title as plain text, runs joined, for logs and tests. */
  public String titleText() {
    StringBuilder out = new StringBuilder();
    for (TitleRun run : title) {
      out.append(run.text());
    }
    return out.toString();
  }

  public SlotLook look(PackSlot slot) {
    return looks.get(slot);
  }

  public static final class Builder {
    private final List<TitleRun> title = new ArrayList<>();
    private final TreeMap<Integer, MenuButton> lower = new TreeMap<>();
    private final TreeMap<PackSlot, SlotLook> looks = new TreeMap<>();
    private final List<SlotCycle> cycles = new ArrayList<>();
    private boolean topRowsOnly;

    private Builder() {
    }

    public Builder title(List<TitleRun> runs) {
      title.clear();
      title.addAll(runs);
      return this;
    }

    public Builder lower(int index, MenuButton button) {
      lower.put(index, Objects.requireNonNull(button, "button"));
      return this;
    }

    public Builder look(PackSlot slot, SlotLook look) {
      looks.put(Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(look, "look"));
      return this;
    }

    public Builder cycle(SlotCycle cycle) {
      cycles.add(Objects.requireNonNull(cycle, "cycle"));
      return this;
    }

    public Builder topRowsOnly(boolean value) {
      topRowsOnly = value;
      return this;
    }

    public PackSurface build() {
      return new PackSurface(title, lower, looks, cycles, topRowsOnly);
    }
  }
}
