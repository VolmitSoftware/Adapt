package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.api.world.BlockStore;
import art.arcane.adapt.engine.adaptation.BonusBlockBreaks;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.internal.WorldRuntime;
import art.arcane.adapt.api.world.BlockScan;
import art.arcane.adapt.engine.attribute.AdaptAttributeKey;
import art.arcane.adapt.engine.addon.DeclaredData;
import art.arcane.adapt.engine.data.BlockStorePort;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.engine.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.xp.CombatXp;
import art.arcane.adapt.engine.xp.CombatXpLedger;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.world.BlockScanScheduler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@link WorldRuntime} over the engine's attribute service, protection probes and XP provenance. */
final class WorldBridge implements WorldRuntime {
  private final AdaptAttributeService attributes = new Attributes();

  @Override
  public AdaptAttributeService attributes() {
    return attributes;
  }

  @Override
  public boolean attemptBlockBreak(PlayerRef player, BlockRef block) {
    return ProtectionProbes.attemptBlockBreak(player, block);
  }

  @Override
  public boolean attemptBlockBreakProbe(PlayerRef player, BlockRef block) {
    return ProtectionProbes.attemptBlockBreakProbe(player, block);
  }

  @Override
  public boolean callBlockBreak(PlayerRef player, BlockRef block) {
    return ProtectionProbes.callBlockBreak(player, block);
  }

  @Override
  public boolean attemptBlockUse(PlayerRef player, BlockRef block) {
    return ProtectionProbes.attemptBlockUse(player, block);
  }

  @Override
  public boolean attemptBlockPlaceProbe(PlayerRef player, BlockRef block) {
    return ProtectionProbes.attemptBlockPlaceProbe(player, block);
  }

  @Override
  public boolean attemptTreeGrow(PlayerRef player, BlockRef origin, Id feature, List<BlockChange> blocks) {
    return ProtectionProbes.attemptTreeGrow(player, origin, feature, blocks);
  }

  @Override
  public boolean attemptBlockDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source) {
    return ProtectionProbes.attemptBlockDropPickup(player, item, remaining, source);
  }

  @Override
  public boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, int remaining) {
    return ProtectionProbes.attemptItemPickup(player, item, remaining);
  }

  @Override
  public boolean attemptEntityItemPickup(LivingRef entity, ItemEntityRef item, int remaining) {
    return ProtectionProbes.attemptEntityItemPickup(entity, item, remaining);
  }

  @Override
  public int remainingAfterPickup(PlayerInventoryRef inventory, ItemRef stack) {
    return ProtectionProbes.remainingAfterPickup(inventory, stack);
  }

  @Override
  public int remainingAfterPickup(InventoryRef inventory, ItemRef stack) {
    return ProtectionProbes.remainingAfterPickup(inventory, stack);
  }

  @Override
  public List<BlockRef> containerBlocks(BlockRef target, boolean includeInventoryHalves) {
    return ProtectionProbes.containerBlocks(target, includeInventoryHalves);
  }

  @Override
  public boolean attemptContainerOpen(PlayerRef player, List<BlockRef> blocks) {
    return ProtectionProbes.attemptContainerOpen(player, blocks);
  }

  @Override
  public boolean allowsDamageTarget(EntityRef target, boolean ignorePassiveMobs) {
    return AdaptationDamageTargets.allows(target, ignorePassiveMobs);
  }

  @Override
  public void recordPlacement(BlockRef block) {
    XpProvenance.recordPlacement(block);
  }

  @Override
  public boolean isPlayerPlaced(BlockRef block) {
    return XpProvenance.isPlayerPlaced(block);
  }

  @Override
  public boolean wasPlaced(BlockRef block) {
    return XpProvenance.wasPlaced(block);
  }

  @Override
  public boolean forgetPlacement(BlockRef block) {
    return XpProvenance.forgetPlacement(block);
  }

  @Override
  public boolean hasPermanentPlayerModification(BlockRef block) {
    return XpProvenance.hasPermanentPlayerModification(block);
  }

  @Override
  public void recordBonemeal(BlockRef block) {
    XpProvenance.recordBonemeal(block);
  }

  @Override
  public double placeXpMultiplier(BlockRef block) {
    return XpProvenance.placeXpMultiplier(block);
  }

  @Override
  public double breakXpMultiplier(BlockRef block) {
    return XpProvenance.breakXpMultiplier(block);
  }

  @Override
  public double harvestXpMultiplier(BlockRef block) {
    return XpProvenance.harvestXpMultiplier(block);
  }

  @Override
  public boolean isFarmed(EntityRef entity) {
    return MobOrigin.isFarmed(entity);
  }

  @Override
  public boolean paysKillReward(PlayerRef killer, EntityRef victim) {
    return MobOrigin.paysKillReward(killer, victim);
  }

  @Override
  public double creditCombat(String skill, PlayerRef player, EntityDamageByEntityEvent event) {
    CombatXpLedger ledger = ledger(player);
    return ledger == null ? 0D : CombatXp.credit(ledger, skill, player, event);
  }

  @Override
  public double creditDefense(String skill, PlayerRef player, EntityDamageByEntityEvent event, double damage) {
    CombatXpLedger ledger = ledger(player);
    return ledger == null ? 0D : CombatXp.creditDefense(ledger, skill, player, event, damage);
  }

  @Override
  public boolean isGraveMob(EntityRef entity) {
    return CombatXp.isGraveMob(entity);
  }

  @Override
  public @Nullable UUID blockOwner(BlockRef block) {
    return worldData().blockOwner(block);
  }

  @Override
  public void setBlockOwner(BlockRef block, @Nullable UUID owner) {
    worldData().setBlockOwner(block, owner);
  }

  @Override
  public UUID scan(Object owner, UUID player, BlockScan scan) {
    BlockScanScheduler.ScanRequest.Builder request = BlockScanScheduler.ScanRequest.builder(scan.origin())
        .radius(scan.radius())
        .denseRadius(scan.denseRadius())
        .maxSamples(scan.maxSamples())
        .maxResults(scan.maxResults())
        .seed(scan.seed())
        .additionalMatches(supplied(scan.supplied()))
        .matcher(scan.matcher())
        .blockMatcher(scan.blockMatcher())
        .completion((BlockScanScheduler.ScanResult result) -> scan.completion().accept(result(result)));
    return BlockScanScheduler.submit(owner, player, request.build());
  }

  @Override
  public void cancelScan(Object owner, UUID player) {
    BlockScanScheduler.cancel(owner, player);
  }

  @Override
  public void cancelScans(Object owner) {
    BlockScanScheduler.cancelOwner(owner);
  }

  @Override
  public <T> T blockValue(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    return BlockStorePort.current().get(world, x, y, z, DeclaredData.requireSlice(slice));
  }

  @Override
  public <T> void setBlockValue(WorldRef world, int x, int y, int z, BlockSlice<T> slice, T value) {
    BlockStorePort.current().set(world, x, y, z, DeclaredData.requireSlice(slice), value);
  }

  @Override
  public <T> void removeBlockValue(WorldRef world, int x, int y, int z, BlockSlice<T> slice) {
    BlockStorePort.current().remove(world, x, y, z, DeclaredData.requireSlice(slice));
  }

  @Override
  public <T> void forEachBlockValue(WorldRef world, int chunkX, int chunkZ, BlockSlice<T> slice, BlockStore.Visitor<T> visitor) {
    BlockStorePort.current().iterateChunk(world, chunkX, chunkZ, DeclaredData.requireSlice(slice), visitor::accept);
  }

  @Override
  public boolean saveBlockValues(WorldRef world, int x, int z) {
    return BlockStorePort.current().savePlate(world, x, z);
  }

  private static List<BlockScanScheduler.AdditionalMatch> supplied(List<BlockScan.Supplied> supplied) {
    List<BlockScanScheduler.AdditionalMatch> matches = new ArrayList<>(supplied.size());
    for (BlockScan.Supplied match : supplied) {
      matches.add(new BlockScanScheduler.AdditionalMatch(match.x(), match.y(), match.z(), match.block(),
          match.distanceSquared()));
    }
    return matches;
  }

  private static BlockScan.Result result(BlockScanScheduler.ScanResult result) {
    List<BlockScan.Match> matches = new ArrayList<>(result.matches().size());
    for (BlockScanScheduler.Match match : result.matches()) {
      matches.add(new ScanMatch(match.x(), match.y(), match.z(), match.material(), match.distanceSquared(),
          match.supplied()));
    }
    return new ScanResult(result.scanId(), result.world(), List.copyOf(matches), result.budgetExpired());
  }

  private record ScanMatch(int x, int y, int z, Id block, double distanceSquared, boolean supplied)
      implements BlockScan.Match {
  }

  private record ScanResult(UUID scan, WorldRef world, List<BlockScan.Match> matches, boolean budgetExpired)
      implements BlockScan.Result {
  }

  private static WorldDataPort worldData() {
    return AdaptPorts.get(WorldDataPort.class, WorldDataPort.NONE);
  }

  private static CombatXpLedger ledger(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    PlayerData data = server == null ? null : server.getPlayerData(player.uuid()).orElse(null);
    return data == null ? null : data.getCombatXp();
  }

  /** Reads the engine service on every call; a core restart replaces it. */
  private static final class Attributes implements AdaptAttributeService {
    private static art.arcane.adapt.engine.attribute.AdaptAttributeService engine() {
      return art.arcane.adapt.engine.attribute.AdaptAttributeService.get();
    }

    @Override
    public void apply(LivingRef target, String adaptationName, String slot, Id attribute, double amount,
                      AttributeOperation operation) {
      engine().apply(target, adaptationName, slot, attribute, amount, operation);
    }

    @Override
    public void applyTimed(LivingRef target, String adaptationName, String slot, Id attribute, double amount,
                           AttributeOperation operation, long durationTicks) {
      engine().applyTimed(target, adaptationName, slot, attribute, amount, operation, durationTicks);
    }

    @Override
    public void remove(LivingRef target, String adaptationName, String slot, Id attribute) {
      engine().remove(target, adaptationName, slot, attribute);
    }

    @Override
    public void removeAll(LivingRef target, String adaptationName) {
      engine().removeAll(target, adaptationName);
    }

    @Override
    public int clearAllAdapt(LivingRef target) {
      return engine().clearAllAdapt(target);
    }

    @Override
    public double value(LivingRef target, Id attribute) {
      return engine().value(target, attribute);
    }

    @Override
    public boolean isEmulated(Id attribute) {
      return engine().isEmulated(attribute);
    }

    @Override
    public Id modifierKey(String adaptationName, String slot) {
      return AdaptAttributeKey.of(adaptationName, slot).key();
    }
  }

  @Override
  public boolean bonusBreakActive(PlayerRef player) {
    return BonusBlockBreaks.isActive(player);
  }

  @Override
  public boolean bonusBreak(PlayerRef player, BlockRef block, int durabilityCost) {
    return BonusBlockBreaks.breakBlock(player, block, durabilityCost);
  }
}
