package art.arcane.adapt.content.skill.kinetics;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeWorld;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsAnvilsTest {
  private static WorldRef namedWorld(String name) {
    return new FakeWorld(name);
  }

  @Test
  void isAnvilAcceptsAllThreeAnvilStatesOnly() {
    assertThat(KineticsAnvils.isAnvil(Id.minecraft("anvil"))).isTrue();
    assertThat(KineticsAnvils.isAnvil(Id.minecraft("chipped_anvil"))).isTrue();
    assertThat(KineticsAnvils.isAnvil(Id.minecraft("damaged_anvil"))).isTrue();
    assertThat(KineticsAnvils.isAnvil(Id.minecraft("stone"))).isFalse();
    assertThat(KineticsAnvils.isAnvil(Id.minecraft("iron_block"))).isFalse();
    assertThat(KineticsAnvils.isAnvil(null)).isFalse();
  }

  @Test
  void crushXpScalesWithFallDistanceOnKills() {
    assertThat(KineticsAnvils.crushXp(20, 2, 6, 20, 0.6, 10, true, 1.5, 250))
        .isCloseTo(76.8, offset(1e-9));
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, 1.5, 250))
        .isCloseTo(163.2, offset(1e-9));
  }

  @Test
  void crushXpSaturatesAtThePerEventCap() {
    assertThat(KineticsAnvils.crushXp(20, 20, 6, 20, 0.6, 10, true, 1.5, 250))
        .isCloseTo(250.0, offset(1e-9));
  }

  @Test
  void crushXpChipsPayByDamageFractionWithAFloor() {
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 8, false, 1.5, 250))
        .isCloseTo(43.52, offset(1e-9));
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 0.5, false, 1.5, 250))
        .isCloseTo(10.88, offset(1e-9));
  }

  @Test
  void crushXpKillsPayMoreThanChipsForTheSameFall() {
    double kill = KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 8, true, 1.5, 250);
    double chip = KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 8, false, 1.5, 250);
    assertThat(kill).isGreaterThan(chip);
  }

  @Test
  void crushXpClampsTinyTargetHealthToOne() {
    assertThat(KineticsAnvils.crushXp(20, 2, 6, 0.5, 0.6, 0.5, false, 1.5, 250))
        .isCloseTo(16.24, offset(1e-9));
  }

  @Test
  void crushXpPaysNothingBelowOneBlockOfFall() {
    assertThat(KineticsAnvils.crushXp(20, 0.99, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 0, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, -3, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, Double.NaN, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
  }

  @Test
  void crushXpRejectsNonFiniteInputsAndOverflow() {
    assertThat(KineticsAnvils.crushXp(Double.NaN, 8, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(Double.POSITIVE_INFINITY, 8, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, Double.POSITIVE_INFINITY, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, Double.POSITIVE_INFINITY, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, Double.POSITIVE_INFINITY, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, Double.NEGATIVE_INFINITY, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, Double.NaN, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, Double.POSITIVE_INFINITY, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, Double.POSITIVE_INFINITY, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, 1.5, Double.NaN)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, 1.5, Double.POSITIVE_INFINITY)).isZero();
    assertThat(KineticsAnvils.crushXp(Double.MAX_VALUE, 8, Double.MAX_VALUE, 20, 0.6, 10, true, 1.5, Double.MAX_VALUE)).isZero();
  }

  @Test
  void crushXpRejectsNonPositiveConfigValues() {
    assertThat(KineticsAnvils.crushXp(0, 8, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 0, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, 0, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, 1.5, 0)).isZero();
    assertThat(KineticsAnvils.crushXp(-1, 8, 6, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, -1, 20, 0.6, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, -1, 10, true, 1.5, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, -1, 250)).isZero();
    assertThat(KineticsAnvils.crushXp(20, 8, 6, 20, 0.6, 10, true, 1.5, -1)).isZero();
  }

  @Test
  void shareXpAppliesTheShareFactor() {
    assertThat(KineticsAnvils.shareXp(100, 0.35)).isCloseTo(35.0, offset(1e-9));
    assertThat(KineticsAnvils.shareXp(0, 0.35)).isZero();
  }

  @Test
  void shareXpRejectsInvalidInputsAndOverflow() {
    assertThat(KineticsAnvils.shareXp(-1, 0.35)).isZero();
    assertThat(KineticsAnvils.shareXp(100, 0)).isZero();
    assertThat(KineticsAnvils.shareXp(100, -0.35)).isZero();
    assertThat(KineticsAnvils.shareXp(Double.NaN, 0.35)).isZero();
    assertThat(KineticsAnvils.shareXp(100, Double.POSITIVE_INFINITY)).isZero();
    assertThat(KineticsAnvils.shareXp(Double.MAX_VALUE, 2)).isZero();
  }

  @Test
  void crushResultCarriesXpAndQualification() {
    KineticsAnvils.CrushResult result = new KineticsAnvils.CrushResult(76.8, true);
    assertThat(result.xp()).isCloseTo(76.8, offset(1e-9));
    assertThat(result.advancementQualifies()).isTrue();
  }

  @Test
  void placementFlowsThroughFallToOwnerWithinTtl() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 10.2, 64.0, -4.8), placer, 1_000L);
    anvils.beginFall(fallingBlock, new Loc(world, 10.9, 64.9, -4.1), 2_000L);

    assertThat(anvils.ownerOf(fallingBlock, 3_000L, 120_000L)).isEqualTo(placer);
  }

  @Test
  void fallPreservesTheOriginalPlacementTimestamp() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 10, 64, 10), placer, 1_000L);
    anvils.beginFall(fallingBlock, new Loc(world, 10, 64, 10), 100_000L);

    assertThat(anvils.ownerOf(fallingBlock, 120_000L, 120_000L)).isEqualTo(placer);
    assertThat(anvils.ownerOf(fallingBlock, 121_001L, 120_000L)).isNull();
  }

  @Test
  void landingPreservesClaimAndOriginalPlacementTimestamp() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID firstFall = UUID.randomUUID();
    UUID secondFall = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 10, 80, 10), placer, 1_000L);
    anvils.beginFall(firstFall, new Loc(world, 10, 80, 10), 2_000L);
    anvils.land(firstFall, new Loc(world, 10, 64, 10));
    anvils.beginFall(secondFall, new Loc(world, 10, 64, 10), 100_000L);

    assertThat(anvils.ownerOf(firstFall, 3_000L, 120_000L)).isNull();
    assertThat(anvils.ownerOf(secondFall, 120_000L, 120_000L)).isEqualTo(placer);
    assertThat(anvils.ownerOf(secondFall, 121_001L, 120_000L)).isNull();
  }

  @Test
  void unknownLandingClearsAStaleDestinationClaim() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    Loc destination = new Loc(world, 4, 64, 4);
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(destination, UUID.randomUUID(), 1_000L);
    anvils.land(UUID.randomUUID(), destination);
    anvils.beginFall(fallingBlock, destination, 2_000L);

    assertThat(anvils.ownerOf(fallingBlock, 2_001L, 120_000L)).isNull();
  }

  @Test
  void explicitClaimClearingRemovesPlacementsAndFalls() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    Loc first = new Loc(world, 2, 64, 2);
    Loc second = new Loc(world, 3, 64, 3);
    UUID placer = UUID.randomUUID();
    UUID firstFall = UUID.randomUUID();
    UUID secondFall = UUID.randomUUID();

    anvils.recordPlacement(first, placer, 1_000L);
    anvils.clearPlacement(first);
    anvils.beginFall(firstFall, first, 2_000L);
    assertThat(anvils.ownerOf(firstFall, 2_001L, 120_000L)).isNull();

    anvils.recordPlacement(second, placer, 1_000L);
    anvils.beginFall(secondFall, second, 2_000L);
    anvils.clearFall(secondFall);
    assertThat(anvils.ownerOf(secondFall, 2_001L, 120_000L)).isNull();
  }

  @Test
  void ownerOfReturnsNullPastTtlAndAtBoundaryStillOwns() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 0.0, 64.0, 0.0), placer, 1_000L);
    anvils.beginFall(fallingBlock, new Loc(world, 0.0, 64.0, 0.0), 1_000L);

    assertThat(anvils.ownerOf(fallingBlock, 1_000L + 120_000L, 120_000L)).isEqualTo(placer);
    assertThat(anvils.ownerOf(fallingBlock, 1_000L + 120_001L, 120_000L)).isNull();
    assertThat(anvils.ownerOf(fallingBlock, 1_500L, 120_000L)).isNull();
  }

  @Test
  void ownerOfReturnsNullForUnknownFallingBlocks() {
    KineticsAnvils anvils = new KineticsAnvils();
    assertThat(anvils.ownerOf(UUID.randomUUID(), 1_000L, 120_000L)).isNull();
  }

  @Test
  void beginFallFromAnUnclaimedLocationYieldsNoOwner() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID fallingBlock = UUID.randomUUID();

    anvils.beginFall(fallingBlock, new Loc(world, 5.0, 70.0, 5.0), 1_000L);

    assertThat(anvils.ownerOf(fallingBlock, 1_001L, 120_000L)).isNull();
  }

  @Test
  void worldsWithSameCoordsDoNotCollide() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef overworld = namedWorld("world");
    WorldRef nether = namedWorld("world_nether");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(overworld, 3.0, 64.0, 3.0), placer, 1_000L);
    anvils.beginFall(fallingBlock, new Loc(nether, 3.0, 64.0, 3.0), 1_100L);

    assertThat(anvils.ownerOf(fallingBlock, 1_200L, 120_000L)).isNull();
  }

  @Test
  void pistonTransferRekeysTheClaim() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fromOld = UUID.randomUUID();
    UUID fromNew = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 8.0, 70.0, 8.0), placer, 1_000L);
    anvils.transferPiston(new Loc(world, 8.0, 70.0, 8.0), new Loc(world, 9.0, 70.0, 8.0));

    anvils.beginFall(fromOld, new Loc(world, 8.0, 70.0, 8.0), 2_000L);
    assertThat(anvils.ownerOf(fromOld, 2_100L, 120_000L)).isNull();

    anvils.beginFall(fromNew, new Loc(world, 9.0, 70.0, 8.0), 2_000L);
    assertThat(anvils.ownerOf(fromNew, 2_100L, 120_000L)).isEqualTo(placer);
  }

  @Test
  void pistonTransferOfAnUnclaimedLocationLeavesDestinationUnclaimed() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID fallingBlock = UUID.randomUUID();

    anvils.transferPiston(new Loc(world, 1.0, 64.0, 1.0), new Loc(world, 2.0, 64.0, 1.0));
    anvils.beginFall(fallingBlock, new Loc(world, 2.0, 64.0, 1.0), 1_000L);

    assertThat(anvils.ownerOf(fallingBlock, 1_100L, 120_000L)).isNull();
  }

  @Test
  void multiBlockPistonTransferIsAtomicAndOrderIndependent() {
    WorldRef world = namedWorld("world");
    Loc first = new Loc(world, 1, 64, 1);
    Loc second = new Loc(world, 2, 64, 1);
    Loc third = new Loc(world, 3, 64, 1);
    UUID firstOwner = UUID.randomUUID();
    UUID secondOwner = UUID.randomUUID();

    assertAtomicTransfer(first, second, third, firstOwner, secondOwner, List.of(
        new KineticsAnvils.PistonTransfer(first, second),
        new KineticsAnvils.PistonTransfer(second, third)));
    assertAtomicTransfer(first, second, third, firstOwner, secondOwner, List.of(
        new KineticsAnvils.PistonTransfer(second, third),
        new KineticsAnvils.PistonTransfer(first, second)));
  }

  @Test
  void pistonTransferClearsAnUnmovedDestinationClaim() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    Loc unclaimedSource = new Loc(world, 1, 64, 1);
    Loc staleDestination = new Loc(world, 2, 64, 1);
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(staleDestination, UUID.randomUUID(), 1_000L);
    anvils.transferPistons(List.of(new KineticsAnvils.PistonTransfer(unclaimedSource, staleDestination)));
    anvils.beginFall(fallingBlock, staleDestination, 2_000L);

    assertThat(anvils.ownerOf(fallingBlock, 2_001L, 120_000L)).isNull();
  }

  @Test
  void expireSweepsStalePlacements() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 4.0, 64.0, 4.0), placer, 1_000L);
    anvils.expire(1_000L + 120_001L, 120_000L);
    anvils.beginFall(fallingBlock, new Loc(world, 4.0, 64.0, 4.0), 1_000L + 120_002L);

    assertThat(anvils.ownerOf(fallingBlock, 1_000L + 120_003L, 120_000L)).isNull();
  }

  @Test
  void expireSweepsStaleFallClaims() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 6.0, 64.0, 6.0), placer, 1_000L);
    anvils.beginFall(fallingBlock, new Loc(world, 6.0, 64.0, 6.0), 2_000L);
    anvils.expire(2_000L + 120_001L, 120_000L);

    assertThat(anvils.ownerOf(fallingBlock, 2_000L + 120_002L, 240_000L)).isNull();
  }

  @Test
  void expireKeepsFreshClaims() {
    KineticsAnvils anvils = new KineticsAnvils();
    WorldRef world = namedWorld("world");
    UUID placer = UUID.randomUUID();
    UUID fallingBlock = UUID.randomUUID();

    anvils.recordPlacement(new Loc(world, 7.0, 64.0, 7.0), placer, 1_000L);
    anvils.expire(2_000L, 120_000L);
    anvils.beginFall(fallingBlock, new Loc(world, 7.0, 64.0, 7.0), 3_000L);
    anvils.expire(4_000L, 120_000L);

    assertThat(anvils.ownerOf(fallingBlock, 5_000L, 120_000L)).isEqualTo(placer);
  }

  private static void assertAtomicTransfer(Loc first, Loc second, Loc third,
      UUID firstOwner, UUID secondOwner, List<KineticsAnvils.PistonTransfer> transfers) {
    KineticsAnvils anvils = new KineticsAnvils();
    UUID sourceFall = UUID.randomUUID();
    UUID firstDestinationFall = UUID.randomUUID();
    UUID secondDestinationFall = UUID.randomUUID();
    anvils.recordPlacement(first, firstOwner, 1_000L);
    anvils.recordPlacement(second, secondOwner, 1_000L);

    anvils.transferPistons(transfers);
    anvils.beginFall(sourceFall, first, 2_000L);
    anvils.beginFall(firstDestinationFall, second, 2_000L);
    anvils.beginFall(secondDestinationFall, third, 2_000L);

    assertThat(anvils.ownerOf(sourceFall, 2_001L, 120_000L)).isNull();
    assertThat(anvils.ownerOf(firstDestinationFall, 2_001L, 120_000L)).isEqualTo(firstOwner);
    assertThat(anvils.ownerOf(secondDestinationFall, 2_001L, 120_000L)).isEqualTo(secondOwner);
  }
}
