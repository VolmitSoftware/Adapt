package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.ability.AbilityDefaultCost;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosInstantRecallHealthCostTest {
    @Test
    void completionChargesHealthWithoutDispatchingProtectedDamage() {
        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(20D);
        ChronosInstantRecall recall = recall(0.5D, true);
        recall.applyRecallHealthCost(player);
        verify(recall).payHealthCost(eq(player), eq("health"), eq(10), any());
        verify(player).setHealth(10D);
        verify(player, never()).damage(10D);
    }

    @Test
    void fullFractionStillLeavesOneHealth() {
        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(3D);
        ChronosInstantRecall recall = recall(1D, true);
        recall.applyRecallHealthCost(player);
        verify(player).setHealth(1D);
    }

    @Test
    void rejectedDefaultCostDoesNotDeductHealth() {
        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(20D);
        ChronosInstantRecall recall = recall(0.5D, false);
        recall.applyRecallHealthCost(player);
        verify(player, never()).setHealth(10D);
    }

    @Test
    void disabledCostDoesNotRequestPayment() {
        Player player = mock(Player.class);
        ChronosInstantRecall recall = recall(0D, true);
        recall.applyRecallHealthCost(player);
        verify(recall, never()).payHealthCost(any(), any(), anyInt(), any());
    }

    private ChronosInstantRecall recall(double fraction, boolean chargeDefault) {
        ChronosInstantRecall recall = mock(ChronosInstantRecall.class, CALLS_REAL_METHODS);
        ChronosInstantRecallConfig config = new ChronosInstantRecallConfig();
        config.healthCostFraction = fraction;
        doReturn(config).when(recall).getConfig();
        doAnswer(invocation -> chargeDefault && invocation.getArgument(3, AbilityDefaultCost.class).take())
                .when(recall).payHealthCost(any(), any(), anyInt(), any());
        return recall;
    }
}
