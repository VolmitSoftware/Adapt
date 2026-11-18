package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.compat.PaperCompat;
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
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Projectile state copied field by field, so the copy can be hydrated before it enters the world in another region. */
final class BukkitProjectileSnapshot implements ProjectileSnapshot {
  private final Kind kind;
  private final CommonState common;
  private final Payload payload;

  private BukkitProjectileSnapshot(Kind kind, CommonState common, Payload payload) {
    this.kind = kind;
    this.common = common;
    this.payload = payload;
  }

  static BukkitProjectileSnapshot capture(Projectile source) {
    Kind kind = Kind.resolve(source);
    Payload payload = capturePayload(source);
    if (kind == null || payload == null) {
      return null;
    }

    CommonState common = new CommonState(
        source.hasGravity(),
        source.getFireTicks(),
        PaperCompat.hasLeftShooter(source),
        PaperCompat.hasBeenShot(source),
        source.isPersistent(),
        source.getTicksLived(),
        source.getFallDistance(),
        PaperCompat.visualFire(source),
        source.getFreezeTicks(),
        source.isSilent(),
        source.isGlowing(),
        source.isInvulnerable(),
        PaperCompat.isInvisible(source),
        PaperCompat.hasNoPhysics(source),
        source.getPortalCooldown(),
        PaperCompat.customName(source),
        source.isCustomNameVisible(),
        source.isVisibleByDefault(),
        source.getScoreboardTags(),
        PersistentData.snapshot(source.getPersistentDataContainer())
    );
    return new BukkitProjectileSnapshot(kind, common, payload);
  }

  @Override
  public Id type() {
    return kind.type;
  }

  @Override
  public ItemRef item() {
    ItemStack item = switch (payload) {
      case TridentPayload trident -> trident.item();
      case ThrowablePayload throwable -> throwable.item();
      case ArrowPayload arrow -> arrow.arrow().item();
      case SpectralPayload spectral -> spectral.arrow().item();
    };
    return item == null ? BukkitRefs.emptyItem() : BukkitRefs.item(item);
  }

  @Override
  public ProjectileRef spawn(Loc location, Vec3 velocity, PlayerRef shooter, Consumer<ProjectileRef> beforeAdd) {
    Location spawnLocation = BukkitRefs.location(location);
    World world = spawnLocation == null ? null : spawnLocation.getWorld();
    if (world == null) {
      return null;
    }
    Player owner = shooter == null ? null : BukkitRefs.player(shooter);
    Projectile target = null;
    try {
      target = spawnHydrated(world, spawnLocation, kind.projectileClass, owner, BukkitConversions.vector(velocity), beforeAdd);
      if (!target.isInWorld()) {
        return null;
      }
    } catch (RuntimeException exception) {
      if (target != null && target.isInWorld()) {
        target.remove();
      }
      throw exception;
    }
    return BukkitRefs.projectile(target);
  }

  private <T extends Projectile> T spawnHydrated(World world, Location location, Class<T> type, Player owner,
                                                 org.bukkit.util.Vector velocity, Consumer<ProjectileRef> beforeAdd) {
    return PaperCompat.spawnHydrated(world, location, type, (T created) -> {
      applyCommonState(created, owner, velocity);
      applyPayload(created);
      if (beforeAdd != null) {
        beforeAdd.accept(BukkitRefs.projectile(created));
      }
    });
  }

  private void applyCommonState(Projectile target, Player shooter, org.bukkit.util.Vector velocity) {
    if (target instanceof AbstractArrow arrow) {
      PaperCompat.setArrowShooter(arrow, shooter);
    } else {
      target.setShooter(shooter);
    }
    target.setGravity(common.gravity());
    target.setFireTicks(common.fireTicks());
    PaperCompat.setHasLeftShooter(target, common.leftShooter());
    PaperCompat.setHasBeenShot(target, common.beenShot());
    target.setPersistent(common.persistent());
    target.setTicksLived(Math.max(1, common.ticksLived()));
    target.setFallDistance(common.fallDistance());
    PaperCompat.setVisualFire(target, common.visualFire());
    target.setFreezeTicks(common.freezeTicks());
    target.setSilent(common.silent());
    target.setGlowing(common.glowing());
    target.setInvulnerable(common.invulnerable());
    PaperCompat.setInvisible(target, common.invisible());
    PaperCompat.setNoPhysics(target, common.noPhysics());
    target.setPortalCooldown(common.portalCooldown());
    PaperCompat.customName(target, common.customName());
    target.setCustomNameVisible(common.customNameVisible());
    target.setVisibleByDefault(common.visibleByDefault());
    for (String tag : common.scoreboardTags()) {
      target.addScoreboardTag(tag);
    }
    PersistentData.restore(target.getPersistentDataContainer(), common.persistentData());
    target.setVelocity(velocity);
  }

  private void applyPayload(Projectile target) {
    if (payload instanceof ArrowPayload arrowPayload && target instanceof Arrow arrow) {
      applyArrowState(arrow, arrowPayload.arrow());
      arrow.setBasePotionType(arrowPayload.basePotionType());
      arrow.setColor(arrowPayload.color());
      for (PotionEffect effect : arrowPayload.customEffects()) {
        arrow.addCustomEffect(effect, true);
      }
      return;
    }
    if (payload instanceof SpectralPayload spectralPayload && target instanceof SpectralArrow spectral) {
      applyArrowState(spectral, spectralPayload.arrow());
      spectral.setGlowingTicks(spectralPayload.glowingTicks());
      return;
    }
    if (payload instanceof TridentPayload tridentPayload && target instanceof Trident trident) {
      applyArrowState(trident, tridentPayload.arrow());
      trident.setItem(tridentPayload.item());
      PaperCompat.setTridentGlint(trident, tridentPayload.glint());
      PaperCompat.setTridentLoyalty(trident, tridentPayload.loyaltyLevel());
      PaperCompat.clearTridentDealtDamage(trident);
      return;
    }
    if (payload instanceof ThrowablePayload throwablePayload && target instanceof ThrowableProjectile throwable) {
      throwable.setItem(throwablePayload.item());
    }
  }

  private static void applyArrowState(AbstractArrow target, ArrowState state) {
    target.setDamage(state.damage());
    target.setCritical(state.critical());
    target.setPierceLevel(state.pierceLevel());
    target.setKnockbackStrength(state.knockbackStrength());
    target.setPickupStatus(state.pickupStatus());
    PaperCompat.setArrowItem(target, state.item());
    ItemStack weapon = state.weapon();
    if (weapon != null) {
      target.setWeapon(weapon);
    }
    PaperCompat.setArrowLifetimeTicks(target, state.lifetimeTicks());
    PaperCompat.setArrowHitSound(target, state.hitSound());
  }

  private static Payload capturePayload(Projectile source) {
    if (source instanceof SpectralArrow spectral) {
      return new SpectralPayload(captureArrowState(spectral), spectral.getGlowingTicks());
    }
    if (source instanceof Trident trident) {
      return new TridentPayload(
          captureArrowState(trident),
          trident.getItem(),
          PaperCompat.tridentGlint(trident),
          PaperCompat.tridentLoyalty(trident)
      );
    }
    if (source instanceof Arrow arrow) {
      return new ArrowPayload(
          captureArrowState(arrow),
          arrow.getBasePotionType(),
          arrow.getColor(),
          arrow.getCustomEffects()
      );
    }
    if (source instanceof ThrowableProjectile throwable) {
      return new ThrowablePayload(throwable.getItem());
    }
    return null;
  }

  private static ArrowState captureArrowState(AbstractArrow arrow) {
    return new ArrowState(
        arrow.getDamage(),
        arrow.isCritical(),
        arrow.getPierceLevel(),
        arrow.getKnockbackStrength(),
        arrow.getPickupStatus(),
        PaperCompat.arrowItem(arrow),
        arrow.getWeapon(),
        PaperCompat.arrowLifetimeTicks(arrow),
        PaperCompat.arrowHitSound(arrow)
    );
  }

  enum Kind {
    SPECTRAL_ARROW(SpectralArrow.class, "spectral_arrow"),
    TRIDENT(Trident.class, "trident"),
    ARROW(Arrow.class, "arrow"),
    SNOWBALL(Snowball.class, "snowball"),
    EGG(Egg.class, "egg"),
    ENDER_PEARL(EnderPearl.class, "ender_pearl"),
    LINGERING_POTION(LingeringPotion.class, "lingering_potion"),
    SPLASH_POTION(SplashPotion.class, "splash_potion"),
    THROWN_POTION(ThrownPotion.class, "potion"),
    EXPERIENCE_BOTTLE(ThrownExpBottle.class, "experience_bottle");

    private final Class<? extends Projectile> projectileClass;
    private final Id type;

    Kind(Class<? extends Projectile> projectileClass, String type) {
      this.projectileClass = projectileClass;
      this.type = Id.minecraft(type);
    }

    Class<? extends Projectile> projectileClass() {
      return projectileClass;
    }

    static Kind resolve(Projectile projectile) {
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

  static final class PersistentData {
    private PersistentData() {
    }

    // Detached-container copy instead of Paper's serializeToBytes/readFromBytes.
    static PersistentDataContainer snapshot(PersistentDataContainer container) {
      if (container.isEmpty()) {
        return null;
      }

      PersistentDataContainer copy = container.getAdapterContext().newPersistentDataContainer();
      container.copyTo(copy, true);
      return copy;
    }

    static void restore(PersistentDataContainer container, PersistentDataContainer data) {
      if (data != null) {
        data.copyTo(container, true);
      }
    }
  }

  record CommonState(
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
      PersistentDataContainer persistentData
  ) {
    CommonState {
      scoreboardTags = Set.copyOf(scoreboardTags);
    }
  }

  record ArrowState(
      double damage,
      boolean critical,
      int pierceLevel,
      int knockbackStrength,
      AbstractArrow.PickupStatus pickupStatus,
      ItemStack item,
      ItemStack weapon,
      int lifetimeTicks,
      Sound hitSound
  ) {
    ArrowState {
      // item is null on runtimes without AbstractArrow#getItemStack.
      item = item == null ? null : item.clone();
      weapon = weapon == null ? null : weapon.clone();
    }

    @Override
    public ItemStack item() {
      return item == null ? null : item.clone();
    }

    @Override
    public ItemStack weapon() {
      return weapon == null ? null : weapon.clone();
    }
  }

  sealed interface Payload permits ArrowPayload, SpectralPayload, TridentPayload, ThrowablePayload {
  }

  record ArrowPayload(ArrowState arrow, PotionType basePotionType, Color color, List<PotionEffect> customEffects)
      implements Payload {
    ArrowPayload {
      customEffects = List.copyOf(customEffects);
    }
  }

  record SpectralPayload(ArrowState arrow, int glowingTicks) implements Payload {
  }

  record TridentPayload(ArrowState arrow, ItemStack item, boolean glint, int loyaltyLevel) implements Payload {
    TridentPayload {
      item = item.clone();
    }

    @Override
    public ItemStack item() {
      return item.clone();
    }
  }

  record ThrowablePayload(ItemStack item) implements Payload {
    ThrowablePayload {
      item = item.clone();
    }

    @Override
    public ItemStack item() {
      return item.clone();
    }
  }
}
