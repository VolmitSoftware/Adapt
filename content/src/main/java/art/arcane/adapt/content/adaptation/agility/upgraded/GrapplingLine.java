package art.arcane.adapt.content.adaptation.agility.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.RayHit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skyrunner's grappling line: right-click a block in sight to hook it and be reeled in along an arc; right-click again
 * to let go and keep the momentum. Each grapple costs one durability and one of Skyrunner's mid-air charges.
 */
public final class GrapplingLine extends MutationTool<CustomItem.Plain> {
  private static final double ARRIVAL_DISTANCE = 1.5D;
  private static final double PLAYER_RAY_SIZE = 0.3D;

  private final Skyrunner owner;
  private final Map<UUID, Reel> reels;

  GrapplingLine(Skyrunner owner, Map<UUID, Reel> reels) {
    super(ToolItems.plain(Id.adapt("agility/tool/grappling_line"), Id.minecraft("fishing_rod")).build(), owner, 1);
    this.owner = owner;
    this.reels = reels;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shapeless(Id.adapt("agility/tool/grappling_line"), List.of(Ingredient.of(Id.minecraft("fishing_rod")), Ingredient.of(Id.minecraft("string")),
            Ingredient.of(Id.minecraft("iron_ingot"))), create(CustomItem.Plain.INSTANCE)).withRequiredLevel(1));
  }

  @Override
  public void applyLore(CustomItem.Plain data, List<String> lines) {
    lines.add(TextFormat.GRAY + Text.key("adapt.tool.agility.grappling_line.usage").resolve());
  }

  @Override
  public void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef p = use.player();
    if (release(p)) {
      return;
    }
    if (!eligible(p)) {
      return;
    }
    Vec3 hook = hookPoint(p, owner.getGrappleRange(use.level()));
    if (hook == null || !owner.spendGrappleCharge(p)) {
      return;
    }
    owner.damageHand(p, owner.getConfig().grappleDurabilityCost);
    start(p, hook, use.level());
  }

  /** The point on the first block in sight within {@code range}, or null when nothing is hooked or it may not be. */
  Vec3 hookPoint(PlayerRef p, double range) {
    Loc eye = p.eyeLocation();
    Vec3 direction = eye.direction();
    if (direction.lengthSquared() <= 1.0E-6D) {
      return null;
    }
    direction = direction.normalize();
    BlockHit hit = eye.world().rayTraceBlockHit(eye.position(), direction, range, false);
    if (hit == null) {
      return null;
    }
    double distance = hit.position().subtract(eye.position()).length();
    RayHit blocker = eye.world().rayTraceEntityHit(eye.position(), direction, distance, PLAYER_RAY_SIZE,
        (EntityRef entity) -> entity instanceof PlayerRef other && !other.uuid().equals(p.uuid()));
    if (blocker != null) {
      return null;
    }
    Loc at = new Loc(eye.world(), hit.position().x(), hit.position().y(), hit.position().z());
    if (!owner.canInteract(p, at) || !owner.checkRegion(p, at) || !ProtectionProbes.attemptBlockUse(p, hit.block())) {
      return null;
    }
    return hit.position();
  }

  boolean reeling(UUID id) {
    return !reels.isEmpty() && reels.containsKey(id);
  }

  /** Lets go of the line, keeping the current momentum; false when the player was not reeling. */
  boolean release(PlayerRef p) {
    Reel reel = reels.remove(p.uuid());
    if (reel == null) {
      return false;
    }
    if (reel.task != null) {
      reel.task.cancel();
    }
    return true;
  }

  private void start(PlayerRef p, Vec3 hook, int level) {
    double speed = owner.getReelSpeed(level);
    double distance = hook.subtract(p.location().position()).length();
    int maxTicks = Math.max(4, Math.min(owner.getConfig().maxReelTicks, (int) Math.ceil(distance / speed) + 10));
    Reel reel = new Reel(hook, maxTicks);
    reels.put(p.uuid(), reel);
    TaskHandle task = AdaptApi.scheduler().repeatEntity(p, () -> pull(p, reel), 1, 1, () -> reels.remove(p.uuid(), reel));
    if (task == null) {
      reels.remove(p.uuid(), reel);
      return;
    }
    reel.task = task;
    Fx.now(owner, new Loc(p.world(), hook.x(), hook.y(), hook.z()), FxPriority.GAMEPLAY)
        .ring(Particles.CRIT, 0.4D, 8, 0.05D)
        .chord(Sounds.ENTITY_FISHING_BOBBER_THROW, 0.7F, 1.4F, Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.8F);
    owner.grappled(p);
  }

  private void pull(PlayerRef p, Reel reel) {
    if (reels.get(p.uuid()) != reel) {
      if (reel.task != null) {
        reel.task.cancel();
      }
      return;
    }
    Vec3 toHook = reel.hook.subtract(p.location().add(0D, p.height() * 0.5D, 0D).position());
    double distance = toHook.length();
    reel.ticks++;
    if (!eligible(p) || distance <= ARRIVAL_DISTANCE || reel.ticks > reel.maxTicks) {
      release(p);
      return;
    }
    double speed = owner.getReelSpeed(owner.getActiveLevel(p));
    Vec3 pull = toHook.normalize().multiply(speed);
    Vec3 current = p.velocity();
    double carry = owner.getConfig().reelMomentumCarry;
    double lift = owner.getConfig().reelArcLift * Math.min(1D, distance / 8D);
    p.setVelocity(new Vec3((current.x() * carry) + (pull.x() * (1D - carry)),
        (current.y() * carry) + (pull.y() * (1D - carry)) + lift,
        (current.z() * carry) + (pull.z() * (1D - carry))));
    p.setFallDistance(0F);
  }

  private static boolean eligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !p.isDead() && !p.isFlying()
        && !p.isGliding() && p.vehicle() == null;
  }

  /** A running reel toward {@code hook}; {@code ticks} counts the pulls so far. */
  static final class Reel {
    private final Vec3 hook;
    private final int maxTicks;
    private int ticks;
    private TaskHandle task;

    Reel(Vec3 hook, int maxTicks) {
      this.hook = hook;
      this.maxTicks = maxTicks;
    }
  }
}
