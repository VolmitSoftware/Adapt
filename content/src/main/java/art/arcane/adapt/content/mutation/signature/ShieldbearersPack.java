package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.blocking.upgraded.LivingWall;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TameRegeneration;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Blocking and Taming, either leading, replacing Convergence: your own pets standing behind you while you block, or
 * behind your Planted Bulwark, take no projectile damage and slowly heal.
 */
public final class ShieldbearersPack extends Signature<ShieldbearersPack.Config> {
  public ShieldbearersPack(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shield"));
    setInterval(1000);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.f(depth(level), 0), 1);
    statLore(v, Format.f(healPerSecond(level), 1), 2);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled() || !(e.damager() instanceof ProjectileRef projectile) || !(e.entity() instanceof LivingRef pet)) {
      return;
    }
    UUID ownerId = pet.tameOwner();
    PlayerRef owner = ownerId == null ? null : AdaptApi.platform().host().player(ownerId);
    if (owner == null || !TamePets.isOwnedPet(pet, ownerId)) {
      return;
    }
    int level = getActiveLevel(owner);
    if (level <= 0 || !sheltered(owner, pet.location(), projectile.velocity(), level)) {
      return;
    }
    if (fire(owner, pet, null, false)) {
      e.setCancelled(true);
    }
  }

  @Override
  public void onTick() {
    for (PlayerProfile data : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef owner = data.player();
      if (owner != null) {
        AdaptApi.scheduler().runEntity(owner, () -> mend(owner));
      }
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    LivingRef pet = use.target();
    if (pet == null) {
      return Outcome.MISSED;
    }
    fx(pet.location().add(0, pet.height() * 0.6D, 0), FxPriority.COMBAT).burst(Particles.CRIT, 6, 0.3D);
    return Outcome.INSTANT;
  }

  private void mend(PlayerRef owner) {
    int level = getActiveLevel(owner);
    if (level <= 0 || !usable()) {
      return;
    }
    LivingWall wall = PairParts.owned(Id.adapt("blocking/upgrade/living_wall"), LivingWall.class);
    if (!owner.isBlocking() && (wall == null || !wall.hasPlanted(owner.uuid()))) {
      return;
    }
    double heal = healPerSecond(level);
    double reach = depth(level) + getConfig().halfWidthAtTopLevel + 1D;
    for (LivingRef pet : TamePets.near(owner, reach)) {
      if (pet.health() < pet.maxHealth() && sheltered(owner, pet.location(), null, level)) {
        AdaptApi.scheduler().runEntity(pet, () -> TameRegeneration.heal(this, pet, heal));
      }
    }
  }

  /** Behind the raised shield or the planted wall; with a shot's velocity, only when it comes from the front. */
  boolean sheltered(PlayerRef owner, Loc at, @Nullable Vec3 incoming, int level) {
    LivingWall wall = PairParts.owned(Id.adapt("blocking/upgrade/living_wall"), LivingWall.class);
    if (wall != null && wall.shelters(owner.uuid(), at, depth(level), incoming)) {
      return true;
    }
    return owner.isBlocking() && behindShield(owner.location(), at, incoming, depth(level), halfWidth(level));
  }

  double depth(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().depthAtFirstLevel, getConfig().depthAtTopLevel);
  }

  double halfWidth(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().halfWidthAtFirstLevel, getConfig().halfWidthAtTopLevel);
  }

  double healPerSecond(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().healPerSecondAtFirstLevel, getConfig().healPerSecondAtTopLevel);
  }

  /** Within {@code depth} behind a player facing their look, no wider than {@code halfWidth} to either side. */
  private static boolean behindShield(Loc owner, Loc at, @Nullable Vec3 incoming, double depth, double halfWidth) {
    Vec3 look = owner.direction().withY(0);
    Vec3 facing = look.lengthSquared() < 1.0E-6 ? new Vec3(0, 0, 1) : look.normalize();
    if (!at.sameWorld(owner) || Math.abs(at.y() - owner.y()) > 3D
        || (incoming != null && incoming.withY(0).dot(facing) >= 0D)) {
      return false;
    }
    Vec3 offset = at.position().subtract(owner.position()).withY(0);
    double behind = -offset.dot(facing);
    double side = Math.abs((offset.x() * facing.z()) - (offset.z() * facing.x()));
    return behind > 0.3D && behind <= depth && side <= halfWidth;
  }

  @ConfigDescription("Your pets behind your raised shield or Planted Bulwark take no projectile damage and slowly heal.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "How far behind you or the wall a pet is covered at level 1, in blocks.", impact = "Higher values cover pets standing farther back.")
    double depthAtFirstLevel = 3;
    @ConfigDoc(value = "How far behind you or the wall a pet is covered at the top level, in blocks.", impact = "Higher values cover pets standing farther back.")
    double depthAtTopLevel = 6;
    @ConfigDoc(value = "How far to either side a pet is covered at level 1, in blocks.", impact = "Higher values cover a wider pack.")
    double halfWidthAtFirstLevel = 1.5;
    @ConfigDoc(value = "How far to either side a pet is covered at the top level, in blocks.", impact = "Higher values cover a wider pack.")
    double halfWidthAtTopLevel = 2.5;
    @ConfigDoc(value = "Health a covered pet regains each second at level 1.", impact = "Higher values heal covered pets faster.")
    double healPerSecondAtFirstLevel = 0.5;
    @ConfigDoc(value = "Health a covered pet regains each second at the top level.", impact = "Higher values heal covered pets faster.")
    double healPerSecondAtTopLevel = 1.5;
    @ConfigDoc(value = "Cooldown after a stopped shot, in milliseconds.", impact = "Higher values let shots through between covers.")
    long cooldownMillis = 0;
  }
}
