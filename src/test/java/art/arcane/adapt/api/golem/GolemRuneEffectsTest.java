package art.arcane.adapt.api.golem;

import org.bukkit.DyeColor;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRuneEffectsTest {
  @Test
  void noRunesMeanNoBonusesAndOneChannel() {
    GolemRuneEffects none = GolemRuneEffects.NONE;

    assertThat(none.radiusBonus()).isZero();
    assertThat(none.channelSlots()).isEqualTo(1);
    assertThat(none.reclaimDelayMultiplier()).isEqualTo(1D);
    assertThat(none.bonusYield(GolemCoreType.HARVESTER)).isZero();
  }

  @Test
  void valuesAreClampedToWhatTheyCanMean() {
    GolemRuneEffects wild = new GolemRuneEffects(-4D, 40, 3D, 2D);
    GolemRuneEffects broken = new GolemRuneEffects(Double.NaN, -1, Double.NaN, Double.POSITIVE_INFINITY);

    assertThat(wild.radiusBonus()).isZero();
    assertThat(wild.channelSlots()).isEqualTo(16);
    assertThat(wild.reclaimDelayMultiplier()).isEqualTo(1D);
    assertThat(wild.bonusYield()).isEqualTo(1D);
    assertThat(broken.radiusBonus()).isZero();
    assertThat(broken.channelSlots()).isEqualTo(1);
    assertThat(broken.reclaimDelayMultiplier()).isEqualTo(1D);
    assertThat(broken.bonusYield()).isZero();
  }

  @Test
  void theChannelMaskKeepsOnlyAsManyChannelsAsTheGolemHasSlots() {
    int mask = ChannelMask.of(DyeColor.WHITE, DyeColor.LIME, DyeColor.RED);

    assertThat(GolemRuneEffects.NONE.channels(mask)).isEqualTo(ChannelMask.of(DyeColor.WHITE));
    assertThat(new GolemRuneEffects(0D, 1, 1D, 0D).channels(mask)).isEqualTo(ChannelMask.of(DyeColor.WHITE, DyeColor.LIME));
    assertThat(new GolemRuneEffects(0D, 3, 1D, 0D).channels(mask)).isEqualTo(mask);
    assertThat(GolemRuneEffects.NONE.channels(ChannelMask.NONE)).isEqualTo(ChannelMask.NONE);
  }

  @Test
  void bonusYieldAppliesOnlyToHarvestingCores() {
    GolemRuneEffects entropy = new GolemRuneEffects(0D, 0, 1D, 0.1D);

    assertThat(entropy.bonusYield(GolemCoreType.HARVESTER)).isEqualTo(0.1D);
    assertThat(entropy.bonusYield(GolemCoreType.LUMBER)).isEqualTo(0.1D);
    assertThat(entropy.bonusYield(GolemCoreType.BREAKER)).isEqualTo(0.1D);
    assertThat(entropy.bonusYield(GolemCoreType.ANGLER)).isEqualTo(0.1D);
    assertThat(entropy.bonusYield(GolemCoreType.GATHERER)).isZero();
    assertThat(entropy.bonusYield(GolemCoreType.GUARDIAN)).isZero();
  }

  @Test
  void eachDropThatWinsItsRollIsYieldedTwice() {
    ItemStack wheat = mock(ItemStack.class);
    ItemStack seeds = mock(ItemStack.class);
    ItemStack extraSeeds = mock(ItemStack.class);
    when(seeds.clone()).thenReturn(extraSeeds);
    Deque<Double> rolls = new ArrayDeque<>(List.of(0.5D, 0.05D));
    GolemRuneEffects entropy = new GolemRuneEffects(0D, 0, 1D, 0.1D);

    List<ItemStack> drops = entropy.withBonusYield(GolemCoreType.HARVESTER, List.of(wheat, seeds), rolls::pop);

    assertThat(drops).containsExactly(wheat, seeds, extraSeeds);
    verify(wheat, never()).clone();
  }

  @Test
  void coresWithoutBonusYieldKeepTheirDropsUntouched() {
    ItemStack wheat = mock(ItemStack.class);
    GolemRuneEffects entropy = new GolemRuneEffects(0D, 0, 1D, 0.1D);

    assertThat(entropy.withBonusYield(GolemCoreType.GATHERER, List.of(wheat), () -> 0D)).containsExactly(wheat);
    assertThat(GolemRuneEffects.NONE.withBonusYield(GolemCoreType.HARVESTER, List.of(wheat), () -> 0D)).containsExactly(wheat);
    verify(wheat, never()).clone();
  }
}
