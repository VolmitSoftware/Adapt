package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OverseerRulesTest {
  private static final List<Integer> RADIUS = List.of(16, 24, 32, 48, 64);
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000601");
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000611");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000612");
  private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000613");
  private static final UUID FOURTH = UUID.fromString("00000000-0000-0000-0000-000000000614");

  @Test
  void theSignalRadiusFollowsTheLevelAndIsClampedToOneThrough128() {
    assertThat(OverseerRules.radius(RADIUS, 0)).isZero();
    assertThat(OverseerRules.radius(RADIUS, 1)).isEqualTo(16D);
    assertThat(OverseerRules.radius(RADIUS, 3)).isEqualTo(32D);
    assertThat(OverseerRules.radius(RADIUS, 5)).isEqualTo(64D);
    assertThat(OverseerRules.radius(RADIUS, 9)).isEqualTo(64D);
    assertThat(OverseerRules.radius(List.of(0, 500), 1)).isEqualTo(1D);
    assertThat(OverseerRules.radius(List.of(0, 500), 2)).isEqualTo(128D);
    assertThat(OverseerRules.radius(List.of(), 2)).isZero();
  }

  @Test
  void levelOneCyclesFollowHoldHomeAndWork() {
    assertThat(OverseerRules.next(null, 1, 2)).isEqualTo(GolemSignal.HOLD);
    assertThat(OverseerRules.next(GolemSignal.FOLLOW, 1, 2)).isEqualTo(GolemSignal.HOLD);
    assertThat(OverseerRules.next(GolemSignal.HOLD, 1, 2)).isEqualTo(GolemSignal.HOME);
    assertThat(OverseerRules.next(GolemSignal.HOME, 1, 2)).isEqualTo(GolemSignal.WORK);
    assertThat(OverseerRules.next(GolemSignal.WORK, 1, 2)).isEqualTo(GolemSignal.FOLLOW);
  }

  @Test
  void sicJoinsTheCycleAtItsLevel() {
    assertThat(OverseerRules.next(GolemSignal.WORK, 2, 2)).isEqualTo(GolemSignal.SIC);
    assertThat(OverseerRules.next(GolemSignal.SIC, 2, 2)).isEqualTo(GolemSignal.FOLLOW);
    assertThat(OverseerRules.next(GolemSignal.WORK, 2, 3)).isEqualTo(GolemSignal.FOLLOW);
  }

  @Test
  void theCurrentSignalStartsAtFollowAndDropsALockedSic() {
    assertThat(OverseerRules.current(null, 1, 2)).isEqualTo(GolemSignal.FOLLOW);
    assertThat(OverseerRules.current(GolemSignal.HOME, 1, 2)).isEqualTo(GolemSignal.HOME);
    assertThat(OverseerRules.current(GolemSignal.SIC, 2, 2)).isEqualTo(GolemSignal.SIC);
    assertThat(OverseerRules.current(GolemSignal.SIC, 1, 2)).isEqualTo(GolemSignal.FOLLOW);
    assertThat(OverseerRules.next(GolemSignal.SIC, 1, 2)).isEqualTo(GolemSignal.HOLD);
  }

  @Test
  void theCrookChannelFiltersOnlyFromTheFilterLevel() {
    assertThat(OverseerRules.crookChannel(2, 3, Optional.of(DyeColor.RED))).isNull();
    assertThat(OverseerRules.crookChannel(3, 3, Optional.of(DyeColor.RED))).isEqualTo(DyeColor.RED);
    assertThat(OverseerRules.crookChannel(5, 3, Optional.empty())).isNull();
  }

  @Test
  void guardiansCompanionsAndMasterworksWithACombatCoreCanFight() {
    assertThat(OverseerRules.combatCapable(GolemBody.IRON, List.of(GolemCoreType.BLANK))).isTrue();
    assertThat(OverseerRules.combatCapable(GolemBody.FROST, List.of(GolemCoreType.GUARDIAN))).isTrue();
    assertThat(OverseerRules.combatCapable(GolemBody.IRON, List.of(GolemCoreType.GATHERER, GolemCoreType.GUARDIAN))).isTrue();
    assertThat(OverseerRules.combatCapable(GolemBody.IRON, List.of(GolemCoreType.BREAKER))).isFalse();
    assertThat(OverseerRules.combatCapable(GolemBody.COPPER, List.of(GolemCoreType.BLANK))).isFalse();
    assertThat(OverseerRules.combatCapable(GolemBody.FROST, List.of())).isFalse();
  }

  @Test
  void movementSignalsSetTheModeOnGolemsInRangeThatCanTakeIt() {
    assertThat(answer(GolemSignal.FOLLOW, GolemCoreType.BLANK, true, true, true, false)).isEqualTo(OverseerRules.Answer.MODE);
    assertThat(answer(GolemSignal.HOLD, GolemCoreType.GATHERER, true, true, true, false)).isEqualTo(OverseerRules.Answer.MODE);
    assertThat(answer(GolemSignal.WORK, GolemCoreType.GATHERER, true, true, true, false)).isEqualTo(OverseerRules.Answer.MODE);
    assertThat(answer(GolemSignal.WORK, GolemCoreType.BLANK, true, true, true, false)).isEqualTo(OverseerRules.Answer.SKIP);
  }

  @Test
  void golemsOffTheCrookChannelOrInAnotherWorldNeverAnswer() {
    assertThat(answer(GolemSignal.FOLLOW, GolemCoreType.BLANK, false, true, true, true)).isEqualTo(OverseerRules.Answer.SKIP);
    assertThat(answer(GolemSignal.FOLLOW, GolemCoreType.BLANK, true, false, false, true)).isEqualTo(OverseerRules.Answer.SKIP);
  }

  @Test
  void onlyFollowRalliesGolemsBeyondTheRadius() {
    assertThat(answer(GolemSignal.FOLLOW, GolemCoreType.GATHERER, true, true, false, true)).isEqualTo(OverseerRules.Answer.RALLY);
    assertThat(answer(GolemSignal.FOLLOW, GolemCoreType.GATHERER, true, true, false, false)).isEqualTo(OverseerRules.Answer.SKIP);
    assertThat(answer(GolemSignal.HOME, GolemCoreType.GATHERER, true, true, false, true)).isEqualTo(OverseerRules.Answer.SKIP);
  }

  @Test
  void sicCommandsOnlyFightingGolemsInRange() {
    assertThat(hear(GolemSignal.SIC, GolemBody.IRON, GolemCoreType.GUARDIAN, true, true)).isEqualTo(OverseerRules.Answer.COMMAND);
    assertThat(hear(GolemSignal.SIC, GolemBody.FROST, GolemCoreType.BLANK, true, true)).isEqualTo(OverseerRules.Answer.COMMAND);
    assertThat(hear(GolemSignal.SIC, GolemBody.IRON, GolemCoreType.BREAKER, true, true)).isEqualTo(OverseerRules.Answer.SKIP);
    assertThat(hear(GolemSignal.SIC, GolemBody.COPPER, GolemCoreType.BLANK, true, true)).isEqualTo(OverseerRules.Answer.SKIP);
    assertThat(hear(GolemSignal.SIC, GolemBody.IRON, GolemCoreType.GUARDIAN, true, false)).isEqualTo(OverseerRules.Answer.SKIP);
  }

  @Test
  void aRallyClaimsTheOwnersCooldownOnceAndCoversEveryGolemOfThatFollowSignal() {
    GolemXpCooldowns<UUID> cooldowns = new GolemXpCooldowns<>();
    OverseerRally first = new OverseerRally(cooldowns, OWNER, 1_000L, 60_000L);

    assertThat(first.claim()).isTrue();
    assertThat(first.claim()).isTrue();

    OverseerRally early = new OverseerRally(cooldowns, OWNER, 60_999L, 60_000L);
    assertThat(early.claim()).isFalse();
    assertThat(early.claim()).isFalse();

    OverseerRally ready = new OverseerRally(cooldowns, OWNER, 61_000L, 60_000L);
    assertThat(ready.claim()).isTrue();
  }

  @Test
  void aDeniedRallyDoesNotRestartTheCooldown() {
    GolemXpCooldowns<UUID> cooldowns = new GolemXpCooldowns<>();
    assertThat(new OverseerRally(cooldowns, OWNER, 0L, 60_000L).claim()).isTrue();
    assertThat(new OverseerRally(cooldowns, OWNER, 30_000L, 60_000L).claim()).isFalse();

    assertThat(new OverseerRally(cooldowns, OWNER, 60_000L, 60_000L).claim()).isTrue();
  }

  @Test
  void ringSlotsSpreadEvenlyAroundTheOwner() {
    assertOffset(OverseerRules.slot(0, 4, 2.5D), 2.5D, 0D);
    assertOffset(OverseerRules.slot(1, 4, 2.5D), 0D, 2.5D);
    assertOffset(OverseerRules.slot(2, 4, 2.5D), -2.5D, 0D);
    assertOffset(OverseerRules.slot(3, 4, 2.5D), 0D, -2.5D);
    assertOffset(OverseerRules.slot(0, 1, 2.5D), 2.5D, 0D);
    assertOffset(OverseerRules.slot(1, 3, 3D), 3D * Math.cos(2D * Math.PI / 3D), 3D * Math.sin(2D * Math.PI / 3D));
  }

  @Test
  void slotAssignmentIsStableWhileMembershipIsUnchanged() {
    List<UUID> ring = OverseerRules.ring(List.of(), List.of(THIRD, FIRST, SECOND));
    assertThat(ring).containsExactly(FIRST, SECOND, THIRD);

    assertThat(OverseerRules.ring(ring, Set.of(SECOND, THIRD, FIRST))).containsExactly(FIRST, SECOND, THIRD);
    assertThat(OverseerRules.ring(List.of(THIRD, FIRST, SECOND), List.of(FIRST, SECOND, THIRD))).containsExactly(THIRD, FIRST, SECOND);
  }

  @Test
  void membershipChangesKeepTheRemainingOrderAndAppendNewcomers() {
    List<UUID> ring = new ArrayList<>(List.of(SECOND, FIRST, THIRD));

    assertThat(OverseerRules.ring(ring, List.of(FIRST, THIRD))).containsExactly(FIRST, THIRD);
    assertThat(OverseerRules.ring(ring, List.of(FOURTH, THIRD, SECOND, FIRST))).containsExactly(SECOND, FIRST, THIRD, FOURTH);
    assertThat(OverseerRules.ring(ring, List.of())).isEmpty();
  }

  @Test
  void formationArmorGrowsPerFollowingGolemUpToTheCap() {
    assertThat(OverseerRules.formationArmor(0, 1D, 6D)).isZero();
    assertThat(OverseerRules.formationArmor(3, 1D, 6D)).isEqualTo(3D);
    assertThat(OverseerRules.formationArmor(6, 1D, 6D)).isEqualTo(6D);
    assertThat(OverseerRules.formationArmor(9, 1D, 6D)).isEqualTo(6D);
    assertThat(OverseerRules.formationArmor(4, 2D, 6D)).isEqualTo(6D);
    assertThat(OverseerRules.formationArmor(4, 1D, 0D)).isZero();
  }

  @Test
  void theArmorModifierChangesOnlyWhenItsAmountDiffers() {
    assertThat(OverseerRules.armorChange(Double.NaN, 0D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
    assertThat(OverseerRules.armorChange(3D, 0D)).isEqualTo(OverseerRules.ArmorChange.REMOVE);
    assertThat(OverseerRules.armorChange(Double.NaN, 3D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerRules.armorChange(2D, 3D)).isEqualTo(OverseerRules.ArmorChange.APPLY);
    assertThat(OverseerRules.armorChange(3D, 3D)).isEqualTo(OverseerRules.ArmorChange.KEEP);
  }

  @Test
  void loadedValuesAreClampedToSafeRanges() {
    assertThat(OverseerRules.sicRange(0D)).isEqualTo(1D);
    assertThat(OverseerRules.sicRange(500D)).isEqualTo(64D);
    assertThat(OverseerRules.sicRange(Double.NaN)).isEqualTo(24D);
    assertThat(OverseerRules.ringRadius(0D)).isEqualTo(1D);
    assertThat(OverseerRules.ringRadius(20D)).isEqualTo(8D);
    assertThat(OverseerRules.armorMax(-1D)).isZero();
    assertThat(OverseerRules.armorMax(50D)).isEqualTo(30D);
    assertThat(OverseerRules.nonNegative(-2D)).isZero();
    assertThat(OverseerRules.nonNegative(Double.POSITIVE_INFINITY)).isZero();
    assertThat(OverseerRules.holdSeconds(0)).isEqualTo(1);
  }

  private static OverseerRules.Answer answer(GolemSignal signal, GolemCoreType core, boolean heard, boolean sameWorld, boolean inRange, boolean rallies) {
    return OverseerRules.answer(new OverseerRules.Hearing(signal, GolemBody.IRON, core, List.of(core), heard, sameWorld, inRange, rallies));
  }

  private static OverseerRules.Answer hear(GolemSignal signal, GolemBody body, GolemCoreType core, boolean heard, boolean inRange) {
    return OverseerRules.answer(new OverseerRules.Hearing(signal, body, core, List.of(core), heard, true, inRange, false));
  }

  private static void assertOffset(OverseerRules.Offset offset, double x, double z) {
    assertThat(offset.x()).isCloseTo(x, within(1.0E-9));
    assertThat(offset.z()).isCloseTo(z, within(1.0E-9));
  }
}
