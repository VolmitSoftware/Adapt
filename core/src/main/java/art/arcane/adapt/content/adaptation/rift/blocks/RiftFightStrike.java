package art.arcane.adapt.content.adaptation.rift.blocks;

import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.content.adaptation.rift.RiftBlinkPath;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/**
 * The target is blinked straight away from the player to the farthest safe spot within reach, checked with Rift Blink's
 * landing rules. The player must see the target and the target's path must be clear, so nothing passes through walls.
 */
public class RiftFightStrike extends StrikeBlock<RiftFightStrike.Config> {
  public RiftFightStrike() {
    super("block-rift-strike-fight");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_eye"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    LivingRef target = use.target();
    PlayerRef player = use.player();
    if (target == null || isProtectedFriendly(player, target) || !allowed(player, target, target.location())
        || !player.hasLineOfSight(target)) {
      return Outcome.MISSED;
    }
    Loc from = target.location();
    Vec3 away = away(player, from);
    double middle = target.height() / 2D;
    for (double distance = use.scaled(getConfig().distanceAtFirstLevel, getConfig().distanceAtTopLevel);
         distance >= 1D; distance -= 1D) {
      Loc destination = from.add(away.multiply(distance));
      if (RiftBlinkPath.isSafeDestination(destination, target.height())
          && RiftBlinkPath.hasClearSight(from.add(0D, middle, 0D), destination.add(0D, middle, 0D))
          && allowed(player, target, destination)) {
        RiftBlink.departFx(this, from, destination);
        target.teleport(destination.withRotation(from.yaw(), from.pitch()), TeleportCause.PLUGIN);
        return Outcome.INSTANT;
      }
    }
    return Outcome.MISSED;
  }

  private boolean allowed(PlayerRef player, LivingRef target, Loc at) {
    return target instanceof PlayerRef ? canPVP(player, at) : canPVE(player, at);
  }

  private static Vec3 away(PlayerRef player, Loc from) {
    Loc origin = player.location();
    Vec3 away = new Vec3(from.x() - origin.x(), 0D, from.z() - origin.z());
    if (away.lengthSquared() < 1.0E-4D) {
      away = origin.direction().withY(0D);
    }
    return away.lengthSquared() < 1.0E-4D ? new Vec3(0D, 0D, 1D) : away.normalize();
  }

  @ConfigDescription("The target is blinked 4 blocks away from you.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How far the target is blinked at level 1, in blocks.", impact = "Higher values throw the target farther.")
    double distanceAtFirstLevel = 4D;
    @ConfigDoc(value = "How far the target is blinked at the top level, in blocks.", impact = "Higher values throw the target farther.")
    double distanceAtTopLevel = 6D;
  }
}
