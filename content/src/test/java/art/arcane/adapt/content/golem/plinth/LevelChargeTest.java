package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.engine.ability.EngineAbilityCharge;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelChargeTest {
  private static final UUID ACTIVATION = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

  private final Adaptation<?> adaptation = mock(Adaptation.class);
  private final PlayerRef player = mock(PlayerRef.class);

  @BeforeEach
  void setUp() {
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.level()).thenReturn(10);
  }

  @Test
  void theVanillaCostIsTakenAsADeferredExperienceChargeAndSettledOnSuccess() {
    defaultCostRuns(AbilityOutcome.ALLOWED_DEFAULT);

    Optional<PlinthCharge> charge = LevelCharge.take(adaptation, player, 5);
    charge.orElseThrow().settle();

    verify(adaptation).payCostDeferred(eq(player), eq(LevelCharge.COST_KEY), eq(AbilityCostKind.VANILLA_EXPERIENCE), isNull(), eq(5),
        any(AbilityDefaultCost.class));
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
    when(adaptation.payCostDeferred(any(PlayerRef.class), anyString(), any(AbilityCostKind.class), any(), anyInt(),
        any(AbilityDefaultCost.class)))
        .thenReturn(charge(AbilityOutcome.ALLOWED_WAIVED));

    LevelCharge.take(adaptation, player, 5).orElseThrow().refund();

    verify(adaptation).refundCost(ACTIVATION, AbilityRefundReason.ACTIVATION_FAILED);
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void aDeniedChargeTakesNothing() {
    when(player.level()).thenReturn(2);
    defaultCostRuns(AbilityOutcome.DENIED_INSUFFICIENT);

    assertThat(LevelCharge.take(adaptation, player, 5)).isEmpty();
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void aFreeInfusionNeverTouchesTheGateway() {
    assertThat(LevelCharge.take(adaptation, player, 0)).contains(PlinthCharge.FREE);
    verify(adaptation, never()).payCostDeferred(any(), any(), any(), any(), anyInt(), any());
  }

  private void defaultCostRuns(AbilityOutcome outcome) {
    when(adaptation.payCostDeferred(any(PlayerRef.class), anyString(), any(AbilityCostKind.class), any(), anyInt(),
        any(AbilityDefaultCost.class))).thenAnswer(invocation -> {
      AbilityDefaultCost cost = invocation.getArgument(5);
      boolean taken = cost.take();
      return charge(taken && outcome.allowed() ? outcome : AbilityOutcome.DENIED_INSUFFICIENT);
    });
  }

  private static AbilityCharge charge(AbilityOutcome outcome) {
    return new EngineAbilityCharge(ACTIVATION, outcome, outcome == AbilityOutcome.ALLOWED_WAIVED, "", "", List.of());
  }
}
