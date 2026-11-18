package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModGoalsFactory;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.data.AdaptMobAwareness;
import art.arcane.adapt.mod.platform.data.AdaptSunBurn;
import art.arcane.adapt.mod.platform.mixin.CopperGolemAccess;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Loc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.block.WeatheringCopper;

public class ModMobRef extends ModLivingRef implements MobRef {
    private static final long WAXED_TICK = -2L;

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
        return mob() instanceof CopperGolem golem ? golem.getWeatherState().ordinal() : 0;
    }

    @Override
    public boolean isWaxed() {
        return mob() instanceof CopperGolem golem && ((CopperGolemAccess) golem).adapt$nextWeatheringTick() == WAXED_TICK;
    }

    @Override
    public void setWeathering(int stage, boolean waxed) {
        if (!(mob() instanceof CopperGolem golem)) {
            return;
        }
        WeatheringCopper.WeatherState[] states = WeatheringCopper.WeatherState.values();
        golem.setWeatherState(states[Math.max(0, Math.min(states.length - 1, stage))]);
        if (waxed) {
            ((CopperGolemAccess) golem).adapt$setNextWeatheringTick(WAXED_TICK);
        }
    }

    @Override
    public CopperGolemState copperGolemState() {
        return mob() instanceof CopperGolem golem ? CopperGolemState.valueOf(golem.getState().name()) : CopperGolemState.IDLE;
    }

    @Override
    public void setCopperGolemState(CopperGolemState state) {
        if (mob() instanceof CopperGolem golem && state != null) {
            golem.setState(net.minecraft.world.entity.animal.golem.CopperGolemState.valueOf(state.name()));
        }
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
