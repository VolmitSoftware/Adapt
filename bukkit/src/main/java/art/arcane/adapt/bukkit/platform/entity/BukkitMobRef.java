package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitGoalsFactory;
import art.arcane.adapt.bukkit.platform.BukkitHosts;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;

public final class BukkitMobRef extends BukkitLivingRef implements MobRef {
  public BukkitMobRef(Mob mob) {
    super(mob);
  }

  public Mob mob() {
    return (Mob) handle();
  }

  @Override
  public LivingRef target() {
    LivingEntity target = mob().getTarget();
    return target == null ? null : BukkitRefs.living(target);
  }

  @Override
  public void setTarget(LivingRef target) {
    mob().setTarget(target == null ? null : BukkitRefs.living(target));
  }

  @Override
  public boolean isAware() {
    return mob().isAware();
  }

  @Override
  public void setAware(boolean aware) {
    mob().setAware(aware);
  }

  @Override
  public boolean pathTo(Loc destination, double speed) {
    Location target = BukkitRefs.location(destination);
    return target != null && PaperCompat.pathfindTo(mob(), target, speed);
  }

  @Override
  public boolean hasPath() {
    return PaperCompat.hasPath(mob());
  }

  @Override
  public void stopPathing() {
    PaperCompat.stopPathfinding(mob());
  }

  @Override
  public void lookAt(Loc target) {
    Location location = BukkitRefs.location(target);
    if (location != null) {
      mob().lookAt(location);
    }
  }

  @Override
  public boolean rangedAttack(LivingRef target) {
    LivingEntity living = target == null ? null : BukkitRefs.living(target);
    return living != null && PaperCompat.rangedAttack(mob(), living);
  }

  @Override
  public boolean isPlayerCreated() {
    return mob() instanceof IronGolem golem && golem.isPlayerCreated();
  }

  @Override
  public void setPlayerCreated(boolean created) {
    if (mob() instanceof IronGolem golem) {
      golem.setPlayerCreated(created);
    }
  }

  @Override
  public int weatheringStage() {
    return PaperCompat.weatheringStage(mob());
  }

  @Override
  public boolean isWaxed() {
    return PaperCompat.copperWaxed(mob());
  }

  @Override
  public void setWeathering(int stage, boolean waxed) {
    PaperCompat.restoreCopper(mob(), stage, waxed);
  }

  @Override
  public CopperGolemState copperGolemState() {
    return BukkitMobVersion.copperGolemState(mob());
  }

  @Override
  public void setCopperGolemState(CopperGolemState state) {
    BukkitMobVersion.setCopperGolemState(mob(), state);
  }

  @Override
  public void setBurnsInDaylight(boolean burns) {
    if (mob() instanceof AbstractSkeleton skeleton) {
      PaperCompat.setShouldBurnInDay(skeleton, burns);
    } else if (mob() instanceof Zombie zombie) {
      try {
        zombie.setShouldBurnInDay(burns);
      } catch (NoSuchMethodError ignored) {
      }
    }
  }

  @Override
  public MobGoalsRef goals() {
    BukkitGoalsFactory factory = BukkitHosts.goals();
    if (factory == null) {
      throw new IllegalStateException("No mob goals factory registered in BukkitHosts");
    }
    return factory.goals(mob());
  }
}
