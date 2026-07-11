package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class MutationEffectEquipmentPolicyTest {
  @Test
  void explicitConsentRequiresAnOptIn() {
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.EXPLICIT,
        true,
        false
    )).isTrue();
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.EXPLICIT,
        false,
        false
    )).isFalse();
  }

  @Test
  void partyConsentRequiresBothMembershipAndOptIn() {
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.PARTY,
        true,
        true
    )).isTrue();
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.PARTY,
        true,
        false
    )).isFalse();
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.PARTY,
        false,
        true
    )).isFalse();
  }

  @Test
  void unsupportedSocialModesFailClosed() {
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.FRIEND,
        true,
        true
    )).isFalse();
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        true,
        MutationConfig.ConsentMode.DISABLED,
        true,
        true
    )).isFalse();
    assertThat(MutationRuntimeAccess.cooperativeModeAllows(
        false,
        MutationConfig.ConsentMode.EXPLICIT,
        true,
        true
    )).isFalse();
  }

  @Test
  void mycelialCopyReservationsStayInsideTheSharedCeiling() {
    assertThat(MutationEffectRuntime.copyRecipientLimit(0)).isZero();
    assertThat(MutationEffectRuntime.copyRecipientLimit(8)).isEqualTo(8);
    assertThat(MutationEffectRuntime.copyRecipientLimit(100)).isEqualTo(8);
  }

  @Test
  void disabledPotionEventsRunOnlyForPendingRecovery() {
    assertThat(MutationEffectRuntime.shouldInspectPotionEvent(false, false, false)).isFalse();
    assertThat(MutationEffectRuntime.shouldInspectPotionEvent(false, true, false)).isTrue();
    assertThat(MutationEffectRuntime.shouldInspectPotionEvent(false, false, true)).isTrue();
    assertThat(MutationEffectRuntime.shouldInspectPotionEvent(true, false, false)).isTrue();
  }

  @Test
  void mycelialRecipientLimitCountsDistinctRecipientsInsteadOfEffects() {
    Map<MutationRuntimeStore.EffectKey, MutationRuntimeStore.CopiedEffect> copies = new HashMap<>();
    UUID rootId = UUID.randomUUID();
    UUID existingRecipient = null;
    for (int index = 0; index < 8; index++) {
      UUID recipientId = UUID.randomUUID();
      if (existingRecipient == null) {
        existingRecipient = recipientId;
      }
      MutationRuntimeStore.EffectKey key = new MutationRuntimeStore.EffectKey(recipientId, null);
      copies.put(key, new MutationRuntimeStore.CopiedEffect(rootId, recipientId, null, 0, 1_000L, 1L));
    }

    assertThat(MutationEffectRuntime.exceedsRecipientLimit(copies, existingRecipient, 8)).isFalse();
    assertThat(MutationEffectRuntime.exceedsRecipientLimit(copies, UUID.randomUUID(), 8)).isTrue();
  }

  @Test
  void deepbloodOnlyAcceptsIndustryTools() {
    assertThat(MutationItemIdentity.isToolMaterial(Material.NETHERITE_PICKAXE)).isTrue();
    assertThat(MutationItemIdentity.isToolMaterial(Material.DIAMOND_AXE)).isTrue();
    assertThat(MutationItemIdentity.isToolMaterial(Material.IRON_SHOVEL)).isTrue();
    assertThat(MutationItemIdentity.isToolMaterial(Material.GOLDEN_HOE)).isTrue();
    assertThat(MutationItemIdentity.isToolMaterial(Material.NETHERITE_SWORD)).isFalse();
    assertThat(MutationItemIdentity.isToolMaterial(Material.DIAMOND_CHESTPLATE)).isFalse();
    assertThat(MutationItemIdentity.isToolMaterial(Material.FISHING_ROD)).isFalse();
  }

  @Test
  void crackedArmorCompensationMatchesTheReducedArmorTarget() {
    double target = MutationEquipmentRuntime.damageAfterArmor(10D, 12D, 2D);
    double compensatedBase = MutationEquipmentRuntime.requiredBaseDamage(target, 20D, 8D);

    assertThat(MutationEquipmentRuntime.damageAfterArmor(compensatedBase, 20D, 8D))
        .isCloseTo(target, offset(0.0001D));
    assertThat(compensatedBase).isGreaterThan(10D);
  }

  @Test
  void disabledRuntimeDoesNotEnforceExistingEquipmentDamageStates() {
    assertThat(MutationEquipmentRuntime.blocksEquipmentState(false, true, false)).isFalse();
    assertThat(MutationEquipmentRuntime.blocksEquipmentState(false, false, true)).isFalse();
    assertThat(MutationEquipmentRuntime.blocksEquipmentState(true, true, false)).isTrue();
    assertThat(MutationEquipmentRuntime.blocksEquipmentState(true, false, true)).isTrue();
    assertThat(MutationEquipmentRuntime.blocksEquipmentState(true, false, false)).isFalse();
  }

  @Test
  void temperboundRequiresFourUniqueIdentitiesInTheirAttunedSlots() {
    List<String> expected = List.of("boots", "legs", "chest", "helmet");

    assertThat(MutationEquipmentRuntime.matchesTemperboundSlots(expected, expected)).isTrue();
    assertThat(MutationEquipmentRuntime.matchesTemperboundSlots(
        List.of("helmet", "legs", "chest", "boots"),
        expected
    )).isFalse();
    assertThat(MutationEquipmentRuntime.matchesTemperboundSlots(
        List.of("boots", "boots", "boots", "boots"),
        List.of("boots", "boots", "boots", "boots")
    )).isFalse();
  }

  @Test
  void temperboundDamageDistributionBatchesLargeValuesWithoutPerPointWork() {
    assertThat(MutationEquipmentRuntime.balancedDamageTargets(
        new int[]{0, 10, 20, 30},
        new int[]{100, 100, 100, 100},
        40L
    )).containsExactly(24, 23, 23, 30);
    assertThat(MutationEquipmentRuntime.balancedDamageTargets(
        new int[]{0, 0, 0, 0},
        new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
        Long.MAX_VALUE
    )).containsExactly(
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE - 1,
        Integer.MAX_VALUE - 1
    );
  }

  @Test
  void deepbloodOnlyDecaysIntervalsSpentAboveTheConfiguredDepth() {
    assertThat(MutationEquipmentRuntime.ichorAfterDepthInterval(100D, 300_000L, 300_000L, true))
        .isEqualTo(50D);
    assertThat(MutationEquipmentRuntime.ichorAfterDepthInterval(100D, 300_000L, 300_000L, false))
        .isEqualTo(100D);
  }
}
