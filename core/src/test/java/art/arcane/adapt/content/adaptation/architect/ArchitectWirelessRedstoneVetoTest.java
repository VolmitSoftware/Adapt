package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArchitectWirelessRedstoneVetoTest {
  @Test
  void aPulseOnABlockWithDeniedBlockUseStillGoesThrough() {
    PlayerInteractEvent event = interaction(InteractAction.RIGHT_CLICK_BLOCK);
    event.setUseBlock(UseResult.DENY);
    assertThat(ArchitectWirelessRedstone.vetoes(event)).isFalse();
    event.setUseItem(UseResult.DENY);
    assertThat(ArchitectWirelessRedstone.vetoes(event)).isTrue();
  }

  @Test
  void aBindingClickOnADeniedBlockIsVetoed() {
    PlayerInteractEvent event = interaction(InteractAction.LEFT_CLICK_BLOCK);
    assertThat(ArchitectWirelessRedstone.vetoes(event)).isFalse();
    event.setUseBlock(UseResult.DENY);
    assertThat(ArchitectWirelessRedstone.vetoes(event)).isTrue();
  }

  @Test
  void anAirPulseFollowsTheItemUse() {
    PlayerInteractEvent event = interaction(InteractAction.RIGHT_CLICK_AIR);
    event.setUseBlock(UseResult.DENY);
    assertThat(ArchitectWirelessRedstone.vetoes(event)).isFalse();
    event.setUseItem(UseResult.DENY);
    assertThat(ArchitectWirelessRedstone.vetoes(event)).isTrue();
  }

  private static PlayerInteractEvent interaction(InteractAction action) {
    BlockRef clicked = action == InteractAction.RIGHT_CLICK_BLOCK || action == InteractAction.LEFT_CLICK_BLOCK ? mock(BlockRef.class) : null;
    return new PlayerInteractEvent(mock(PlayerRef.class), action, Hand.MAIN_HAND, null, clicked, null, null,
        UseResult.DEFAULT, UseResult.DEFAULT);
  }
}
