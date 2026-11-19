package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModGoalsFactory;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.data.AdaptMobAwareness;
import art.arcane.adapt.mod.platform.data.AdaptSunBurn;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Loc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.RangedAttackMob;

public class ModMobRef extends ModLivingRef implements MobRef {
    public ModMobRef(Mob mob) {
        super(mob);
    }

    public Mob mob() {
        return (Mob) handle();
    }

    @Override
    public LivingRef target() {
        LivingEntity target = mob().getTarget();
        return target == null ? null : ModRefs.living(target);
    }

    @Override
    public void setTarget(LivingRef target) {
        mob().setTarget(target == null ? null : ModRefs.living(target));
    }

    @Override
    public boolean isAware() {
        return ((AdaptMobAwareness) mob()).adapt$isAware();
    }

    @Override
    public void setAware(boolean aware) {
        ((AdaptMobAwareness) mob()).adapt$setAware(aware);
    }

    @Override
    public boolean pathTo(Loc destination, double speed) {
        return mob().getNavigation().moveTo(destination.x(), destination.y(), destination.z(), speed);
    }

    @Override
    public boolean hasPath() {
        return !mob().getNavigation().isDone();
    }

    @Override
    public void stopPathing() {
        mob().getNavigation().stop();
    }

    @Override
    public void lookAt(Loc target) {
        mob().getLookControl().setLookAt(target.x(), target.y(), target.z());
    }

    @Override
    public boolean rangedAttack(LivingRef target) {
        LivingEntity living = target == null ? null : ModRefs.living(target);
        if (living == null || !(mob() instanceof RangedAttackMob ranged)) {
            return false;
        }
        ranged.performRangedAttack(living, 1F);
        return true;
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
        ((AdaptSunBurn) mob()).adapt$setBurnsInDaylight(burns);
    }

    @Override
    public MobGoalsRef goals() {
        ModGoalsFactory factory = ModHosts.goals();
        if (factory == null) {
            throw new IllegalStateException("No mob goal factory registered");
        }
        return factory.goals(mob());
    }
}
