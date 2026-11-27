package art.arcane.adapt.engine.platform;

import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

public record EngineAdvancementNode(Id key, ItemRef icon, String title, String description, AdvancementFrame frame,
                                    boolean toast, boolean announce, AdvancementVisibility visibility, float x, float y,
                                    int maxProgress, Id background, List<AdvancementNode> children)
    implements AdvancementNode {
  public EngineAdvancementNode {
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
