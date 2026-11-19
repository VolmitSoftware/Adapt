package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BukkitProjectileRef extends BukkitEntityRef implements ProjectileRef {
  public BukkitProjectileRef(Projectile projectile) {
    super(projectile);
  }

  public Projectile projectile() {
    return (Projectile) handle();
  }

  @Override
  public EntityRef shooter() {
    ProjectileSource source = projectile().getShooter();
    return source instanceof Entity entity ? BukkitRefs.entity(entity) : null;
  }

  @Override
  public UUID shooterId() {
    UUID owner = projectile().getOwnerUniqueId();
    if (owner != null) {
      return owner;
    }
    return projectile().getShooter() instanceof Entity entity ? entity.getUniqueId() : null;
  }

  @Override
  public void setShooter(EntityRef shooter) {
    Entity entity = shooter == null ? null : BukkitRefs.entity(shooter);
    projectile().setShooter(entity instanceof ProjectileSource source ? source : null);
  }

  @Override
  public ItemRef item() {
    Projectile projectile = projectile();
    if (projectile instanceof AbstractArrow arrow) {
      return BukkitRefs.item(PaperCompat.arrowItem(arrow));
    }
    if (projectile instanceof ThrowableProjectile throwable) {
      return BukkitRefs.item(throwable.getItem());
    }
    return BukkitRefs.emptyItem();
  }

  @Override
  public void setItem(ItemRef item) {
    if (projectile() instanceof ThrowableProjectile throwable) {
      throwable.setItem(BukkitRefs.item(item));
    }
  }

  @Override
  public double damage() {
    return projectile() instanceof AbstractArrow arrow ? arrow.getDamage() : Double.NaN;
  }

  @Override
  public void setDamage(double damage) {
    if (projectile() instanceof AbstractArrow arrow) {
      arrow.setDamage(damage);
    }
  }

  @Override
  public boolean isCritical() {
    return projectile() instanceof AbstractArrow arrow && arrow.isCritical();
  }

  @Override
  public void setCritical(boolean critical) {
    if (projectile() instanceof AbstractArrow arrow) {
      arrow.setCritical(critical);
    }
  }

  @Override
  public boolean canPickup() {
    return projectile() instanceof AbstractArrow arrow && arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED;
  }

  @Override
  public void setCanPickup(boolean pickup) {
    if (projectile() instanceof AbstractArrow arrow) {
      arrow.setPickupStatus(pickup ? AbstractArrow.PickupStatus.ALLOWED : AbstractArrow.PickupStatus.DISALLOWED);
    }
  }

  @Override
  public ArrowPickup pickupRule() {
    if (!(projectile() instanceof AbstractArrow arrow)) {
      return ArrowPickup.DISALLOWED;
    }
    return switch (arrow.getPickupStatus()) {
      case ALLOWED -> ArrowPickup.ALLOWED;
      case CREATIVE_ONLY -> ArrowPickup.CREATIVE_ONLY;
      case DISALLOWED -> ArrowPickup.DISALLOWED;
    };
  }

  @Override
  public void setPickupRule(ArrowPickup rule) {
    if (projectile() instanceof AbstractArrow arrow) {
      arrow.setPickupStatus(switch (rule) {
        case ALLOWED -> AbstractArrow.PickupStatus.ALLOWED;
        case CREATIVE_ONLY -> AbstractArrow.PickupStatus.CREATIVE_ONLY;
        case DISALLOWED -> AbstractArrow.PickupStatus.DISALLOWED;
      });
    }
  }

  @Override
  public int pierceLevel() {
    return projectile() instanceof AbstractArrow arrow ? arrow.getPierceLevel() : 0;
  }

  @Override
  public void setPierceLevel(int level) {
    if (projectile() instanceof AbstractArrow arrow) {
      arrow.setPierceLevel(Math.max(0, Math.min(127, level)));
    }
  }

  @Override
  public boolean isInBlock() {
    return projectile() instanceof AbstractArrow arrow && arrow.isInBlock();
  }

  @Override
  public boolean shotFromCrossbow() {
    return projectile() instanceof AbstractArrow arrow && arrow.isShotFromCrossbow();
  }

  @Override
  public void setRotation(float yaw, float pitch) {
    projectile().setRotation(yaw, pitch);
  }

  @Override
  public PotionContents arrowPotion() {
    if (!(projectile() instanceof Arrow arrow)) {
      return PotionContents.EMPTY;
    }
    List<EffectInstance> effects = new ArrayList<>();
    for (PotionEffect effect : arrow.getCustomEffects()) {
      effects.add(new EffectInstance(BukkitResolve.id(effect.getType()),
          effect.isInfinite() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier(), effect.isAmbient(),
          effect.hasParticles(), effect.hasIcon()));
    }
    return new PotionContents(BukkitProjectileVersion.basePotion(arrow), null, effects, null);
  }

  @Override
  public void setArrowPotion(PotionContents contents) {
    if (!(projectile() instanceof Arrow arrow)) {
      return;
    }
    BukkitProjectileVersion.setBasePotion(arrow, contents.potion());
    for (EffectInstance effect : contents.customEffects()) {
      PotionEffectType type = BukkitResolve.effect(effect.type());
      if (type != null) {
        int duration = effect.isInfinite() ? PotionEffect.INFINITE_DURATION : effect.durationTicks();
        arrow.addCustomEffect(new PotionEffect(type, duration, effect.amplifier(), effect.ambient(), effect.particles(),
            effect.icon()), true);
      }
    }
  }

  @Override
  public int glowingTicks() {
    return projectile() instanceof SpectralArrow spectral ? spectral.getGlowingTicks() : 0;
  }

  @Override
  public void setGlowingTicks(int ticks) {
    if (projectile() instanceof SpectralArrow spectral) {
      spectral.setGlowingTicks(ticks);
    }
  }

  @Override
  public ProjectileSnapshot snapshot() {
    return BukkitProjectileSnapshot.capture(projectile());
  }
}
