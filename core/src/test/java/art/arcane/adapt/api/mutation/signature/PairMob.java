package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.TestPlatform;

/** A mob for the pair signature tests that records its target and where it was told to walk. */
final class PairMob extends FakeLiving implements MobRef {
  private LivingRef target;
  private Loc pathedTo;

  PairMob(Id type, Loc at) {
    super(type, at);
  }

  /** A tamed wolf of {@code owner} at full health, tracked by the host. */
  static PairMob pet(TestPlatform platform, PlayerRef owner, double x, double z) {
    PairMob wolf = animal(platform, Id.minecraft("wolf"), x, z);
    wolf.trait(EntityTrait.TAMEABLE);
    wolf.setTameOwner(owner.uuid());
    return wolf;
  }

  /** An untamed animal at full 20 health, tracked by the host. */
  static PairMob animal(TestPlatform platform, Id type, double x, double z) {
    PairMob animal = platform.world().add(new PairMob(type, new Loc(platform.world(), x, 61D, z)));
    animal.trait(EntityTrait.ANIMAL);
    animal.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    animal.setHealth(20D);
    platform.host().track(animal);
    return animal;
  }

  Loc pathedTo() {
    return pathedTo;
  }

  @Override
  public LivingRef target() {
    return target;
  }

  @Override
  public void setTarget(LivingRef target) {
    this.target = target;
  }

  @Override
  public boolean isAware() {
    return true;
  }

  @Override
  public void setAware(boolean aware) {
  }

  @Override
  public boolean pathTo(Loc destination, double speed) {
    pathedTo = destination;
    return true;
  }

  @Override
  public boolean hasPath() {
    return pathedTo != null;
  }

  @Override
  public void stopPathing() {
    pathedTo = null;
  }

  @Override
  public void lookAt(Loc target) {
  }

  @Override
  public boolean rangedAttack(LivingRef target) {
    return false;
  }

  @Override
  public boolean isPlayerCreated() {
    return false;
  }

  @Override
  public void setPlayerCreated(boolean created) {
  }

  @Override
  public int weatheringStage() {
    return 0;
  }

  @Override
  public boolean isWaxed() {
    return false;
  }

  @Override
  public void setWeathering(int stage, boolean waxed) {
  }

  @Override
  public CopperGolemState copperGolemState() {
    return CopperGolemState.IDLE;
  }

  @Override
  public void setCopperGolemState(CopperGolemState state) {
  }

  @Override
  public void setBurnsInDaylight(boolean burns) {
  }

  @Override
  public MobGoalsRef goals() {
    return null;
  }
}
