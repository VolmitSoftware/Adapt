package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TragoulSkeletalServantInteractionTest {
    @Test
    void airInteractionAcceptsVanillaBlockDenial() {
        PlayerInteractEvent event = interaction(InteractAction.RIGHT_CLICK_AIR);
        event.setUseBlock(UseResult.DENY);
        event.setUseItem(UseResult.DEFAULT);
        assertThat(event.isCancelled()).isTrue();
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isTrue();
    }

    @Test
    void explicitCancellationAndItemDenialPreventAirSummons() {
        PlayerInteractEvent event = interaction(InteractAction.RIGHT_CLICK_AIR);
        event.setCancelled(true);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
        event.setUseBlock(UseResult.ALLOW);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
    }

    @Test
    void protectedBlockCannotBeUsedToSummon() {
        PlayerInteractEvent event = interaction(InteractAction.RIGHT_CLICK_BLOCK);
        event.setUseBlock(UseResult.DENY);
        event.setUseItem(UseResult.ALLOW);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
        event.setUseBlock(UseResult.DEFAULT);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isTrue();
        event.setUseItem(UseResult.DENY);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
    }

    @Test
    void leftClicksCannotSummon() {
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(interaction(InteractAction.LEFT_CLICK_AIR))).isFalse();
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(interaction(InteractAction.LEFT_CLICK_BLOCK))).isFalse();
    }

    private PlayerInteractEvent interaction(InteractAction action) {
        return new PlayerInteractEvent(mock(PlayerRef.class), action, Hand.MAIN_HAND, null, null, null, null,
            UseResult.DEFAULT, UseResult.DEFAULT);
    }
}
