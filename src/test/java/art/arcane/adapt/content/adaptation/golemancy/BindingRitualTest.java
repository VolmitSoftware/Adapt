package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingRitualTest {
  private static final long STARTED_AT = 10_000L;

  @Test
  void progressStepsFromZeroToOneAcrossTheRitual() {
    BindingRitual ritual = ritual(4);

    assertThat(ritual.progress()).isZero();
    ritual.advance();
    ritual.advance();
    assertThat(ritual.progress()).isEqualTo(0.5D);
    assertThat(ritual.complete()).isFalse();
    ritual.advance();
    ritual.advance();
    ritual.advance();
    assertThat(ritual.progress()).isEqualTo(1D);
    assertThat(ritual.complete()).isTrue();
  }

  @Test
  void finishingRunsOnceAndStopsTheEffects() {
    BindingRitual ritual = ritual(4);
    FxTimeline timeline = mock(FxTimeline.class);
    ritual.attach(timeline);

    assertThat(ritual.finish()).isTrue();
    assertThat(ritual.finish()).isFalse();
    assertThat(ritual.isFinished()).isTrue();
    verify(timeline, times(1)).cancel();
  }

  @Test
  void aRitualTurnsStaleFiveSecondsAfterItShouldHaveEnded() {
    BindingRitual ritual = ritual(40);

    assertThat(ritual.stale(STARTED_AT + 2_000L + 5_000L)).isFalse();
    assertThat(ritual.stale(STARTED_AT + 2_000L + 5_001L)).isTrue();
  }

  @Test
  void theGolemsAwarenessIsRememberedForTheEnd() {
    assertThat(ritual(4).wasAware()).isTrue();
  }

  private static BindingRitual ritual(int ticks) {
    Player owner = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(owner.getInventory()).thenReturn(inventory);
    when(inventory.getHeldItemSlot()).thenReturn(3);
    Snowman golem = mock(Snowman.class);
    when(golem.isAware()).thenReturn(true);
    return new BindingRitual(new BindingTarget(owner, golem, GolemBody.FROST, GolemCoreType.BLANK), ticks, STARTED_AT);
  }
}
