package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;

/** Aim and effects shared by Clod Hurler and Brickbat, whose blocks fly on {@link AxeThrow#fling}. */
public final class BlockFling {
  static final double GRAVITY = 0.03D;
  static final double DRAG = 0.99D;
  private static final int MIN_TICKS = 2;
  private static final int MAX_TICKS = 40;

  private BlockFling() {
  }

  /** Flight ticks for a throw over {@code distance} blocks at about {@code speed} blocks per tick. */
  public static int flightTicks(double distance, double speed) {
    return Math.max(MIN_TICKS, Math.min(MAX_TICKS, (int) Math.round(distance / Math.max(0.1D, speed))));
  }

  /**
   * The launch velocity that carries a snowball from {@code from} to {@code to} in exactly {@code ticks} ticks under
   * vanilla snowball physics (move, then drag, then gravity), so the block arcs onto the target instead of flying flat.
   */
  public static Vec3 launch(Vec3 from, Vec3 to, int ticks) {
    double carry = 0D;
    double fall = 0D;
    double fallen = 0D;
    double kept = 1D;
    for (int tick = 0; tick < ticks; tick++) {
      carry += kept;
      fall += fallen;
      fallen += kept;
      kept *= DRAG;
    }
    Vec3 span = to.subtract(from);
    return new Vec3(span.x() / carry, (span.y() + (GRAVITY * fall)) / carry, span.z() / carry);
  }

  /** The point to throw at: the middle of {@code target}, led by its motion over the flight. */
  public static Vec3 aimPoint(LivingRef target, int ticks) {
    Vec3 middle = target.location().position().add(0D, target.height() * 0.55D, 0D);
    Vec3 motion = target.velocity();
    return middle.add(motion.x() * ticks, 0D, motion.z() * ticks);
  }

  /** A short block-dust trail behind the flying block and a whoosh as it leaves the hand. */
  public static void launchFx(Adaptation<?> owner, Loc spawn, ProjectileRef ball, BlockStateRef block, int flightTicks) {
    Fx.now(owner, spawn, FxPriority.GAMEPLAY)
        .burst(Particles.blockCrack(block), 4, 0.15D)
        .chord(Sounds.ENTITY_SNOWBALL_THROW, 0.7F, 0.6F, block.breakSound(), 0.5F, 1.3F);
    FxTimeline.follow(owner, ball)
        .duration(flightTicks)
        .priority(FxPriority.TRAIL)
        .cullRadius(40)
        .frame((FxEmitter f, int tick, double progress) -> {
          f.particle(Particles.blockCrack(block), 2, 0D, 0D, 0D, 0.08D, 0.0D);
          if ((tick % 3) == 0) {
            f.particle(Particles.CLOUD, 1, 0D, 0D, 0D, 0.02D, 0.0D);
          }
        })
        .start();
  }

  /** The block bursting where it landed, with its own break sound. */
  public static void impactFx(Adaptation<?> owner, Loc impact, BlockStateRef block) {
    Fx.now(owner, impact, FxPriority.COMBAT)
        .burst(Particles.blockCrack(block), 16, 0.35D)
        .chord(block.breakSound(), 1.0F, 0.8F, Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.5F, 0.7F);
  }

  /** A crit spark on the creature the block struck. */
  public static void hitFx(Adaptation<?> owner, LivingRef target) {
    Fx.now(owner, target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
        .burst(Particles.CRIT, 8, 0.3D)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.7F, 0.8F);
  }

  /**
   * Shows {@code item} flying back from {@code impact} to {@code owner} on a short hop over {@code ticks}. The item is
   * only a picture: nothing is saved, and the real item is added when the throw arrives.
   */
  public static void returnFlight(Adaptation<?> owner, PlayerRef player, Loc impact, ItemRef item, int ticks) {
    ItemDisplayRef shown = impact.world().spawnItemDisplay(impact, item.copyWithAmount(1), (ItemDisplayRef display) -> {
      display.setPersistent(false);
      display.data().set(AxeThrow.FLUNG_KEY, (byte) 1);
      display.setGravity(false);
      display.setInvulnerable(true);
      display.setSilent(true);
      display.setShadowRadius(0F);
      display.setTeleportDuration(1);
      display.setTransform(Transform.IDENTITY.withScale(new Vec3(0.6D, 0.6D, 0.6D)));
    });
    if (shown == null) {
      return;
    }
    for (int step = 1; step <= ticks; step++) {
      double progress = (double) step / ticks;
      boolean last = step == ticks;
      AdaptApi.scheduler().runEntity(shown, () -> {
        if (!shown.isValid()) {
          return;
        }
        if (last || !player.isOnline() || !player.world().equals(impact.world())) {
          shown.remove();
          return;
        }
        Vec3 home = player.location().position().add(0D, 1.1D, 0D);
        Vec3 at = returnPoint(impact.position(), home, progress);
        shown.teleport(new Loc(impact.world(), at.x(), at.y(), at.z()), TeleportCause.PLUGIN);
      }, step);
    }
    Fx.now(owner, impact, FxPriority.TRAIL)
        .particle(Particles.itemCrack(item), 4, 0D, 0.2D, 0D, 0.15D, 0.02D);
  }

  /** A point on the hop from {@code from} to {@code to}: a straight line lifted by up to a block in the middle. */
  static Vec3 returnPoint(Vec3 from, Vec3 to, double progress) {
    Vec3 line = from.add(to.subtract(from).multiply(progress));
    return line.add(0D, Math.sin(Math.PI * progress), 0D);
  }

  /** The pickup pop when a returning block reaches its owner. */
  public static void returnFx(Adaptation<?> owner, PlayerRef player) {
    Fx.now(owner, player.location().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
        .sound(Sounds.ENTITY_ITEM_PICKUP, 0.6F, 1.2F);
  }

}
