package art.arcane.adapt.content.adaptation.axe;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AxeAdaptationLogicTest {
  @Test
  void meleeBaseDamageMatchesAxeTier() {
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.NETHERITE_AXE)).isEqualTo(10D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.DIAMOND_AXE)).isEqualTo(9D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.IRON_AXE)).isEqualTo(9D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.STONE_AXE)).isEqualTo(9D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.WOODEN_AXE)).isEqualTo(7D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.GOLDEN_AXE)).isEqualTo(7D);
  }

  @Test
  void shutdownRecoveryKeepsUnavailableOwnersPending() {
    assertThat(AxeThrowingAxe.recoveryDisposition(true, true))
        .isEqualTo(AxeThrowingAxe.RecoveryDisposition.OWNER);
    assertThat(AxeThrowingAxe.recoveryDisposition(true, false))
        .isEqualTo(AxeThrowingAxe.RecoveryDisposition.PENDING);
    assertThat(AxeThrowingAxe.recoveryDisposition(false, true))
        .isEqualTo(AxeThrowingAxe.RecoveryDisposition.DESTROYED);
  }

  @Test
  void onlyAnActuallyConsumedUnbrokenAxeCanBeRecovered() {
    assertThat(AxeThrowingAxe.isRecoverableThrow(true, false)).isTrue();
    assertThat(AxeThrowingAxe.isRecoverableThrow(false, false)).isFalse();
    assertThat(AxeThrowingAxe.isRecoverableThrow(true, true)).isFalse();
    assertThat(AxeThrowingAxe.isRecoverableThrow(false, true)).isFalse();
  }

  @Test
  void durabilityBreaksAtTheExactMaximumThreshold() {
    assertThat(AxeThrowingAxe.reachesBreakThreshold(98, 1, 100)).isFalse();
    assertThat(AxeThrowingAxe.reachesBreakThreshold(99, 1, 100)).isTrue();
    assertThat(AxeThrowingAxe.reachesBreakThreshold(98, 2, 100)).isTrue();
  }

  @Test
  void throwingAxeRewardsOnlyAnObservedUncancelledDamageEvent() {
    assertThat(AxeThrowingAxe.isSuccessfulDamageEvent(true, false, 4D)).isTrue();
    assertThat(AxeThrowingAxe.isSuccessfulDamageEvent(false, false, 4D)).isFalse();
    assertThat(AxeThrowingAxe.isSuccessfulDamageEvent(true, true, 4D)).isFalse();
    assertThat(AxeThrowingAxe.isSuccessfulDamageEvent(true, false, 0D)).isFalse();
  }

  @Test
  void throwingAxeAcceptsOnlyMainHandAirClicks() {
    assertThat(AxeThrowingAxe.isThrowInteraction(Action.LEFT_CLICK_AIR, EquipmentSlot.HAND))
        .isTrue();
    assertThat(AxeThrowingAxe.isThrowInteraction(Action.LEFT_CLICK_AIR, null)).isTrue();
    assertThat(AxeThrowingAxe.isThrowInteraction(Action.LEFT_CLICK_AIR, EquipmentSlot.OFF_HAND))
        .isFalse();
    assertThat(AxeThrowingAxe.isThrowInteraction(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND))
        .isFalse();
    assertThat(AxeThrowingAxe.isThrowInteraction(Action.RIGHT_CLICK_AIR, EquipmentSlot.HAND))
        .isFalse();
  }

  @Test
  void throwingAxeRecordsOnlyCompletedBlockBreaks() throws ReflectiveOperationException {
    Method handler = AxeThrowingAxe.class.getDeclaredMethod("on", BlockBreakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);

    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void configDefaultsAreSaneAndNonZero() {
    AxeThrowingAxe.Config throwing = new AxeThrowingAxe.Config();
    assertThat(throwing.damageMultiplierBase).isGreaterThan(0D);
    assertThat(throwing.throwSpeedBase).isGreaterThan(0D);
    assertThat(throwing.durabilityCost).isGreaterThan(0);
    assertThat(throwing.maxLevel).isEqualTo(4);

    AxeSunder.Config sunder = new AxeSunder.Config();
    assertThat(sunder.shredPerStackBase).isGreaterThan(0D);
    assertThat(sunder.maxStacksBase).isGreaterThanOrEqualTo(1);
    assertThat(sunder.durationTicks).isGreaterThan(0);

    AxeCleave.Config cleave = new AxeCleave.Config();
    assertThat(cleave.targetCapBase).isEqualTo(2);
    assertThat(cleave.targetCapMaxBonus).isEqualTo(2);
    assertThat(cleave.damageShareBase).isGreaterThan(0D);

    AxeBarkHide.Config bark = new AxeBarkHide.Config();
    assertThat(bark.absorptionCapBase).isGreaterThanOrEqualTo(1);
    assertThat(bark.gracePeriodTicksBase).isGreaterThan(0D);

    AxeShieldSplitter.Config splitter = new AxeShieldSplitter.Config();
    assertThat(splitter.disableTicksBase).isGreaterThan(0D);
    assertThat(splitter.bonusDamagePctBase).isGreaterThan(0D);
  }
}
