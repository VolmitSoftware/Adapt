package art.arcane.adapt.platform.advancement;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * One advancement in a tab tree. A root node (no parent) has a {@link #background()} texture id. Title and description
 * use Adapt markup. Adapt creates nodes; new accessors may be added.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AdvancementNode {
  /** The advancement id. */
  Id key();

  /** Item shown in the frame. */
  ItemRef icon();

  /** Title text, never null. */
  String title();

  /** Description text, never null. */
  String description();

  /** Frame shape. */
  AdvancementFrame frame();

  /** Whether granting shows a toast. */
  boolean toast();

  /** Whether granting is announced in chat. */
  boolean announce();

  /** When the advancement shows before it is granted. */
  AdvancementVisibility visibility();

  /** Horizontal position in the tab, in grid units. */
  float x();

  /** Vertical position in the tab, in grid units. */
  float y();

  /** Counter target, at least 1; above 1 the advancement is a counter that completes at that value. */
  int maxProgress();

  /** Background texture id of a root node, or null. */
  Id background();

  /** Child nodes, never null. */
  List<AdvancementNode> children();
}
