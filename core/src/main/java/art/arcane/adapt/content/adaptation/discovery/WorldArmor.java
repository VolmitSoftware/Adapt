package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.math.Sphere;
import art.arcane.adapt.util.common.world.LoadedChunkAccess;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** World Armor's armor from the hardness of the blocks around a player, applied under its owner's name. */
public final class WorldArmor {
  private static final String SLOT = "armor";
  private static final List<BlockPos> OFFSETS = createOffsets();

  private final Map<Id, Double> points = new ConcurrentHashMap<>();
  private final Adaptation<?> owner;
  private final Map<UUID, Double> applied;

  /** {@code applied} is the owner's per-player map of the armor it last applied. */
  public WorldArmor(Adaptation<?> owner, Map<UUID, Double> applied) {
    this.owner = owner;
    this.applied = applied;
  }

  /** Moves the player's armor a step towards what the blocks around them give at {@code level} and applies it. */
  public Change update(PlayerRef player, int level) {
    UUID playerId = player.uuid();
    double before = lerpSeed(applied.get(playerId));
    double lerped = M.lerp(before, armorAt(player.location(), level), 0.3D);
    lerped = Double.isFinite(lerped) ? lerped : 0D;
    AdaptAttributeService.get().apply(player, owner.getName(), SLOT, Attributes.ARMOR, lerped, AttributeOperation.ADD_VALUE);
    applied.put(playerId, lerped);
    return new Change(before, lerped);
  }

  public void remove(PlayerRef player) {
    AdaptAttributeService.get().remove(player, owner.getName(), SLOT, Attributes.ARMOR);
    applied.remove(player.uuid());
  }

  public void forget(UUID playerId) {
    applied.remove(playerId);
  }

  public void clear() {
    applied.clear();
  }

  public boolean isEmpty() {
    return applied.isEmpty();
  }

  public Set<UUID> players() {
    return applied.keySet();
  }

  public double armorAt(Loc l, int level) {
    WorldRef world = l.world();
    if (world == null) {
      return 0D;
    }

    int centerX = l.blockX();
    int centerY = l.blockY();
    int centerZ = l.blockZ();
    int minY = world.minHeight();
    int maxY = world.maxHeight() - 1;
    LoadedChunkAccess chunkAccess = new LoadedChunkAccess(world, 5);
    double armorValue = 0.0;
    int count = 0;

    for (BlockPos offset : OFFSETS) {
      int blockX = centerX + offset.x();
      int blockY = centerY + offset.y();
      int blockZ = centerZ + offset.z();
      if (blockY < minY || blockY > maxY || !chunkAccess.canRead(blockX, blockZ)) {
        continue;
      }

      BlockRef block = world.blockAt(blockX, blockY, blockZ);
      Id material = block.type();
      if (material.isAir() || block.isLiquid()) {
        continue;
      }

      count++;
      armorValue += points.computeIfAbsent(material, (Id type) -> computeArmorPoints(type, AdaptCore.platform().host().items()));
    }

    return scaleArmor(armorValue, count, level);
  }

  static int scanBlockCount() {
    return OFFSETS.size();
  }

  static double computeArmorPoints(Id material, ItemOps items) {
    if (!items.isBlock(material)) {
      return 0D;
    }
    return computeArmorPoints(items.blastResistance(material), items.blockHardness(material));
  }

  static double computeArmorPoints(double blastResistance, double hardness) {
    double points = Math.log(Math.min(2000D, blastResistance * blastResistance))
        + Math.log((hardness < 0D ? 50D : Math.min(50D, hardness + 25D)) * 0.33D);
    return Double.isFinite(points) && points > 0D ? points : 0D;
  }

  static double scaleArmor(double armorValue, int count, int level) {
    if (count <= 0 || armorValue <= 0D || level <= 0) {
      return 0D;
    }

    return Math.min((armorValue / count) * (level / 2D) * 0.65D, 10D);
  }

  static double lerpSeed(Double previous) {
    return previous == null || !Double.isFinite(previous) ? 0D : previous;
  }

  private static List<BlockPos> createOffsets() {
    Sphere sphere = new Sphere(5);
    List<BlockPos> offsets = new ArrayList<>(1331);
    while (sphere.hasNext()) {
      offsets.add(sphere.next());
    }
    return List.copyOf(offsets);
  }

  /** The armor applied before and after one update. */
  public record Change(double before, double after) {
  }
}
