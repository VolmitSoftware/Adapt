package art.arcane.adapt.api.mutation.tool;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.Nullable;

/**
 * One use of a tool in the main hand by a player whose owner level reaches the tool's required level. {@code action}
 * is the click; {@code block} and {@code face} are set for a block click, {@code entity} for a right-click on an
 * entity (then {@code action} is {@link InteractAction#RIGHT_CLICK_AIR}). {@code item} is the live stack in the hand.
 */
public record ToolUse(PlayerRef player, ItemRef item, int level, InteractAction action, @Nullable BlockRef block,
                      @Nullable BlockFace face, @Nullable EntityRef entity) {
  public boolean rightClick() {
    return action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK;
  }

  public boolean leftClick() {
    return action == InteractAction.LEFT_CLICK_AIR || action == InteractAction.LEFT_CLICK_BLOCK;
  }

  public boolean sneaking() {
    return player.isSneaking();
  }
}
