package art.arcane.adapt.mod.feature.ai;

import art.arcane.adapt.platform.value.Id;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaperGoalKeysTest {
    private static final List<Class<? extends Goal>> GOLEM_GOALS = List.of(
            AvoidEntityGoal.class, DefendVillageTargetGoal.class, FleeSunGoal.class, GolemRandomStrollInVillageGoal.class,
            HurtByTargetGoal.class, MeleeAttackGoal.class, MoveBackToVillageGoal.class, NearestAttackableTargetGoal.class,
            OfferFlowerGoal.class, RangedAttackGoal.class, RangedBowAttackGoal.class, RestrictSunGoal.class,
            WaterAvoidingRandomStrollGoal.class);

    @Test
    void goalsMatchedByClassKeepTheKeysTheirMojangNamesDerive() {
        for (Class<? extends Goal> goal : GOLEM_GOALS) {
            assertEquals(PaperGoalKeys.derived(goal), Id.minecraft(PaperGoalKeys.known(goal)), goal.getName());
            assertEquals(PaperGoalKeys.derived(goal), PaperGoalKeys.key(goal), goal.getName());
        }
    }

    @Test
    void theAnonymousSkeletonMeleeGoalIsMatchedWithoutItsName() throws ClassNotFoundException {
        Class<?> melee = skeletonMelee();

        assertEquals(PaperGoalKeys.derived(melee), Id.minecraft(PaperGoalKeys.known(melee)));
    }

    @Test
    void otherGoalsFallBackToTheirDerivedKey() {
        assertNull(PaperGoalKeys.known(Goal.class));
    }

    private static Class<?> skeletonMelee() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.world.entity.monster.skeleton.AbstractSkeleton$1", false,
                    PaperGoalKeysTest.class.getClassLoader());
        } catch (ClassNotFoundException older) {
            return Class.forName("net.minecraft.world.entity.monster.AbstractSkeleton$1", false,
                    PaperGoalKeysTest.class.getClassLoader());
        }
    }
}
