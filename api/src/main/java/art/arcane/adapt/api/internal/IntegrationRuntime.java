package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.integration.HiddenVein;
import art.arcane.adapt.api.integration.PluginContainer;
import art.arcane.adapt.api.integration.TreeFelling;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** What the engine answers about other plugins: NPCs, shields, markers, input, teleports, hidden ores and tree felling. */
@ApiStatus.Internal
public interface IntegrationRuntime {
  boolean isServerPlayer(PlayerRef player);

  boolean isShielded(EntityRef entity);

  boolean marked(EntityRef entity, String marker);

  @Nullable PlayerInput input(PlayerRef player);

  /** The settled destination, or null when a listener cancelled the teleport. */
  @Nullable Loc announceTeleport(PlayerRef player, Adaptation<?> source, Loc from, Loc to);

  List<HiddenVein> hiddenVeins(Loc origin, int radius);

  List<BlockRef> hiddenVeinSiblings(BlockRef block);

  boolean treeFellingAvailable();

  boolean isTreeBlock(BlockRef block);

  boolean isFellingBreak(BlockBreakEvent event);

  boolean fellTree(BlockBreakEvent event, int durabilityPreservationChance, TreeFelling felling);

  PluginContainer pluginContainer(Loc block);

  @Nullable InventoryRef openPluginContainer(PlayerRef player, Loc block);

  void excludeFromStacking(EntityRef entity);

  void includeInStacking(EntityRef entity);

  boolean isSuperStack(ItemRef stack);

  List<ItemRef> superStackItems(ItemRef stack);

  @Nullable ItemRef withSuperStackItems(ItemRef stack, List<ItemRef> items);

  boolean glossAvailable();

  boolean glossOverlayActive(LivingRef entity);

  boolean glossShow(PlayerRef viewer, LivingRef target, List<String> lines, long durationMillis);

  void glossClear(UUID viewer);

  void glossRestrict(boolean restricted);

  void glossRefresh();
}
