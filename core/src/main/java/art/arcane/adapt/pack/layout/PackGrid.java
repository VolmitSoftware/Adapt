package art.arcane.adapt.pack.layout;

import art.arcane.adapt.pack.surface.PackSlot;

import java.util.List;

/**
 * Where slots sit in container pixels: column {@code c} at {@code x + pitch * c}, chest row {@code r} at
 * {@code chestY + pitch * r}, and the four player rows (three inventory rows, then the hotbar) at {@code lowerY}.
 */
public record PackGrid(int width, int height, int x, int pitch, int chestY, List<Integer> lowerY) {
  public PackGrid {
    lowerY = List.copyOf(lowerY);
    if (lowerY.size() != 4) {
      throw new IllegalArgumentException("the player area has four rows");
    }
  }

  public int x(PackSlot slot) {
    return x + (pitch * slot.column());
  }

  public int y(PackSlot slot) {
    return slot.area() == PackSlot.Area.TOP ? chestY + (pitch * slot.row()) : lowerY.get(slot.row());
  }
}
