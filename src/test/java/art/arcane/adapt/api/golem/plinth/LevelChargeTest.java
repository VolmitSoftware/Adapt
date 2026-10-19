package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.Adaptation;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelChargeTest {
  private static final UUID ACTIVATION = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

  private final Adaptation<?> adaptation = mock(Adaptation.class);
  private final Player player = mock(Player.class);

  @BeforeEach
  void setUp() {
    when(player.getLevel()).thenReturn(10);
  }

  @Test
  void theVanillaCostIsTakenAsADeferredExperienceChargeAndSettledOnSuccess() {
    defaultCostRuns(AbilityOutcome.ALLOWED_DEFAULT);

    Optional<PlinthCharge> charge = LevelCharge.take(adaptation, player, 5);
    charge.orElseThrow().settle();

    verify(adaptation).payExperienceCostDeferred(eq(player), eq(LevelCharge.COST_KEY), eq(5), any(AbilityDefaultCost.class));
    verify(player).giveExpLevels(-5);
    verify(adaptation).settleCost(ACTIVATION);
    verify(adaptation, never()).refundCost(any(), any());
  }

  @Test
  void aRefundRollsBackThroughTheGatewayAndReturnsTheVanillaLevels() {
    defaultCostRuns(AbilityOutcome.ALLOWED_DEFAULT);

    LevelCharge.take(adaptation, player, 5).orElseThrow().refund();

    InOrder order = inOrder(adaptation, player);
    order.verify(player).giveExpLevels(-5);
    order.verify(adaptation).refundCost(ACTIVATION, AbilityRefundReason.ACTIVATION_FAILED);
    order.verify(player).giveExpLevels(5);
    verify(adaptation, never()).settleCost(any());
  }

  @Test
  void aWaivedDefaultCostIsNotReturnedOnRefund() {
    when(adaptation.payExperienceCostDeferred(any(Player.class), anyString(), anyInt(), any(AbilityDefaultCost.class)))
        .thenReturn(charge(AbilityOutcome.ALLOWED_WAIVED));

    LevelCharge.take(adaptation, player, 5).orElseThrow().refund();

    verify(adaptation).refundCost(ACTIVATION, AbilityRefundReason.ACTIVATION_FAILED);
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void aDeniedChargeTakesNothing() {
    when(player.getLevel()).thenReturn(2);
    defaultCostRuns(AbilityOutcome.DENIED_INSUFFICIENT);

    assertThat(LevelCharge.take(adaptation, player, 5)).isEmpty();
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void aFreeInfusionNeverTouchesTheGateway() {
    assertThat(LevelCharge.take(adaptation, player, 0)).contains(PlinthCharge.FREE);
    verify(adaptation, never()).payExperienceCostDeferred(any(), any(), anyInt(), any());
  }

  private void defaultCostRuns(AbilityOutcome outcome) {
    when(adaptation.payExperienceCostDeferred(any(Player.class), anyString(), anyInt(), any(AbilityDefaultCost.class))).thenAnswer(invocation -> {
      AbilityDefaultCost cost = invocation.getArgument(3);
      boolean taken = cost.take();
      return charge(taken && outcome.allowed() ? outcome : AbilityOutcome.DENIED_INSUFFICIENT);
    });
  }

  private static AbilityCharge charge(AbilityOutcome outcome) {
    return new AbilityCharge(ACTIVATION, outcome, outcome == AbilityOutcome.ALLOWED_WAIVED, "", "", List.of());
  }
}
