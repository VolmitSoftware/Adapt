package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Objects;
import java.util.UUID;

public final class OwnerBoundDrop {
  private OwnerBoundDrop() {
  }

  public static ItemEntityRef drop(Loc at, ItemRef item, UUID owner) {
    Objects.requireNonNull(owner);
    WorldRef world = Objects.requireNonNull(at.world());
    return world.dropItem(spot(at, world.minHeight()).position(), item, (ItemEntityRef drop) -> bind(drop, owner));
  }

  static Loc spot(Loc at, int minHeight) {
    double floor = minHeight + 1D;
    return at.y() >= floor ? at : at.withY(floor);
  }

  static void bind(ItemEntityRef drop, UUID owner) {
    drop.setOwner(owner);
    drop.setInvulnerable(true);
    drop.setUnlimitedLifetime(true);
    drop.setCanMobPickup(false);
  }
}
