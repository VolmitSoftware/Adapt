package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.world.BlockScan;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.api.world.BlockStore;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** World services behind the attribute, protection-probe, damage-target, XP-provenance and mob-origin faces. */
@ApiStatus.Internal
public interface WorldRuntime {
  AdaptAttributeService attributes();

  boolean attemptBlockBreak(PlayerRef player, BlockRef block);

  boolean attemptBlockBreakProbe(PlayerRef player, BlockRef block);

  boolean callBlockBreak(PlayerRef player, BlockRef block);

  boolean attemptBlockUse(PlayerRef player, BlockRef block);

  boolean attemptBlockPlaceProbe(PlayerRef player, BlockRef block);

  boolean attemptTreeGrow(PlayerRef player, BlockRef origin, Id feature, List<BlockChange> blocks);

  boolean attemptBlockDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source);

  boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, int remaining);

  boolean attemptEntityItemPickup(LivingRef entity, ItemEntityRef item, int remaining);

  int remainingAfterPickup(PlayerInventoryRef inventory, ItemRef stack);

  int remainingAfterPickup(InventoryRef inventory, ItemRef stack);

  List<BlockRef> containerBlocks(BlockRef target, boolean includeInventoryHalves);

  boolean attemptContainerOpen(PlayerRef player, List<BlockRef> blocks);

  boolean allowsDamageTarget(EntityRef target, boolean ignorePassiveMobs);

  void recordPlacement(BlockRef block);

  boolean isPlayerPlaced(BlockRef block);

  boolean wasPlaced(BlockRef block);

  boolean forgetPlacement(BlockRef block);

  boolean hasPermanentPlayerModification(BlockRef block);

  void recordBonemeal(BlockRef block);

  double placeXpMultiplier(BlockRef block);

  double breakXpMultiplier(BlockRef block);

  double harvestXpMultiplier(BlockRef block);

  boolean isFarmed(EntityRef entity);

  boolean paysKillReward(PlayerRef killer, EntityRef victim);

  /** Combat XP credit from the player's own ledger; 0 when the player has no loaded data. */
  double creditCombat(String skill, PlayerRef player, EntityDamageByEntityEvent event);

  double creditDefense(String skill, PlayerRef player, EntityDamageByEntityEvent event, double damage);

  boolean isGraveMob(EntityRef entity);

  @Nullable UUID blockOwner(BlockRef block);

  /** Stores the block's owner; null removes it. */
  void setBlockOwner(BlockRef block, @Nullable UUID owner);

  UUID scan(Object owner, UUID player, BlockScan scan);

  void cancelScan(Object owner, UUID player);

  void cancelScans(Object owner);

  <T> T blockValue(WorldRef world, int x, int y, int z, BlockSlice<T> slice);

  <T> void setBlockValue(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value);

  <T> void removeBlockValue(WorldRef world, int x, int y, int z, BlockSlice<T> slice);

  <T> void forEachBlockValue(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, BlockStore.Visitor<T> visitor);

  boolean saveBlockValues(WorldRef world, int x, int z);

  boolean bonusBreakActive(PlayerRef player);

  boolean bonusBreak(PlayerRef player, BlockRef block, int durabilityCost);
}
