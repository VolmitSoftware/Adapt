package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.bukkit.event.BukkitEventBridge;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerLink;
import art.arcane.adapt.engine.integration.IrisTreeFellerPort;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.block.Block;
import org.bukkit.event.Event;

/**
 * Iris tree felling through the Bukkit-only link. Iris tracks the native break event by identity, so calls resolve
 * the Bukkit event currently being bridged and copy its cancel, drop and experience state back to the core event.
 */
final class BukkitIrisTreeFellerPort implements IrisTreeFellerPort {
  @Override
  public boolean isAvailable() {
    return IrisTreeFellerLink.isAvailable();
  }

  @Override
  public boolean tryFell(art.arcane.adapt.platform.event.block.BlockBreakEvent event, int durabilityPreservationChance,
                         RunHooks runHooks) {
    org.bukkit.event.block.BlockBreakEvent bukkit = nativeEvent(event);
    if (bukkit == null) {
      return false;
    }
    boolean accepted = IrisTreeFellerLink.tryFell(bukkit, durabilityPreservationChance, runHooks);
    event.setCancelled(bukkit.isCancelled());
    event.setDropItems(bukkit.isDropItems());
    event.setExperience(bukkit.getExpToDrop());
    return accepted;
  }

  @Override
  public boolean isManagedBreak(art.arcane.adapt.platform.event.block.BlockBreakEvent event) {
    org.bukkit.event.block.BlockBreakEvent bukkit = nativeEvent(event);
    return bukkit != null && IrisTreeFellerLink.isManagedBreak(bukkit);
  }

  @Override
  public boolean isTreeBlock(BlockRef block) {
    Block bukkit = BukkitRefs.block(block);
    return bukkit != null && IrisTreeFellerLink.isTreeBlock(bukkit);
  }

  private static org.bukkit.event.block.BlockBreakEvent nativeEvent(art.arcane.adapt.platform.event.block.BlockBreakEvent event) {
    Event bridging = BukkitEventBridge.bridging();
    if (!(bridging instanceof org.bukkit.event.block.BlockBreakEvent bukkit)) {
      return null;
    }
    Block block = bukkit.getBlock();
    BlockRef target = event.block();
    if (block.getX() != target.x() || block.getY() != target.y() || block.getZ() != target.z()
        || !block.getWorld().getUID().equals(target.world().uid())
        || !bukkit.getPlayer().getUniqueId().equals(event.player().uuid())) {
      return null;
    }
    return bukkit;
  }
}
