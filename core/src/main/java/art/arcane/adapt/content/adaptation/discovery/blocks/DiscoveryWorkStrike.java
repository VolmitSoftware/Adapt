package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryKeenEye;
import art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense;
import art.arcane.adapt.content.adaptation.discovery.KeenEyeScan;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;


/**
 * The nearest chest, spawner or generated structure around the work location pings for the player: Sixth Sense's
 * direction line points at it, and a chest or spawner also glows as Keen Eye shows it. A chest or spawner counts only
 * when the player can see it and may open it there.
 */
public class DiscoveryWorkStrike extends StrikeBlock<DiscoveryWorkStrike.Config> {
  public DiscoveryWorkStrike() {
    super("block-discovery-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("recovery_compass"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.center();
    double range = use.scaled(getConfig().rangeAtFirstLevel, getConfig().rangeAtTopLevel);
    BlockRef container = null;
    for (BlockRef seen : KeenEyeScan.within(player, center, range, KeenEyeScan.HARD_MAX_HIGHLIGHTS, (Id type) -> true)) {
      if (canAccessChest(player, seen.location())) {
        container = seen;
        break;
      }
    }
    double nearest = container == null ? Double.MAX_VALUE : center.distanceSquared(container.center());
    Loc found = container == null ? null : container.center();
    Found structure = nearestStructure(center, range);
    if (structure != null && structure.distanceSquared() < nearest) {
      found = structure.at();
      container = null;
    }
    if (found == null) {
      return Outcome.MISSED;
    }
    if (container != null) {
      AdaptPorts.get(ViewerDisplayPort.class, ViewerDisplayPort.NONE).showBlock(getName(), "ping", player,
          new Loc(container.world(), container.x(), container.y(), container.z()), container.state(),
          KeenEyeScan.isSpawner(container.type()) ? DiscoveryKeenEye.SPAWNER_COLOR : DiscoveryKeenEye.CHEST_COLOR,
          (int) (getConfig().pingMillis / 50L));
    }
    Loc eye = player.location();
    double dx = found.x() - eye.x();
    double dz = found.z() - eye.z();
    double horizontal = Math.sqrt((dx * dx) + (dz * dz));
    if (horizontal > 0.5D) {
      DiscoverySixthSense.pointTowards(this, player, dx / horizontal, dz / horizontal, horizontal);
    }
    return Outcome.INSTANT;
  }

  /** The middle of the nearest generated structure whose box comes within {@code range} of {@code center}, or null. */
  private static Found nearestStructure(Loc center, double range) {
    WorldRef world = center.world();
    int chunkRadius = Math.max(1, (int) Math.ceil(range / 16D));
    Found nearest = null;
    double nearestDistance = range * range;
    for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
      for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
        int chunkX = (center.blockX() >> 4) + cx;
        int chunkZ = (center.blockZ() >> 4) + cz;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        for (GeneratedStructure structure : world.structuresAt(chunkX, chunkZ)) {
          Box box = structure.bounds();
          double x = clamp(center.x(), box.minX(), box.maxX());
          double y = clamp(center.y(), box.minY(), box.maxY());
          double z = clamp(center.z(), box.minZ(), box.maxZ());
          double distance = center.distanceSquared(new Loc(world, x, y, z));
          if (distance <= nearestDistance) {
            nearestDistance = distance;
            nearest = new Found(new Loc(world, (box.minX() + box.maxX()) / 2D, (box.minY() + box.maxY()) / 2D,
                (box.minZ() + box.maxZ()) / 2D), distance);
          }
        }
      }
    }
    return nearest;
  }

  private record Found(Loc at, double distanceSquared) {
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  @ConfigDescription("The nearest chest, spawner or structure within 24 blocks pings for you.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Search range in blocks at level 1.", impact = "Higher values find things farther away.")
    double rangeAtFirstLevel = 24D;
    @ConfigDoc(value = "Search range in blocks at the top level.", impact = "Higher values find things farther away.")
    double rangeAtTopLevel = 32D;
    @ConfigDoc(value = "How long a found chest or spawner glows, in milliseconds.", impact = "Higher values keep the ping visible longer.")
    long pingMillis = 4_000L;
  }
}
