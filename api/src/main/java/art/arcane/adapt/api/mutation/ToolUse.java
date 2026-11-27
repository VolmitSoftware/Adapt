package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * One click with a {@link MutationTool}.
 *
 * @param player the player who clicked
 * @param item   the tool stack clicked with
 * @param level  the player's level in the adaptation that owns the tool
 * @param action which button was clicked, at the air or at a block
 * @param block  the clicked block, when there is one
 * @param face   the clicked face of that block, when there is one
 * @param entity the clicked entity, when there is one
 */
@ApiStatus.Experimental
public record ToolUse(PlayerRef player, ItemRef item, int level, InteractAction action, @Nullable BlockRef block,
                      @Nullable BlockFace face, @Nullable EntityRef entity) {
  /** Whether the click was a right click, at the air or at a block. */
  public boolean rightClick() {
    return action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK;
  }

  /** Whether the click was a left click, at the air or at a block. */
  public boolean leftClick() {
    return action == InteractAction.LEFT_CLICK_AIR || action == InteractAction.LEFT_CLICK_BLOCK;
  }

  /** Whether the player is sneaking at the time of the call. */
  public boolean sneaking() {
    return player.isSneaking();
  }
}
