package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class GolemGuiEssenceTest {
  private final AtomicReference<Double> essence = new AtomicReference<>(140.5D);
  private final List<Double> writes = new ArrayList<>();
  private final AtomicReference<Runnable> gift = new AtomicReference<>();
  private final AtomicReference<Runnable> retired = new AtomicReference<>();
  private MockedStatic<J> scheduling;
  private IronGolem golem;
  private Player player;
  private GolemHandle handle;

  @BeforeEach
  void setUp() {
    golem = mock(IronGolem.class);
    player = mock(Player.class);
    GolemState state = mock(GolemState.class, withSettings().strictness(Strictness.LENIENT));
    when(state.essence()).thenAnswer(invocation -> essence.get());
    when(state.essencePoints()).thenAnswer(invocation -> (int) Math.max(0D, essence.get()));
    handle = mock(GolemHandle.class, withSettings().strictness(Strictness.LENIENT));
    when(handle.state()).thenReturn(state);
    when(handle.entity()).thenReturn(golem);
    doAnswer(invocation -> {
      double value = invocation.getArgument(0);
      writes.add(value);
      essence.set(value);
      return null;
    }).when(handle).setEssence(anyDouble());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(eq(golem), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
  }

  @AfterEach
  void close() {
    scheduling.close();
  }

  @Test
  void aRefusedGiftThatAlsoRunsItsRetiredCallbackRestoresTheEssenceOnce() {
    playerSchedule(true, false);

    assertThat(GolemGui.takeEssence(handle, player)).isEqualTo(GolemGui.EssenceWithdrawal.REFUSED);

    assertThat(writes).containsExactly(0.5D, 140.5D);
    assertThat(essence.get()).isEqualTo(140.5D);
    verify(player, never()).giveExp(anyInt());
  }

  @Test
  void aRefusedGiftWithoutItsRetiredCallbackRestoresTheEssenceOnce() {
    playerSchedule(false, false);

    assertThat(GolemGui.takeEssence(handle, player)).isEqualTo(GolemGui.EssenceWithdrawal.REFUSED);

    assertThat(writes).containsExactly(0.5D, 140.5D);
    verify(player, never()).giveExp(anyInt());
  }

  @Test
  void anOwnerWhoLeavesBeforeTheGiftRunsGetsTheEssenceBackOnce() {
    playerSchedule(false, true);

    assertThat(GolemGui.takeEssence(handle, player)).isEqualTo(GolemGui.EssenceWithdrawal.TAKEN);
    retired.get().run();
    gift.get().run();
    retired.get().run();

    assertThat(writes).containsExactly(0.5D, 140.5D);
    verify(player, never()).giveExp(anyInt());
  }

  @Test
  void aDeliveredGiftHandsOverTheWholePointsWrittenFirstAndNeverRestores() {
    playerSchedule(false, true);

    assertThat(GolemGui.takeEssence(handle, player)).isEqualTo(GolemGui.EssenceWithdrawal.TAKEN);
    assertThat(writes).containsExactly(0.5D);
    verify(player, never()).giveExp(anyInt());

    gift.get().run();
    retired.get().run();

    verify(player).giveExp(140);
    assertThat(writes).containsExactly(0.5D);
  }

  @Test
  void lessThanOnePointOfEssenceWithdrawsNothing() {
    essence.set(0.4D);

    assertThat(GolemGui.takeEssence(handle, player)).isEqualTo(GolemGui.EssenceWithdrawal.EMPTY);

    assertThat(writes).isEmpty();
    scheduling.verify(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class)), never());
  }

  private void playerSchedule(boolean runRetired, boolean accepted) {
    scheduling.when(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      gift.set(invocation.getArgument(1));
      retired.set(invocation.getArgument(2));
      if (runRetired) {
        retired.get().run();
      }
      return accepted;
    });
  }
}
