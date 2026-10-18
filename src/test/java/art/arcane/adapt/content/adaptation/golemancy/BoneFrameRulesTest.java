package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.minion.ServantOrder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;

class BoneFrameRulesTest {
  private static final List<Integer> SLOTS = List.of(0, 2, 3);
  private static final Set<GolemRuneType> ALLOWED = EnumSet.of(GolemRuneType.SWIFTNESS, GolemRuneType.MIGHT, GolemRuneType.EMBER, GolemRuneType.BULWARK);

  private final World world = mock(World.class);

  @Test
  void latticeSlotsFollowTheLevelTableAndStayInsideTheWindow() {
    assertThat(BoneFrameRules.latticeSlots(SLOTS, 0)).isZero();
    assertThat(BoneFrameRules.latticeSlots(SLOTS, 1)).isZero();
    assertThat(BoneFrameRules.latticeSlots(SLOTS, 2)).isEqualTo(2);
    assertThat(BoneFrameRules.latticeSlots(SLOTS, 3)).isEqualTo(3);
    assertThat(BoneFrameRules.latticeSlots(SLOTS, 9)).isEqualTo(3);
    assertThat(BoneFrameRules.latticeSlots(List.of(-4, 40), 1)).isZero();
    assertThat(BoneFrameRules.latticeSlots(List.of(-4, 40), 2)).isEqualTo(BoneFrameRules.MAX_LATTICE_SLOTS);
  }

  @Test
  void eachLockedSlotNamesTheLevelThatUnlocksIt() {
    assertThat(BoneFrameRules.slotUnlockLevel(SLOTS, 3, 0)).isEqualTo(2);
    assertThat(BoneFrameRules.slotUnlockLevel(SLOTS, 3, 1)).isEqualTo(2);
    assertThat(BoneFrameRules.slotUnlockLevel(SLOTS, 3, 2)).isEqualTo(3);
    assertThat(BoneFrameRules.slotUnlockLevel(SLOTS, 3, 3)).isZero();
    assertThat(BoneFrameRules.mostSlots(SLOTS, 3)).isEqualTo(3);
    assertThat(BoneFrameRules.mostSlots(List.of(0, 2, 3), 2)).isEqualTo(2);
  }

  @Test
  void theServantShareNeedsBoneFrameAndReachesTheFullKillValueAtLevelThree() {
    List<Double> shares = List.of(0.35D, 0.35D, 1.0D);

    assertThat(BoneFrameRules.share(shares, 0)).isZero();
    assertThat(BoneFrameRules.share(shares, 1)).isEqualTo(0.35D);
    assertThat(BoneFrameRules.share(shares, 2)).isEqualTo(0.35D);
    assertThat(BoneFrameRules.share(shares, 3)).isEqualTo(1.0D);
    assertThat(BoneFrameRules.share(List.of(1.5D, -0.2D), 1)).isEqualTo(1.0D);
    assertThat(BoneFrameRules.share(List.of(1.5D, -0.2D), 2)).isZero();
    assertThat(BoneFrameRules.share(List.of(Double.NaN), 1)).isZero();
    assertThat(BoneFrameRules.share(List.of(-0.5D), 1)).isZero();
  }

  @Test
  void latticeAspectsParseFromTheirIdsAndUnknownIdsAreReported() {
    List<String> warnings = new ArrayList<>();

    Set<GolemRuneType> parsed = BoneFrameRules.aspects(List.of("swiftness", " MIGHT ", "glitter", "ember", "bulwark"), warnings::add);

    assertThat(parsed).containsExactlyInAnyOrder(GolemRuneType.SWIFTNESS, GolemRuneType.MIGHT, GolemRuneType.EMBER, GolemRuneType.BULWARK);
    assertThat(warnings).singleElement().asString().contains("glitter");
    assertThat(BoneFrameRules.aspects(List.of(), warnings::add)).isEmpty();
  }

  @Test
  void theEffectiveTierIsTheLowerOfTheRuneAndTheOwnersRunicAugmentsTier() {
    assertThat(BoneFrameRules.effectiveTier(3, 2)).isEqualTo(2);
    assertThat(BoneFrameRules.effectiveTier(1, 3)).isEqualTo(1);
    assertThat(BoneFrameRules.effectiveTier(2, 0)).isZero();
    assertThat(BoneFrameRules.effectiveTier(2, -1)).isZero();
  }

  @Test
  void theActiveLatticeUsesUnlockedSlotsAllowedAspectsAndCappedTiers() {
    List<GolemRune> lattice = List.of(new GolemRune(GolemRuneType.MIGHT, 3), new GolemRune(GolemRuneType.TIDE, 2),
        new GolemRune(GolemRuneType.EMBER, 2));

    assertThat(BoneFrameRules.active(lattice, 3, ALLOWED, 2)).containsExactlyInAnyOrderEntriesOf(Map.of(GolemRuneType.MIGHT, 2, GolemRuneType.EMBER, 2));
    assertThat(BoneFrameRules.active(lattice, 2, ALLOWED, 3)).containsExactlyInAnyOrderEntriesOf(Map.of(GolemRuneType.MIGHT, 3));
    assertThat(BoneFrameRules.active(lattice, 3, ALLOWED, 0)).isEmpty();
    assertThat(BoneFrameRules.active(lattice, 0, ALLOWED, 3)).isEmpty();
  }

  @Test
  void insertingRefusesForeignAspectsDuplicatesAndAFullLattice() {
    List<GolemRune> lattice = List.of(new GolemRune(GolemRuneType.MIGHT, 1));

    assertThat(BoneFrameRules.refuse(lattice, new GolemRune(GolemRuneType.ORDER, 1), 2, ALLOWED)).contains(BoneFrameRules.Refusal.ASPECT);
    assertThat(BoneFrameRules.refuse(lattice, new GolemRune(GolemRuneType.MIGHT, 3), 2, ALLOWED)).contains(BoneFrameRules.Refusal.DUPLICATE);
    assertThat(BoneFrameRules.refuse(lattice, new GolemRune(GolemRuneType.EMBER, 2), 1, ALLOWED)).contains(BoneFrameRules.Refusal.NO_SLOT);
    assertThat(BoneFrameRules.refuse(lattice, new GolemRune(GolemRuneType.EMBER, 2), 2, ALLOWED)).isEmpty();
  }

  @Test
  void boneScaleAppliesOnlyWithBoneFrameAndAScaleOtherThanOne() {
    assertThat(BoneFrameRules.scaleAmount(0, 0.8D)).isEmpty();
    assertThat(BoneFrameRules.scaleAmount(1, 1.0D)).isEmpty();
    assertThat(BoneFrameRules.scaleAmount(1, 0.8D).orElseThrow()).isCloseTo(-0.2D, offset(1e-9));
    assertThat(BoneFrameRules.scaleAmount(3, 1.5D).orElseThrow()).isCloseTo(0.5D, offset(1e-9));
    assertThat(BoneFrameRules.scaleAmount(2, Double.NaN)).isEmpty();
  }

  @Test
  void followClearsACommandAndSetsTheFollowMode() {
    LivingEntity target = mock(Zombie.class);
    ServantOrder current = ServantOrder.hold(spot()).command(target, 5_000L);

    Optional<ServantOrder> answer = BoneFrameRules.answer(current, GolemSignal.FOLLOW, spot(), null, 0L);

    assertThat(answer).contains(ServantOrder.follow());
  }

  @Test
  void holdAnchorsTheServantWhereItStands() {
    Optional<ServantOrder> answer = BoneFrameRules.answer(ServantOrder.FREE, GolemSignal.HOLD, new Location(world, 7D, 70D, -3D), null, 0L);

    assertThat(answer).contains(ServantOrder.hold(new Location(world, 7D, 70D, -3D)));
  }

  @Test
  void sicCommandsTheTargetUntilTheHoldEndsAndKeepsTheMode() {
    LivingEntity target = mock(Zombie.class);

    Optional<ServantOrder> answer = BoneFrameRules.answer(ServantOrder.follow(), GolemSignal.SIC, spot(), target, 25_000L);

    assertThat(answer).contains(ServantOrder.follow().command(target, 25_000L));
    assertThat(BoneFrameRules.answer(ServantOrder.follow(), GolemSignal.SIC, spot(), null, 25_000L)).isEmpty();
  }

  @Test
  void homeAndWorkDoNotApplyToServants() {
    assertThat(BoneFrameRules.answer(ServantOrder.follow(), GolemSignal.HOME, spot(), null, 0L)).isEmpty();
    assertThat(BoneFrameRules.answer(ServantOrder.follow(), GolemSignal.WORK, spot(), null, 0L)).isEmpty();
    assertThat(BoneFrameRules.obeys(GolemSignal.HOME)).isFalse();
    assertThat(BoneFrameRules.obeys(GolemSignal.WORK)).isFalse();
    assertThat(BoneFrameRules.obeys(GolemSignal.FOLLOW)).isTrue();
    assertThat(BoneFrameRules.obeys(GolemSignal.HOLD)).isTrue();
    assertThat(BoneFrameRules.obeys(GolemSignal.SIC)).isTrue();
  }

  private Location spot() {
    return new Location(world, 0D, 64D, 0D);
  }
}
