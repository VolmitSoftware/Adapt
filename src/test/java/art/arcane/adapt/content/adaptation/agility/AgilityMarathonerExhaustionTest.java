package art.arcane.adapt.content.adaptation.agility;

import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityMarathonerExhaustionTest {
    @Test
    void recognizesSprintMovementWhenTheServerLabelsItsExhaustionAsWalking() {
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.WALK, true)).isTrue();
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.WALK, false)).isFalse();
    }

    @Test
    void retainsExplicitSprintReasonsWithoutDiscountingUnrelatedExhaustion() {
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.SPRINT, false)).isTrue();
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.JUMP_SPRINT, false)).isTrue();
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.ATTACK, true)).isFalse();
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.JUMP, true)).isFalse();
        assertThat(AgilityMarathoner.isSprintExhaustion(ExhaustionReason.SWIM, true)).isFalse();
    }
}
