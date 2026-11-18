package art.arcane.adapt.platform.advancement;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

/**
 * One advancement in a tab tree. A root node (no parent) needs a {@code background} texture id.
 * {@code maxProgress} above 1 makes the advancement a counter that completes at that value.
 * Title and description use Adapt markup. Position is in tab grid units.
 */
public record AdvancementNode(Id key, ItemRef icon, String title, String description, AdvancementFrame frame,
                              boolean toast, boolean announce, AdvancementVisibility visibility,
                              float x, float y, int maxProgress, Id background, List<AdvancementNode> children) {
  public AdvancementNode {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(visibility, "visibility");
    title = title == null ? "" : title;
    description = description == null ? "" : description;
    children = children == null ? List.of() : List.copyOf(children);
    if (maxProgress < 1) {
      throw new IllegalArgumentException("maxProgress must be >= 1: " + maxProgress);
    }
  }
}
