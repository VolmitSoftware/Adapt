package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TragoulSkeletalServantInteractionTest {
    @Test
    void airInteractionAcceptsVanillaBlockDenial() {
        PlayerInteractEvent event = interaction(Action.RIGHT_CLICK_AIR);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DEFAULT);
        assertThat(event.isCancelled()).isTrue();
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isTrue();
    }

    @Test
    void explicitCancellationAndItemDenialPreventAirSummons() {
        PlayerInteractEvent event = interaction(Action.RIGHT_CLICK_AIR);
        event.setCancelled(true);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
        event.setUseInteractedBlock(Event.Result.ALLOW);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
    }

    @Test
    void protectedBlockCannotBeUsedToSummon() {
        PlayerInteractEvent event = interaction(Action.RIGHT_CLICK_BLOCK);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.ALLOW);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
        event.setUseInteractedBlock(Event.Result.DEFAULT);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isTrue();
        event.setUseItemInHand(Event.Result.DENY);
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(event)).isFalse();
    }

    @Test
    void leftClicksCannotSummon() {
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(interaction(Action.LEFT_CLICK_AIR))).isFalse();
        assertThat(TragoulSkeletalServant.allowsSummonInteraction(interaction(Action.LEFT_CLICK_BLOCK))).isFalse();
    }

    private PlayerInteractEvent interaction(Action action) {
        return new PlayerInteractEvent(mock(Player.class), action, null, null, BlockFace.SELF, EquipmentSlot.HAND);
    }
}
