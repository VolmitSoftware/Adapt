package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChronosTimeBombInteractionTest {
  @Test
  void aSneakingBlockClickWithADeniedBlockUseStillGovernsTheThrow() {
    PlayerInteractEvent event = interaction(InteractAction.RIGHT_CLICK_BLOCK);
    event.setUseBlock(UseResult.DENY);
    assertThat(event.isUseDenied()).isTrue();
    assertThat(ChronosTimeBomb.governsThrow(event)).isTrue();
  }

  @Test
  void aDeniedItemUseIsLeftAlone() {
    PlayerInteractEvent air = interaction(InteractAction.RIGHT_CLICK_AIR);
    air.setUseItem(UseResult.DENY);
    PlayerInteractEvent block = interaction(InteractAction.RIGHT_CLICK_BLOCK);
    block.setCancelled(true);
    assertThat(ChronosTimeBomb.governsThrow(air)).isFalse();
    assertThat(ChronosTimeBomb.governsThrow(block)).isFalse();
  }

  @Test
  void onlyRightClicksThrow() {
    assertThat(ChronosTimeBomb.governsThrow(interaction(InteractAction.RIGHT_CLICK_AIR))).isTrue();
    assertThat(ChronosTimeBomb.governsThrow(interaction(InteractAction.LEFT_CLICK_AIR))).isFalse();
    assertThat(ChronosTimeBomb.governsThrow(interaction(InteractAction.LEFT_CLICK_BLOCK))).isFalse();
  }

  private static PlayerInteractEvent interaction(InteractAction action) {
    BlockRef clicked = action == InteractAction.RIGHT_CLICK_BLOCK || action == InteractAction.LEFT_CLICK_BLOCK ? mock(BlockRef.class) : null;
    return new PlayerInteractEvent(mock(PlayerRef.class), action, Hand.MAIN_HAND, null, clicked, null, null,
        UseResult.DEFAULT, UseResult.DEFAULT);
  }
}
