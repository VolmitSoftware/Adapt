package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.concurrent.ThreadLocalRandom;
/**
 * Clinging to a wall mid-air: gravity is cancelled, the player is pulled against the wall and falls slowly. The
 * latched-wall map belongs to the owning adaptation.
 */
public final class AgilityWallLatch {
  public static final List<PlatformNeed> NEEDS = List.of(PlatformNeed.attribute(Attributes.GRAVITY));
  private static final String SLOT_LATCH = "latch";
  private static final double GRAVITY_CANCEL = -1D;
  private static final int[][] WALL_OFFSETS = {
      {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0},
      {1, 0, -1}, {1, 0, 1}, {-1, 0, -1}, {-1, 0, 1},
      {1, 1, -1}, {1, 1, 1}, {-1, 1, -1}, {-1, 1, 1},
      {0, 1, -1}, {0, 1, 1}, {1, 1, 0}, {-1, 1, 0}
  };

  private final Adaptation<?> owner;
  private final Map<UUID, BlockRef> latchedWalls;

  public AgilityWallLatch(Adaptation<?> owner, Map<UUID, BlockRef> latchedWalls) {
    this.owner = owner;
    this.latchedWalls = latchedWalls;
  }

  public boolean latched(UUID id) {
    return latchedWalls.containsKey(id);
  }

  public boolean anyLatched() {
    return !latchedWalls.isEmpty();
  }

  public BlockRef wall(UUID id) {
    return latchedWalls.get(id);
  }

  /** The first solid block beside or just above the player, or null. */
  public BlockRef findWall(PlayerRef p) {
    Loc location = p.location();
    BlockRef base = location.world().blockAt(location);
    for (int[] offset : WALL_OFFSETS) {
      BlockRef wall = base.relative(offset[0], offset[1], offset[2]);
      if (wall.isSolid()) {
        return wall;
      }
    }

    return null;
  }

  /** Holds the player on {@code wall} for this tick; true on first contact. */
  public boolean hold(PlayerRef p, BlockRef wall) {
    UUID id = p.uuid();
    boolean firstContact = !latchedWalls.containsKey(id);
    if (firstContact) {
      Loc location = p.location();
      double dx = (wall.x() + 0.5D) - location.x();
      double dz = (wall.z() + 0.5D) - location.z();
      double len = Math.sqrt((dx * dx) + (dz * dz));
      Loc contact = len < 1.0E-4D
          ? location.add(0, 0.3D, 0)
          : location.add((dx / len) * 0.35D, 0.3D, (dz / len) * 0.35D);
      AgilityWallJump.latchFx(owner, contact, wall);
    }

    Vec3 velocity = p.velocity();
    Loc location = p.location();
    double shiftX = location.x() - (wall.x() + 0.5D);
    double shiftZ = location.z() - (wall.z() + 0.5D);
    p.setVelocity(new Vec3(velocity.x() - (shiftX / 16), velocity.y(), velocity.z() - (shiftZ / 16)));
    latchedWalls.put(id, wall);
    clearLatchedFallDistance(p);
    AdaptAttributeService.get().apply(p, owner.getId().toString(), SLOT_LATCH, Attributes.GRAVITY, GRAVITY_CANCEL, AttributeOperation.ADD_MULTIPLIED_TOTAL);
    Vec3 c = p.velocity();
    p.setVelocity(c.withY((c.y() * 0.35) - 0.0025));
    if (ThreadLocalRandom.current().nextDouble() < 0.25) {
      AgilityWallJump.clingFx(owner, p, wall);
    }
    return firstContact;
  }

  /** Lets go of the wall; false when the player was not latched. */
  public boolean release(PlayerRef p) {
    if (latchedWalls.remove(p.uuid()) == null) {
      return false;
    }

    clearLatchedFallDistance(p);
    AdaptAttributeService.get().remove(p, owner.getId().toString(), SLOT_LATCH, Attributes.GRAVITY);
    return true;
  }

  /** The push-off effect when jumping away from {@code wall}. */
  public void kick(PlayerRef p, BlockRef wall, int cloudCount, float pitch) {
    Loc location = p.location();
    double awayX = location.x() - (wall.x() + 0.5D);
    double awayZ = location.z() - (wall.z() + 0.5D);
    AgilityWallJump.kickFx(owner, location, wall, awayX, awayZ, cloudCount, pitch);
  }

  static void clearLatchedFallDistance(PlayerRef p) {
    p.setFallDistance(0F);
  }
}
