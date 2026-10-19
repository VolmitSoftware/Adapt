package art.arcane.adapt.api.golem.item;

import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public final class OwnerBoundDrop {
  private OwnerBoundDrop() {
  }

  public static Item drop(Location at, ItemStack item, UUID owner) {
    Objects.requireNonNull(owner);
    World world = Objects.requireNonNull(at.getWorld());
    return world.dropItem(spot(at, world.getMinHeight()), item, drop -> bind(drop, owner));
  }

  static Location spot(Location at, int minHeight) {
    Location spot = at.clone();
    double floor = minHeight + 1D;
    if (!(spot.getY() >= floor)) {
      spot.setY(floor);
    }
    return spot;
  }

  static void bind(Item drop, UUID owner) {
    drop.setOwner(owner);
    drop.setInvulnerable(true);
    drop.setUnlimitedLifetime(true);
    PaperCompat.setCanMobPickup(drop, false);
  }
}
