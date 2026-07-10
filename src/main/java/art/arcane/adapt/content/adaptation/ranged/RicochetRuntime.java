package art.arcane.adapt.content.adaptation.ranged;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.List;
import java.util.Set;

record RicochetProfile(int maximumRicochets, double speedBonusPerRicochet,
                        double damageBonusPerRicochet, double minimumVelocitySquared,
                        double minimumPostBounceSpeed) {
  RicochetProfile {
    maximumRicochets = Math.max(0, maximumRicochets);
    speedBonusPerRicochet = normalizeNonnegative(speedBonusPerRicochet);
    damageBonusPerRicochet = normalizeNonnegative(damageBonusPerRicochet);
    minimumVelocitySquared = normalizeNonnegative(minimumVelocitySquared);
    minimumPostBounceSpeed = normalizeNonnegative(minimumPostBounceSpeed);
  }

  RicochetProfile withMaximumRicochets(int maximum) {
    return new RicochetProfile(
        Math.max(0, maximum),
        speedBonusPerRicochet,
        damageBonusPerRicochet,
        minimumVelocitySquared,
        minimumPostBounceSpeed
    );
  }

  RicochetTransition next(int currentCount, double currentBonusDamage, Vector incoming,
                          Vector surfaceNormal) {
    int count = Math.max(0, currentCount);
    if (count >= maximumRicochets || incoming.lengthSquared() < minimumVelocitySquared) {
      return null;
    }

    Vector direction = incoming.clone().normalize();
    Vector normal = surfaceNormal.clone().normalize();
    Vector reflected = direction.subtract(normal.multiply(2D * direction.dot(normal)));
    if (reflected.lengthSquared() <= 0.0000001D) {
      return null;
    }
    reflected.normalize();
    double nextSpeed = Math.max(minimumPostBounceSpeed, incoming.length())
        * (1D + speedBonusPerRicochet);
    if (!Double.isFinite(nextSpeed) || nextSpeed <= 0D) {
      return null;
    }
    double currentDamage = normalizeNonnegative(currentBonusDamage);
    double bonusDamage = currentDamage + damageBonusPerRicochet;
    return new RicochetTransition(reflected, nextSpeed, count + 1, bonusDamage);
  }

  private static double normalizeNonnegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }
}

record RicochetTransition(Vector direction, double speed, int count, double bonusDamage) {
}

enum RicochetProjectileKind {
  SPECTRAL_ARROW(SpectralArrow.class),
  TRIDENT(Trident.class),
  ARROW(Arrow.class),
  SNOWBALL(Snowball.class),
  EGG(Egg.class),
  ENDER_PEARL(EnderPearl.class),
  LINGERING_POTION(LingeringPotion.class),
  SPLASH_POTION(SplashPotion.class),
  THROWN_POTION(ThrownPotion.class),
  EXPERIENCE_BOTTLE(ThrownExpBottle.class);

  private final Class<? extends Projectile> projectileClass;

  RicochetProjectileKind(Class<? extends Projectile> projectileClass) {
    this.projectileClass = projectileClass;
  }

  Class<? extends Projectile> projectileClass() {
    return projectileClass;
  }

  static RicochetProjectileKind resolve(Projectile projectile) {
    if (projectile instanceof SpectralArrow) {
      return SPECTRAL_ARROW;
    }
    if (projectile instanceof Trident) {
      return TRIDENT;
    }
    if (projectile instanceof Arrow) {
      return ARROW;
    }
    if (projectile instanceof Snowball) {
      return SNOWBALL;
    }
    if (projectile instanceof Egg) {
      return EGG;
    }
    if (projectile instanceof EnderPearl) {
      return ENDER_PEARL;
    }
    if (projectile instanceof LingeringPotion) {
      return LINGERING_POTION;
    }
    if (projectile instanceof SplashPotion) {
      return SPLASH_POTION;
    }
    if (projectile instanceof ThrownPotion) {
      return THROWN_POTION;
    }
    if (projectile instanceof ThrownExpBottle) {
      return EXPERIENCE_BOTTLE;
    }
    return null;
  }
}

final class RicochetTransferRules {
  private RicochetTransferRules() {
  }

  static boolean requiresRegionHandoff(boolean foliaThreading, boolean destinationOwned) {
    return foliaThreading && !destinationOwned;
  }
}

final class RicochetPersistentData {
  private RicochetPersistentData() {
  }

  static byte[] snapshot(PersistentDataContainer container) throws IOException {
    return container.isEmpty() ? null : container.serializeToBytes();
  }

  static void restore(PersistentDataContainer container, byte[] data) throws IOException {
    if (data != null) {
      container.readFromBytes(data, true);
    }
  }
}

record RicochetCommonState(
    boolean gravity,
    int fireTicks,
    boolean leftShooter,
    boolean beenShot,
    boolean persistent,
    int ticksLived,
    float fallDistance,
    TriState visualFire,
    int freezeTicks,
    boolean silent,
    boolean glowing,
    boolean invulnerable,
    boolean invisible,
    boolean noPhysics,
    int portalCooldown,
    Component customName,
    boolean customNameVisible,
    boolean visibleByDefault,
    Set<String> scoreboardTags,
    int heavyDrawLevel,
    byte[] persistentData
) {
  RicochetCommonState {
    scoreboardTags = Set.copyOf(scoreboardTags);
    persistentData = persistentData == null ? null : persistentData.clone();
  }

  @Override
  public byte[] persistentData() {
    return persistentData == null ? null : persistentData.clone();
  }
}

record RicochetArrowState(
    double damage,
    boolean critical,
    int pierceLevel,
    int knockbackStrength,
    AbstractArrow.PickupStatus pickupStatus,
    boolean piercingInitialized,
    int piercingHits,
    ItemStack item,
    ItemStack weapon,
    int lifetimeTicks,
    Sound hitSound
) {
  RicochetArrowState {
    item = item.clone();
    weapon = weapon == null ? null : weapon.clone();
  }

  @Override
  public ItemStack item() {
    return item.clone();
  }

  @Override
  public ItemStack weapon() {
    return weapon == null ? null : weapon.clone();
  }
}

sealed interface RicochetProjectilePayload permits RicochetArrowPayload,
    RicochetSpectralPayload, RicochetTridentPayload, RicochetThrowablePayload {
}

record RicochetArrowPayload(
    RicochetArrowState arrow,
    PotionType basePotionType,
    Color color,
    List<PotionEffect> customEffects
) implements RicochetProjectilePayload {
  RicochetArrowPayload {
    customEffects = List.copyOf(customEffects);
  }
}

record RicochetSpectralPayload(
    RicochetArrowState arrow,
    int glowingTicks
) implements RicochetProjectilePayload {
}

record RicochetTridentPayload(
    RicochetArrowState arrow,
    ItemStack item,
    boolean glint,
    int loyaltyLevel
) implements RicochetProjectilePayload {
  RicochetTridentPayload {
    item = item.clone();
  }

  @Override
  public ItemStack item() {
    return item.clone();
  }
}

record RicochetThrowablePayload(ItemStack item) implements RicochetProjectilePayload {
  RicochetThrowablePayload {
    item = item.clone();
  }

  @Override
  public ItemStack item() {
    return item.clone();
  }
}

record RicochetProjectileTemplate(
    RicochetProjectileKind kind,
    World world,
    Location spawnLocation,
    Vector velocity,
    Player shooter,
    RicochetProfile profile,
    int count,
    int maximum,
    double bonusDamage,
    Location hitCenter,
    RicochetTransition transition,
    RicochetCommonState common,
    RicochetProjectilePayload payload
) {
  RicochetProjectileTemplate {
    spawnLocation = spawnLocation.clone();
    velocity = velocity.clone();
    hitCenter = hitCenter.clone();
    transition = new RicochetTransition(
        transition.direction().clone(),
        transition.speed(),
        transition.count(),
        transition.bonusDamage()
    );
  }

  @Override
  public Location spawnLocation() {
    return spawnLocation.clone();
  }

  @Override
  public Vector velocity() {
    return velocity.clone();
  }

  @Override
  public Location hitCenter() {
    return hitCenter.clone();
  }

  @Override
  public RicochetTransition transition() {
    return new RicochetTransition(
        transition.direction().clone(),
        transition.speed(),
        transition.count(),
        transition.bonusDamage()
    );
  }

  RicochetProjectileTemplate withSpawnLocation(Location replacement) {
    return new RicochetProjectileTemplate(
        kind,
        world,
        replacement,
        velocity,
        shooter,
        profile,
        count,
        maximum,
        bonusDamage,
        hitCenter,
        transition,
        common,
        payload
    );
  }
}

record RicochetTransferRequest(
    RicochetProjectileTemplate primary,
    RicochetProjectileTemplate fallback
) {
}
