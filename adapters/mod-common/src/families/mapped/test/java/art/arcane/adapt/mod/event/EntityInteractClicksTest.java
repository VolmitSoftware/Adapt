package art.arcane.adapt.mod.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

class EntityInteractClicksTest {
    private final EntityInteractClicks.Click positioned = new EntityInteractClicks.Click(42, InteractionHand.MAIN_HAND, 100, true);

    @Test
    void plainPacketForTheSameEntityAndHandCompletesTheClick() {
        assertTrue(positioned.completedBy(42, InteractionHand.MAIN_HAND, 100));
        assertTrue(positioned.completedBy(42, InteractionHand.MAIN_HAND, 101));
    }

    @Test
    void anotherEntityHandOrLaterTickIsANewClick() {
        assertFalse(positioned.completedBy(43, InteractionHand.MAIN_HAND, 100));
        assertFalse(positioned.completedBy(42, InteractionHand.OFF_HAND, 100));
        assertFalse(positioned.completedBy(42, InteractionHand.MAIN_HAND, 102));
    }
}
