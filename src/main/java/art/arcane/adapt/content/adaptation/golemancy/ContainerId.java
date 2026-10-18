package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

record ContainerId(UUID world, int x, int y, int z) {
  private static final double CENTER = 0.5D;

  ContainerId {
    Objects.requireNonNull(world);
  }

  static ContainerId of(Block block, Inventory inventory) {
    return left(inventory).flatMap(ContainerId::at).orElseGet(() -> new ContainerId(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
  }

  static Optional<ContainerId> of(Inventory inventory) {
    Optional<ContainerId> left = left(inventory).flatMap(ContainerId::at);
    return left.isPresent() ? left : at(inventory.getLocation());
  }

  double distanceSquared(ContainerId other) {
    return distanceSquared(other.x + CENTER, other.y + CENTER, other.z + CENTER);
  }

  double distanceSquared(double pointX, double pointY, double pointZ) {
    double dx = x + CENTER - pointX;
    double dy = y + CENTER - pointY;
    double dz = z + CENTER - pointZ;
    return dx * dx + dy * dy + dz * dz;
  }

  private static Optional<Location> left(Inventory inventory) {
    return inventory instanceof DoubleChestInventory chest ? Optional.ofNullable(chest.getLeftSide().getLocation()) : Optional.empty();
  }

  private static Optional<ContainerId> at(Location location) {
    World world = location == null ? null : location.getWorld();
    return world == null ? Optional.empty() : Optional.of(new ContainerId(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
  }
}
