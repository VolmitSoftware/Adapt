package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;

/** A hostile mob with a target, for the Stealth signature tests. */
final class StealthSignatureMob extends FakeLiving implements MobRef {
  private LivingRef target;
  private boolean aware = true;

  private StealthSignatureMob(Loc location) {
    super(Id.minecraft("pillager"), location);
    trait(EntityTrait.MONSTER);
    trait(EntityTrait.ENEMY);
    setAttributeBase(Attributes.MAX_HEALTH, 20D);
  }

  static StealthSignatureMob at(FakeWorld world, double x, double y, double z) {
    return world.add(new StealthSignatureMob(new Loc(world, x, y, z)));
  }

  /** Stone under the area the tests stand and step in. */
  static void floor(FakeWorld world) {
    for (int x = -2; x <= 8; x++) {
      for (int z = -4; z <= 4; z++) {
        world.set(x, 60, z, "stone");
      }
    }
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
    return aware;
  }

  @Override
  public void setAware(boolean aware) {
    this.aware = aware;
  }

  @Override
  public boolean pathTo(Loc destination, double speed) {
    return false;
  }

  @Override
  public boolean hasPath() {
    return false;
  }

  @Override
  public void stopPathing() {
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
    return null;
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
