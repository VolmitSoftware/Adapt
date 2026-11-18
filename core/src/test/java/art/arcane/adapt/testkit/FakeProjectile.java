package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Projectile; arrows are minecraft:arrow, spectral_arrow and trident. */
public final class FakeProjectile extends FakeEntity implements ProjectileRef {
  private static final Set<Id> ARROWS = Set.of(Id.minecraft("arrow"), Id.minecraft("spectral_arrow"), Id.minecraft("trident"));

  private EntityRef shooter;
  private ItemRef item = FakeItem.empty();
  private double damage = 2D;
  private boolean critical;
  private ArrowPickup pickupRule = ArrowPickup.ALLOWED;
  private int pierceLevel;
  private boolean inBlock;
  private boolean shotFromCrossbow;
  private float yaw;
  private float pitch;
  private PotionContents arrowPotion = PotionContents.EMPTY;
  private int glowingTicks = 200;

  public FakeProjectile(Id type, Loc location) {
    super(type, location);
  }

  public boolean isArrow() {
    return ARROWS.contains(type());
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public void setInBlock(boolean inBlock) {
    this.inBlock = inBlock;
  }

  public void setShotFromCrossbow(boolean shotFromCrossbow) {
    this.shotFromCrossbow = shotFromCrossbow;
  }

  @Override
  public EntityRef shooter() {
    return shooter;
  }

  @Override
  public UUID shooterId() {
    return shooter == null ? null : shooter.uuid();
  }

  @Override
  public void setShooter(EntityRef shooter) {
    this.shooter = shooter;
  }

  @Override
  public ItemRef item() {
    return item;
  }

  @Override
  public void setItem(ItemRef item) {
    this.item = item == null ? FakeItem.empty() : item;
  }

  @Override
  public double damage() {
    return isArrow() ? damage : Double.NaN;
  }

  @Override
  public void setDamage(double damage) {
    this.damage = damage;
  }

  @Override
  public boolean isCritical() {
    return critical;
  }

  @Override
  public void setCritical(boolean critical) {
    this.critical = critical;
  }

  @Override
  public boolean canPickup() {
    return isArrow() && pickupRule == ArrowPickup.ALLOWED;
  }

  @Override
  public void setCanPickup(boolean pickup) {
    pickupRule = pickup ? ArrowPickup.ALLOWED : ArrowPickup.DISALLOWED;
  }

  @Override
  public ArrowPickup pickupRule() {
    return isArrow() ? pickupRule : ArrowPickup.DISALLOWED;
  }

  @Override
  public void setPickupRule(ArrowPickup rule) {
    pickupRule = rule;
  }

  @Override
  public int pierceLevel() {
    return isArrow() ? pierceLevel : 0;
  }

  @Override
  public void setPierceLevel(int level) {
    pierceLevel = Math.max(0, Math.min(127, level));
  }

  @Override
  public boolean isInBlock() {
    return inBlock;
  }

  @Override
  public boolean shotFromCrossbow() {
    return shotFromCrossbow;
  }

  @Override
  public void setRotation(float yaw, float pitch) {
    this.yaw = yaw;
    this.pitch = pitch;
  }

  @Override
  public PotionContents arrowPotion() {
    return arrowPotion;
  }

  @Override
  public void setArrowPotion(PotionContents contents) {
    arrowPotion = contents;
  }

  @Override
  public int glowingTicks() {
    return glowingTicks;
  }

  @Override
  public void setGlowingTicks(int ticks) {
    glowingTicks = ticks;
  }

  @Override
  public ProjectileSnapshot snapshot() {
    FakeProjectile source = this;
    return new ProjectileSnapshot() {
      @Override
      public Id type() {
        return source.type();
      }

      @Override
      public ItemRef item() {
        return source.item;
      }

      @Override
      public ProjectileRef spawn(Loc location, Vec3 velocity, PlayerRef shooter, Consumer<ProjectileRef> beforeAdd) {
        FakeProjectile copy = new FakeProjectile(source.type(), location);
        copy.shooter = shooter;
        copy.item = source.item;
        copy.damage = source.damage;
        copy.critical = source.critical;
        copy.pickupRule = source.pickupRule;
        copy.pierceLevel = source.pierceLevel;
        copy.arrowPotion = source.arrowPotion;
        copy.glowingTicks = source.glowingTicks;
        copy.setVelocity(velocity);
        if (beforeAdd != null) {
          beforeAdd.accept(copy);
        }
        return ((FakeWorld) location.world()).add(copy);
      }
    };
  }
}
