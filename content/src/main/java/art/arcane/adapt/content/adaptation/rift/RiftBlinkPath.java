package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.function.DoublePredicate;

/**
 * Rift Blink's landing search: finds a safe spot to stand along a direction, through walls when phasing, preferring
 * the farthest or the highest spot.
 */
public final class RiftBlinkPath {

  private RiftBlinkPath() {
  }

  /**
   * How far and how a blink may travel. {@code phase} receives the vertical part of the normalized direction and
   * says whether the blink passes through blocks.
   */
  public record Shape(double distance, double minDistance, DoublePredicate phase, RiftBlink.Targeting targeting,
                      RiftBlink.Landing landing, int groundSnapDepth, int verticalSearchHeight) {
  }

  /** The feet location to land on, or null when no safe spot exists. */
  public static Loc find(PlayerRef player, Vec3 requestedDirection, Shape shape) {
    Loc eye = player.eyeLocation();
    if (!Double.isFinite(requestedDirection.lengthSquared()) || requestedDirection.lengthSquared() <= 0.000001) {
      return null;
    }
    Vec3 direction = requestedDirection.normalize();
    double maxDistance = shape.distance();
    double readableDistance = readableReach(eye, direction, maxDistance);
    if (readableDistance < 1D) {
      return null;
    }
    boolean phase = shape.phase().test(direction.y());
    RiftBlink.Targeting targeting = shape.targeting();
    BlockHit hit = phase ? null : eye.world().rayTraceBlockHit(eye.position(), direction, readableDistance, false);
    Loc origin = player.location();
    double eyeHeight = eye.y() - origin.y();
    Loc best = null;
    if (hit != null) {
      BlockRef mantleFeet = hit.block().relative(BlockFace.UP);
      Loc mantle = mantleFeet.location().add(0.5D, 0D, 0.5D);
      if (isStandableBlock(mantleFeet) && validCandidate(player, origin, mantle, maxDistance, shape.minDistance())) {
        if (targeting == RiftBlink.Targeting.DISTANCE) {
          return mantle;
        }
        best = mantle;
      }
    }
    Double hitDistance = hit == null ? null : hit.position().distance(eye.position());
    double reach = RiftBlink.blinkReach(phase, readableDistance, hitDistance);
    int verticalSearch = targeting == RiftBlink.Targeting.VERTICALITY ? Math.max(0, Math.min(16, shape.verticalSearchHeight())) : 0;
    for (double distance = reach; distance >= 1D; distance -= 1D) {
      Loc feet = eye.add(direction.multiply(distance)).add(0D, -eyeHeight, 0D);
      Loc resolved = resolveStand(feet, shape.landing(), shape.groundSnapDepth());
      if (validCandidate(player, origin, resolved, maxDistance, shape.minDistance())) {
        if (targeting == RiftBlink.Targeting.DISTANCE) {
          return resolved;
        }
        best = RiftBlink.betterCandidate(origin, best, resolved, targeting);
      }
      for (int rise = 1; rise <= verticalSearch; rise++) {
        Loc raised = new Loc(feet.world(), feet.x(), feet.blockY() + rise, feet.z());
        if (!canRead(raised, 0.35D) || !isStandableBlock(raised.world().blockAt(raised))
            || !validCandidate(player, origin, raised, maxDistance, shape.minDistance())
            || (!phase && !hasClearSight(eye, raised.add(0D, eyeHeight, 0D)))) {
          continue;
        }
        best = RiftBlink.betterCandidate(origin, best, raised, targeting);
      }
    }
    return best;
  }

  /** {@code minDistance} is floored at half a block. */
  public static boolean validCandidate(PlayerRef player, Loc origin, Loc candidate, double maxDistance, double minDistance) {
    double minimum = Math.max(0.5D, minDistance);
    return candidate != null && Double.isFinite(candidate.x()) && Double.isFinite(candidate.y())
        && Double.isFinite(candidate.z()) && origin.sameWorld(candidate)
        && origin.distanceSquared(candidate) >= minimum * minimum
        && origin.distanceSquared(candidate) <= maxDistance * maxDistance
        && isSafeDestination(candidate, player.height());
  }

  private static double readableReach(Loc eye, Vec3 direction, double maxDistance) {
    double reach = 0D;
    for (double distance = 0D; distance <= maxDistance; distance += 0.5D) {
      if (!canRead(eye.add(direction.multiply(distance)), 0.4D)) {
        break;
      }
      reach = distance;
    }
    return reach;
  }

  /** True when nothing solid and no unloaded chunk lies on the straight line between the two points. */
  public static boolean hasClearSight(Loc from, Loc to) {
    Vec3 direction = to.position().subtract(from.position());
    double distance = direction.length();
    if (distance < 0.001D) {
      return true;
    }
    direction = direction.normalize();
    return readableReach(from, direction, distance + 0.5D) >= distance
        && from.world().rayTraceBlockHit(from.position(), direction, distance, false) == null;
  }

  private static Loc resolveStand(Loc feet, RiftBlink.Landing landing, int groundSnapDepth) {
    if (!canRead(feet, 0.35D)) {
      return null;
    }
    if (landing == RiftBlink.Landing.NONE) {
      return isSafeDestination(feet, 1.8D) ? feet : null;
    }
    int snapDepth = RiftBlink.landingSnapDepth(groundSnapDepth, landing);
    for (int i = 0; i <= snapDepth; i++) {
      Loc candidateLocation = feet.add(0D, -i, 0D);
      if (!canRead(candidateLocation, 0.35D)) {
        break;
      }
      BlockRef candidate = candidateLocation.world().blockAt(candidateLocation);
      if (isStandableBlock(candidate)) {
        return new Loc(feet.world(), feet.x(), candidate.y(), feet.z());
      }
      if (!candidate.isPassable()) {
        break;
      }
    }
    return isSafeDestination(feet, 1.8D) ? feet : null;
  }

  private static boolean isStandableBlock(BlockRef feet) {
    if (!canRead(feet.location(), 0.35D) || !feet.isPassable() || !feet.relative(BlockFace.UP).isPassable()) {
      return false;
    }
    BlockRef floor = feet.relative(BlockFace.DOWN);
    return !floor.isPassable() && floor.boundingBox().maxY() >= feet.y() - 0.001D;
  }

  private static boolean canRead(Loc location, double radius) {
    WorldRef world = location.world();
    if (location.y() < world.minHeight() + 1D || location.y() + 2D >= world.maxHeight()) {
      return false;
    }
    int minChunkX = (int) Math.floor((location.x() - radius) / 16D);
    int maxChunkX = (int) Math.floor((location.x() + radius) / 16D);
    int minChunkZ = (int) Math.floor((location.z() - radius) / 16D);
    int maxChunkZ = (int) Math.floor((location.z() + radius) / 16D);
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          return false;
        }
      }
    }
    return !AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().ownsArea(location, radius, radius);
  }

  /** Room to stand at {@code feet} for a body {@code height} tall: loaded, inside the border, clear of blocks, lava and fire. */
  public static boolean isSafeDestination(Loc feet, double height) {
    if (!canRead(feet, 0.35D) || !Double.isFinite(height)
        || feet.y() + Math.max(1.8D, height) >= feet.world().maxHeight()
        || !feet.world().isInsideBorder(feet.position().add(-0.3D, 0D, -0.3D))
        || !feet.world().isInsideBorder(feet.position().add(0.3D, 0D, 0.3D))) {
      return false;
    }
    Box body = new Box(feet.x() - 0.3D, feet.y() + 0.001D, feet.z() - 0.3D,
        feet.x() + 0.3D, feet.y() + Math.max(1.8D, height), feet.z() + 0.3D);
    for (int x = (int) Math.floor(body.minX()); x <= (int) Math.floor(body.maxX()); x++) {
      for (int y = (int) Math.floor(body.minY()); y <= (int) Math.floor(body.maxY()); y++) {
        for (int z = (int) Math.floor(body.minZ()); z <= (int) Math.floor(body.maxZ()); z++) {
          BlockRef block = feet.world().blockAt(x, y, z);
          Id type = block.type();
          if (type.equals(RiftBlink.LAVA) || type.equals(RiftBlink.FIRE) || type.equals(RiftBlink.SOUL_FIRE)
              || (!block.isPassable() && block.boundingBox().overlaps(body))) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
