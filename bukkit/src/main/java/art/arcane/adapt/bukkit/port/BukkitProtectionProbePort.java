package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.protection.ProtectionProbePort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.world.BukkitTrees;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import org.bukkit.Bukkit;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.ArrayList;
import java.util.List;

/** Fires the real Bukkit break and pickup events so every protection plugin can veto. */
final class BukkitProtectionProbePort implements ProtectionProbePort {
  @Override
  public boolean probeBreak(PlayerRef player, BlockRef block) {
    Player bukkitPlayer = BukkitRefs.player(player);
    Block bukkitBlock = BukkitRefs.block(block);
    return bukkitPlayer != null && bukkitBlock != null && ProtectionEventProbe.attemptBlockBreakProbe(bukkitPlayer, bukkitBlock);
  }

  @Override
  public boolean callBreak(PlayerRef player, BlockRef block) {
    Player bukkitPlayer = BukkitRefs.player(player);
    Block bukkitBlock = BukkitRefs.block(block);
    if (bukkitPlayer == null || bukkitBlock == null) {
      return false;
    }
    BlockBreakEvent event = new BlockBreakEvent(bukkitBlock, bukkitPlayer);
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled();
  }

  @Override
  public boolean probeTreeGrow(PlayerRef player, BlockRef origin, Id feature, List<BlockChange> blocks) {
    Player bukkitPlayer = BukkitRefs.player(player);
    Block bukkitOrigin = BukkitRefs.block(origin);
    TreeType type = BukkitTrees.treeType(feature);
    if (bukkitPlayer == null || bukkitOrigin == null || type == null) {
      return false;
    }
    List<BlockState> states = new ArrayList<>(blocks.size());
    for (BlockChange change : blocks) {
      BlockState state = BukkitRefs.block(change.block()).getState();
      state.setBlockData(BukkitRefs.blockData(change.state()));
      states.add(state);
    }
    StructureGrowEvent event = new StructureGrowEvent(bukkitOrigin.getLocation(), type, false, bukkitPlayer,
        new ArrayList<>(states));
    ProtectionEventProbe.dispatch(event);
    if (event.isCancelled() || event.getBlocks().size() != states.size()) {
      return false;
    }
    for (int i = 0; i < states.size(); i++) {
      BlockState planned = states.get(i);
      BlockState current = event.getBlocks().get(i);
      if (!planned.getLocation().equals(current.getLocation())
          || !planned.getBlockData().getAsString().equals(current.getBlockData().getAsString())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean probeUse(PlayerRef player, BlockRef block) {
    Player bukkitPlayer = BukkitRefs.player(player);
    Block bukkitBlock = BukkitRefs.block(block);
    return bukkitPlayer != null && bukkitBlock != null && ProtectionEventProbe.attemptBlockUse(bukkitPlayer, bukkitBlock);
  }

  @Override
  public boolean probePlace(PlayerRef player, BlockRef block) {
    Player bukkitPlayer = BukkitRefs.player(player);
    Block bukkitBlock = BukkitRefs.block(block);
    return bukkitPlayer != null && bukkitBlock != null && ProtectionEventProbe.attemptBlockPlaceProbe(bukkitPlayer, bukkitBlock);
  }

  @Override
  public boolean probeDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source) {
    Player bukkitPlayer = BukkitRefs.player(player);
    if (bukkitPlayer == null || !(BukkitRefs.entity(item) instanceof Item bukkitItem)) {
      return true;
    }
    return ProtectionEventProbe.attemptBlockDropPickup(bukkitPlayer, bukkitItem, remaining, BukkitRefs.location(source));
  }

  @Override
  public boolean probeItemPickup(PlayerRef player, ItemEntityRef item, int remaining) {
    Player bukkitPlayer = BukkitRefs.player(player);
    return bukkitPlayer != null && BukkitRefs.entity(item) instanceof Item bukkitItem
        && ProtectionEventProbe.attemptItemPickup(bukkitPlayer, bukkitItem, remaining);
  }

  @Override
  public boolean probeEntityPickup(LivingRef entity, ItemEntityRef item, int remaining) {
    return BukkitRefs.entity(entity) instanceof LivingEntity living && BukkitRefs.entity(item) instanceof Item bukkitItem
        && ProtectionEventProbe.attemptEntityItemPickup(living, bukkitItem, remaining);
  }
}
