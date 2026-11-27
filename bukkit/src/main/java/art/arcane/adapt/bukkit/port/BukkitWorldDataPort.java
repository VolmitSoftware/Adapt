package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.engine.data.WorldData;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.engine.data.unit.PlacementStamp;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.engine.data.BrewingStandOwner;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.block.Block;

import java.util.UUID;

public final class BukkitWorldDataPort implements WorldDataPort {
  @Override
  public double reportEarnings(BlockRef block) {
    Block target = BukkitRefs.block(block);
    return WorldData.of(target.getWorld()).reportEarnings(target);
  }

  @Override
  public double getEarningsMultiplier(BlockRef block) {
    Block target = BukkitRefs.block(block);
    return WorldData.of(target.getWorld()).getEarningsMultiplier(target);
  }

  @Override
  public Placement placement(BlockRef block) {
    Block target = BukkitRefs.block(block);
    PlacementStamp stamp = WorldData.of(target.getWorld()).get(target, PlacementStamp.class);
    return stamp == null ? null : new Placement(stamp.getPlacedAt(), stamp.getBrokenAt(), stamp.getBonemealedAt());
  }

  @Override
  public void setPlacement(BlockRef block, Placement placement) {
    Block target = BukkitRefs.block(block);
    WorldData data = WorldData.of(target.getWorld());
    if (placement == null) {
      data.remove(target, PlacementStamp.class);
      return;
    }
    data.set(target, new PlacementStamp(placement.placedAt(), placement.brokenAt(), placement.bonemealedAt()));
  }

  @Override
  public void clearPlacement(BlockRef block) {
    Block target = BukkitRefs.block(block);
    WorldData.of(target.getWorld()).remove(target, PlacementStamp.class);
  }

  @Override
  public UUID blockOwner(BlockRef block) {
    Block target = BukkitRefs.block(block);
    BrewingStandOwner owner = WorldData.of(target.getWorld()).get(target, BrewingStandOwner.class);
    return owner == null ? null : owner.getOwner();
  }

  @Override
  public void setBlockOwner(BlockRef block, UUID owner) {
    Block target = BukkitRefs.block(block);
    WorldData data = WorldData.of(target.getWorld());
    if (owner == null) {
      data.remove(target, BrewingStandOwner.class);
      return;
    }
    data.set(target, new BrewingStandOwner(owner));
  }
}
