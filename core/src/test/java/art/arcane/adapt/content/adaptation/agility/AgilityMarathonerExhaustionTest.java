package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.platform.event.player.EntityExhaustionEvent.Reason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityMarathonerExhaustionTest {
    @Test
    void recognizesSprintMovementWhenTheServerLabelsItsExhaustionAsWalking() {
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.WALK, true)).isTrue();
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.WALK, false)).isFalse();
    }

    @Test
    void retainsExplicitSprintReasonsWithoutDiscountingUnrelatedExhaustion() {
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.SPRINT, false)).isTrue();
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.JUMP_SPRINT, false)).isTrue();
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.ATTACK, true)).isFalse();
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.JUMP, true)).isFalse();
        assertThat(AgilityMarathoner.isSprintExhaustion(Reason.SWIM, true)).isFalse();
    }
}
