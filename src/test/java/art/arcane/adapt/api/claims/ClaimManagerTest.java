package art.arcane.adapt.api.claims;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ClaimManagerTest {
  private static final UUID WORLD = UUID.randomUUID();
  private static final UUID FOUNDER = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();
  private static final UUID ATTACKER = UUID.randomUUID();
  private static final ClaimWorldAccess OVERWORLD = new ClaimWorldAccess("world", true);
  private static final ClaimRanks RANKS = new ClaimRanks(true, 1, 1, 4, true);
  private static final ClaimBlock ANCHOR = new ClaimBlock(WORLD, 2048, 64, 2048);
  private static final ClaimBlock STONE = new ClaimBlock(WORLD, 2049, 64, 2048);

  @TempDir
  Path directory;

  private ClaimsConfig config;
  private ClaimManager manager;

  @BeforeEach
  void open() throws IOException {
    config = new ClaimsConfig();
    manager = new ClaimManager(config, new ClaimRepository(directory.resolve("claims.json")));
  }

  @AfterEach
  void close() throws IOException {
    manager.close();
  }

  @Test
  void foundingRequiresBothUnlockAndFortification() {
    assertReason(() -> manager.found(FOUNDER, "Town", ANCHOR, ClaimRanks.none(), OVERWORLD), ClaimException.Reason.FOUNDING_REQUIRED);
    assertReason(() -> manager.found(FOUNDER, "Town", ANCHOR, new ClaimRanks(true, 0, 0, 0, false), OVERWORLD),
        ClaimException.Reason.FORTIFICATION_REQUIRED);
    assertThat(manager.snapshot().cities()).isEmpty();
  }

  @Test
  void cityOccupiesNineFullHeightChunksWithImmutableSnapshots() {
    ClaimCity city = found();
    ClaimSnapshot original = manager.snapshot();
    assertThat(city.chunks()).hasSize(9);
    assertThat(original.cityAt(new ClaimBlock(WORLD, 2032, -64, 2063).chunk())).contains(city);
    assertThat(original.cityAt(new ClaimBlock(WORLD, 2032, 320, 2063).chunk())).contains(city);
    assertThatThrownBy(() -> city.members().add(MEMBER)).isInstanceOf(UnsupportedOperationException.class);
    manager.expand(FOUNDER, new ClaimChunk(WORLD, 130, 128), OVERWORLD);
    assertThat(original.cities().get(city.id()).chunks()).hasSize(9);
    assertThat(manager.snapshot().cities().get(city.id()).chunks()).hasSize(10);
  }

  @ParameterizedTest
  @CsvSource({"-63,-63,true", "62,62,true", "63,62,false", "-64,-63,false", "0,63,false", "0,0,true"})
  void exclusionRejectsAnyChunkOverlappingInclusiveSquare(int x, int z, boolean excluded) {
    assertThat(config.excludes(new ClaimChunk(WORLD, x, z))).isEqualTo(excluded);
  }

  @Test
  void foundingValidatesWholeFootprintBeforePublishingAnyLand() {
    ClaimBlock overlapping = new ClaimBlock(WORLD, 63 * 16, 64, 0);
    assertReason(() -> manager.found(FOUNDER, "Town", overlapping, RANKS, OVERWORLD), ClaimException.Reason.EXCLUDED_AREA);
    assertThat(manager.snapshot().cities()).isEmpty();
    manager.found(FOUNDER, "Town", new ClaimBlock(WORLD, 64 * 16, 64, 0), RANKS, OVERWORLD);
  }

  @Test
  void negativeBlockCoordinatesUseFloorDivision() {
    assertThat(new ClaimBlock(WORLD, -1, 64, -17).chunk()).isEqualTo(new ClaimChunk(WORLD, -1, -2));
    assertThat(new ClaimChunk(WORLD, Integer.MIN_VALUE, 0).sharesEdge(new ClaimChunk(WORLD, Integer.MAX_VALUE, 0))).isFalse();
  }

  @Test
  void worldAllowlistOverridesOverworldDefault() {
    assertReason(() -> manager.found(FOUNDER, "Town", ANCHOR, RANKS, new ClaimWorldAccess("world_nether", false)),
        ClaimException.Reason.WORLD_DENIED);
    config.worldAllowlist = List.of("world_nether");
    manager.reconfigure(config);
    assertReason(() -> manager.found(FOUNDER, "Town", ANCHOR, RANKS, OVERWORLD), ClaimException.Reason.WORLD_DENIED);
    assertThat(manager.found(FOUNDER, "Town", ANCHOR, RANKS, new ClaimWorldAccess("world_nether", false))).isNotNull();
  }

  @Test
  void foundingRejectsOverlapAndSecondMembership() {
    found();
    assertReason(() -> manager.found(MEMBER, "Other", new ClaimBlock(WORLD, 2080, 64, 2048), RANKS, OVERWORLD),
        ClaimException.Reason.LAND_OCCUPIED);
    assertReason(() -> manager.found(FOUNDER, "Other", new ClaimBlock(WORLD, 4096, 64, 2048), RANKS, OVERWORLD),
        ClaimException.Reason.ALREADY_MEMBER);
    assertThat(manager.snapshot().cities()).hasSize(1);
  }

  @Test
  void expansionRequiresEdgeAdjacencyAndFounder() {
    found();
    joinMember();
    assertReason(() -> manager.expand(MEMBER, new ClaimChunk(WORLD, 130, 128), OVERWORLD), ClaimException.Reason.NOT_FOUNDER);
    assertReason(() -> manager.expand(FOUNDER, new ClaimChunk(WORLD, 130, 130), OVERWORLD), ClaimException.Reason.NOT_ADJACENT);
    assertReason(() -> manager.expand(FOUNDER, new ClaimChunk(UUID.randomUUID(), 130, 128), OVERWORLD), ClaimException.Reason.NOT_ADJACENT);
    assertThat(manager.expand(FOUNDER, new ClaimChunk(WORLD, 130, 128), OVERWORLD).chunks()).hasSize(10);
  }

  @Test
  void noExpansionRankKeepsNineChunksAndReducedRanksKeepExistingLand() {
    ClaimCity city = found();
    manager.expand(FOUNDER, new ClaimChunk(WORLD, 130, 128), OVERWORLD);
    manager.updateRanks(FOUNDER, ClaimRanks.none());
    assertThat(manager.snapshot().cities().get(city.id()).chunks()).hasSize(10);
    assertReason(() -> manager.expand(FOUNDER, new ClaimChunk(WORLD, 131, 128), OVERWORLD), ClaimException.Reason.CAPACITY_REACHED);
    manager.setOnline(FOUNDER, true, 1000);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.BREAK, 1000)).isEqualTo(16);
  }

  @Test
  void allCapacityRanksUseConfiguredValues() {
    for (int rank = 0; rank <= 4; rank++) {
      ClaimRanks ranks = new ClaimRanks(true, 1, rank, rank, false);
      assertThat(config.memberCapacity(ranks)).isEqualTo(List.of(0, 4, 8, 16, 32).get(rank));
      assertThat(config.chunkCapacity(ranks)).isEqualTo(List.of(9, 25, 49, 81, 121).get(rank));
    }
  }

  @Test
  void runtimeRankLimitsApplyToOfflineCitiesWithoutReplacingPurchasedRanks() throws IOException {
    ClaimRanks purchased = new ClaimRanks(true, 4, 4, 4, true);
    ClaimCity city = manager.found(FOUNDER, "Town", ANCHOR, purchased, OVERWORLD);
    expandEast(130, 132);
    manager.setRankLimits(new ClaimRanks(true, 2, 0, 0, false));

    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.BREAK, System.currentTimeMillis() + 600_000)).isEqualTo(256);
    assertReason(() -> manager.invite(FOUNDER, MEMBER, 1000), ClaimException.Reason.MEMBER_CAPACITY_REACHED);
    assertReason(() -> manager.expand(FOUNDER, new ClaimChunk(WORLD, 133, 128), OVERWORLD), ClaimException.Reason.CAPACITY_REACHED);
    ClaimBlock personal = new ClaimBlock(WORLD, 131 * 16, 64, 2048);
    assertReason(() -> manager.createPlot(FOUNDER, personal, purchased), ClaimException.Reason.HOMESTEAD_REQUIRED);
    assertThat(manager.snapshot().cities().get(city.id()).ranks()).isEqualTo(purchased);
    assertThat(manager.snapshot().cities().get(city.id()).chunks()).hasSize(18);

    manager.close();
    manager = new ClaimManager(config, new ClaimRepository(directory.resolve("claims.json")));
    manager.setRankLimits(ClaimRanks.none());
    assertThat(manager.snapshot().cities().get(city.id()).ranks()).isEqualTo(purchased);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.BREAK, System.currentTimeMillis() + 600_000)).isEqualTo(128);

    manager.setRankLimits(purchased);
    manager.invite(FOUNDER, MEMBER, 2000);
    manager.accept(MEMBER, FOUNDER, 2001);
    manager.createPlot(FOUNDER, personal, purchased);
    assertThat(manager.expand(FOUNDER, new ClaimChunk(WORLD, 133, 128), OVERWORLD).chunks()).hasSize(19);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.BREAK, System.currentTimeMillis() + 600_000)).isEqualTo(512);
  }

  @Test
  void disabledFoundingAndFortificationCannotActivateAnAnchor() {
    manager.setRankLimits(new ClaimRanks(false, 4, 4, 4, true));
    assertReason(this::found, ClaimException.Reason.FOUNDING_REQUIRED);
    manager.setRankLimits(new ClaimRanks(true, 0, 4, 4, true));
    assertReason(this::found, ClaimException.Reason.FORTIFICATION_REQUIRED);
    assertThat(manager.snapshot().cities()).isEmpty();
  }

  @Test
  void invitationsRequireAcceptanceAndEnforceExpiry() {
    found();
    manager.invite(FOUNDER, MEMBER, 1000);
    assertThat(manager.snapshot().cityOf(MEMBER)).isEmpty();
    assertReason(() -> manager.accept(MEMBER, ATTACKER, 1001), ClaimException.Reason.INVITATION_MISSING);
    assertReason(() -> manager.accept(MEMBER, FOUNDER, 121000), ClaimException.Reason.INVITATION_EXPIRED);
    manager.invite(FOUNDER, MEMBER, 122000);
    manager.accept(MEMBER, FOUNDER, 122001);
    assertThat(manager.snapshot().cityOf(MEMBER)).isPresent();
    assertThat(manager.invitation(MEMBER)).isEmpty();
  }

  @Test
  void invitationsRecheckMembershipAndCapacityAtAcceptance() {
    found();
    manager.invite(FOUNDER, MEMBER, 1000);
    manager.updateRanks(FOUNDER, new ClaimRanks(true, 1, 0, 4, true));
    assertReason(() -> manager.accept(MEMBER, FOUNDER, 1001), ClaimException.Reason.MEMBER_CAPACITY_REACHED);
    manager.updateRanks(FOUNDER, RANKS);
    manager.accept(MEMBER, FOUNDER, 1001);
    assertReason(() -> manager.invite(FOUNDER, MEMBER, 1002), ClaimException.Reason.ALREADY_MEMBER);
    for (int index = 0; index < 3; index++) {
      UUID member = UUID.randomUUID();
      manager.invite(FOUNDER, member, 1002);
      manager.accept(member, FOUNDER, 1003);
    }
    assertReason(() -> manager.invite(FOUNDER, ATTACKER, 1004), ClaimException.Reason.MEMBER_CAPACITY_REACHED);
    assertThat(manager.snapshot().cityOf(FOUNDER).orElseThrow().members()).hasSize(4);
  }

  @Test
  void founderCannotLeaveAndOnlyFounderRemovesMembers() {
    found();
    joinMember();
    assertReason(() -> manager.leave(FOUNDER), ClaimException.Reason.FOUNDER_CANNOT_LEAVE);
    assertReason(() -> manager.removeMember(MEMBER, FOUNDER), ClaimException.Reason.NOT_FOUNDER);
    manager.leave(MEMBER);
    assertThat(manager.snapshot().cityOf(MEMBER)).isEmpty();
  }

  @Test
  void homesteadRequiresCompleteCityFootprintAndExcludesMainAnchorFootprint() {
    found();
    joinMember();
    assertReason(() -> manager.createPlot(MEMBER, ANCHOR, ClaimRanks.none()), ClaimException.Reason.HOMESTEAD_REQUIRED);
    assertReason(() -> manager.createPlot(MEMBER, ANCHOR, RANKS), ClaimException.Reason.MAIN_ANCHOR_OVERLAP);
    ClaimBlock plotAnchor = new ClaimBlock(WORLD, 131 * 16, 64, 2048);
    assertReason(() -> manager.createPlot(MEMBER, plotAnchor, RANKS), ClaimException.Reason.PLOT_OUTSIDE_CITY);
    expandEast(130, 132);
    ClaimPlot plot = manager.createPlot(MEMBER, plotAnchor, RANKS);
    assertThat(plot.chunks()).hasSize(9);
    assertReason(() -> manager.createPlot(FOUNDER, plotAnchor, RANKS), ClaimException.Reason.PLOT_OVERLAP);
    assertReason(() -> manager.createPlot(MEMBER, plotAnchor, RANKS), ClaimException.Reason.ALREADY_HAS_PLOT);
  }

  @ParameterizedTest
  @CsvSource({"130,128", "128,130", "130,130"})
  void homesteadRejectsMainFootprintEdgeAndCornerOverlap(int chunkX, int chunkZ) {
    ClaimCity city = found();
    joinMember();
    for (int x = 127; x <= 131; x++) {
      for (int z = 127; z <= 131; z++) {
        ClaimChunk chunk = new ClaimChunk(WORLD, x, z);
        if (manager.snapshot().cityAt(chunk).isEmpty()) {
          manager.expand(FOUNDER, chunk, OVERWORLD);
        }
      }
    }
    ClaimBlock plotAnchor = new ClaimBlock(WORLD, chunkX * 16, 200, chunkZ * 16);

    assertReason(() -> manager.createPlot(MEMBER, plotAnchor, RANKS), ClaimException.Reason.MAIN_ANCHOR_OVERLAP);

    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cityAt(plotAnchor.chunk())).get().extracting(ClaimCity::id).isEqualTo(city.id());
  }

  @Test
  void removedMemberKeepsPersonalOwnershipAndFounderFacesResistance() {
    found();
    joinMember();
    ClaimPlot plot = personalPlot();
    ClaimBlock personalBlock = new ClaimBlock(WORLD, plot.anchor().x() + 1, 100, plot.anchor().z());
    assertThat(manager.canEdit(FOUNDER, STONE)).isTrue();
    assertThat(manager.canEdit(MEMBER, STONE)).isTrue();
    assertThat(manager.canEdit(FOUNDER, personalBlock)).isFalse();
    assertThat(manager.canEdit(MEMBER, personalBlock)).isTrue();
    manager.removeMember(FOUNDER, MEMBER);
    assertThat(manager.snapshot().plots()).containsKey(MEMBER);
    assertThat(manager.canEdit(MEMBER, personalBlock)).isTrue();
    assertThat(manager.canEdit(MEMBER, STONE)).isFalse();
  }

  @Test
  void fellowMembersUseOnlinePersonalResistanceWhileOutsidersUseCityState() {
    ClaimCity city = found();
    joinMember();
    ClaimPlot plot = personalPlot();
    manager.setOnline(FOUNDER, true, 1000);
    manager.setOnline(FOUNDER, false, 2000);
    assertThat(manager.isOnline(city.id(), 302000)).isFalse();
    assertThat(manager.resistance(FOUNDER, plot.anchor(), ClaimAction.BREAK, 302000)).isEqualTo(16);
    assertThat(manager.resistance(ATTACKER, plot.anchor(), ClaimAction.BREAK, 302000)).isEqualTo(128);
  }

  @Test
  void offlineGraceStartsOnlyAfterLastMemberDisconnects() {
    ClaimCity city = found();
    joinMember();
    manager.setOnline(FOUNDER, true, 1000);
    manager.setOnline(MEMBER, true, 2000);
    manager.setOnline(FOUNDER, false, 3000);
    assertThat(manager.isOnline(city.id(), 1_000_000)).isTrue();
    manager.setOnline(MEMBER, false, 4000);
    assertThat(manager.isOnline(city.id(), 303999)).isTrue();
    assertThat(manager.isOnline(city.id(), 304000)).isFalse();
    manager.setOnline(MEMBER, true, 400000);
    assertThat(manager.isOnline(city.id(), 400000)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"1,16,128,64,512", "2,32,256,128,1024", "3,48,384,192,1536", "4,64,512,256,2048"})
  void resistanceTableMatchesEveryRank(int rank, int onlineBreaks, int offlineBreaks, int onlinePlacements, int offlinePlacements) {
    found();
    manager.updateRanks(FOUNDER, new ClaimRanks(true, rank, 1, 4, true));
    manager.setOnline(FOUNDER, true, 1000);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.BREAK, 1000)).isEqualTo(onlineBreaks);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.PLACE, 1000)).isEqualTo(onlinePlacements);
    manager.setOnline(FOUNDER, false, 2000);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.BREAK, 302000)).isEqualTo(offlineBreaks);
    assertThat(manager.resistance(ATTACKER, STONE, ClaimAction.PLACE, 302000)).isEqualTo(offlinePlacements);
  }

  @ParameterizedTest
  @CsvSource({"1,true,BREAK,16", "2,true,BREAK,32", "3,true,BREAK,48", "4,true,BREAK,64",
      "1,false,BREAK,128", "2,false,BREAK,256", "3,false,BREAK,384", "4,false,BREAK,512",
      "1,true,PLACE,64", "2,true,PLACE,128", "3,true,PLACE,192", "4,true,PLACE,256",
      "1,false,PLACE,512", "2,false,PLACE,1024", "3,false,PLACE,1536", "4,false,PLACE,2048"})
  void everyThresholdAllowsExactlyTheFinalAttempt(int rank, boolean online, ClaimAction action, int required) {
    found();
    manager.updateRanks(FOUNDER, new ClaimRanks(true, rank, 1, 4, true));
    manager.setOnline(FOUNDER, true, 1000);
    if (!online) {
      manager.setOnline(FOUNDER, false, 2000);
    }
    for (int count = 1; count <= required; count++) {
      ClaimAttemptResult result = attempt(ATTACKER, STONE, action, "stone", 302000L + count * 250L);
      assertThat(result.credited()).isEqualTo(count < required);
      assertThat(result.allowed()).isEqualTo(count == required);
    }
    assertThat(manager.progress(STONE).orElseThrow().progress(action)).isCloseTo((required - 1D) / required,
        offset(1E-10));
  }

  @Test
  void failedReloadKeepsPriorValidConfiguration() {
    found();
    config.placementAttemptsPerSecond = 5;
    assertThatThrownBy(() -> manager.reconfigure(config)).isInstanceOf(IllegalArgumentException.class);
    assertThat(manager.config().placementAttemptsPerSecond).isEqualTo(4);
  }

  @Test
  void attackersShareBreakProgressWithoutRoundingOrEarlyCompletion() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    for (int count = 1; count <= 16; count++) {
      ClaimAttemptResult result = attempt(count % 2 == 0 ? MEMBER : ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000);
      assertThat(result.allowed()).isEqualTo(count == 16);
      assertThat(result.progress()).isEqualTo(count / 16D);
    }
    assertThat(attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000).allowed()).isTrue();
    assertThat(manager.progress(STONE).orElseThrow().breaking()).isEqualTo(15 / 16D);
    manager.blockChanged(STONE);
    assertThat(manager.progress(STONE)).isEmpty();
    assertThat(attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000).progress()).isEqualTo(1 / 16D);
  }

  @Test
  void rankChangesPreserveNormalizedCompletedWork() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    for (int index = 0; index < 8; index++) {
      attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000);
    }
    manager.updateRanks(FOUNDER, new ClaimRanks(true, 2, 1, 4, true));
    for (int index = 1; index <= 16; index++) {
      assertThat(attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000).allowed()).isEqualTo(index == 16);
    }
  }

  @Test
  void changedBlockStateResetsBothCounters() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000);
    attempt(ATTACKER, STONE, ClaimAction.PLACE, "stone", 1000);
    ClaimProgress before = manager.progress(STONE).orElseThrow();
    assertThat(before.breaking()).isPositive();
    assertThat(before.placement()).isPositive();
    attempt(ATTACKER, STONE, ClaimAction.BREAK, "dirt", 1001);
    ClaimProgress after = manager.progress(STONE).orElseThrow();
    assertThat(after.breaking()).isEqualTo(1 / 16D);
    assertThat(after.placement()).isZero();
  }

  @Test
  void externalDenialsGiveNoCreditOrPlacementDebit() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    for (int index = 0; index < 8; index++) {
      assertThat(manager.attempt(new ClaimAttempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 1000, false)).credited()).isFalse();
    }
    assertThat(manager.progress(STONE)).isEmpty();
    for (int index = 0; index < 4; index++) {
      assertThat(attempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 1000).credited()).isTrue();
    }
    assertThat(attempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 1000).credited()).isFalse();
    assertThat(attempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 1999).credited()).isFalse();
    assertThat(attempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 2000).credited()).isTrue();
    assertThat(attempt(MEMBER, STONE, ClaimAction.PLACE, "air", 2000).credited()).isTrue();
  }

  @Test
  void multiBlockPlacementsDebitOneAttemptAndValidateEveryPosition() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    ClaimBlock upper = new ClaimBlock(WORLD, STONE.x(), STONE.y() + 1, STONE.z());
    for (int index = 0; index < 4; index++) {
      List<ClaimAttemptResult> results = manager.attemptAll(List.of(
          new ClaimAttempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 1000, true),
          new ClaimAttempt(ATTACKER, upper, ClaimAction.PLACE, "air", 1000, true)));
      assertThat(results).allMatch(ClaimAttemptResult::credited);
    }
    assertThat(manager.progress(STONE).orElseThrow().placement()).isEqualTo(4 / 64D);
    assertThat(manager.progress(upper).orElseThrow().placement()).isEqualTo(4 / 64D);
    List<ClaimAttemptResult> denied = manager.attemptAll(List.of(
        new ClaimAttempt(ATTACKER, STONE, ClaimAction.PLACE, "air", 2000, true),
        new ClaimAttempt(ATTACKER, upper, ClaimAction.PLACE, "air", 2000, false)));
    assertThat(denied).noneMatch(ClaimAttemptResult::credited);
    assertThat(manager.progress(STONE).orElseThrow().placement()).isEqualTo(4 / 64D);
  }

  @Test
  void multiBlockAttemptsPersistReadyCounterpartsUntilTheWholeActionCanComplete() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    ClaimBlock upper = new ClaimBlock(WORLD, STONE.x(), STONE.y() + 1, STONE.z());
    for (int index = 0; index < 15; index++) {
      attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000);
    }
    for (int count = 1; count <= 16; count++) {
      List<ClaimAttemptResult> results = manager.attemptAll(List.of(
          new ClaimAttempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000, true),
          new ClaimAttempt(ATTACKER, upper, ClaimAction.BREAK, "stone", 1000, true)));
      assertThat(results.getFirst().allowed()).isTrue();
      assertThat(results.getFirst().credited()).isEqualTo(count == 1);
      assertThat(results.getLast().allowed()).isEqualTo(count == 16);
      assertThat(results.getLast().credited()).isEqualTo(count < 16);
    }
    assertThat(manager.progress(STONE).orElseThrow().breaking()).isEqualTo(1);
    assertThat(manager.progress(upper).orElseThrow().breaking()).isEqualTo(15 / 16D);
  }

  @Test
  void finalUnconfirmedAttemptDoesNotPersistAcrossRestart() throws IOException {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    for (int count = 1; count <= 16; count++) {
      attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000);
    }
    manager.close();
    manager = new ClaimManager(config, new ClaimRepository(directory.resolve("claims.json")));
    assertThat(manager.progress(STONE).orElseThrow().breaking()).isEqualTo(15 / 16D);
  }

  @Test
  void immediatelyAllowedChangedBlockClearsStaleCountersWithoutAddingFinalCredit() {
    found();
    manager.setOnline(FOUNDER, true, 1000);
    attempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000);
    attempt(ATTACKER, STONE, ClaimAction.PLACE, "stone", 1000);
    config.resistanceRanks.rank1 = new ClaimResistance(1, 1, 1, 1);
    manager.reconfigure(config);
    ClaimAttemptResult result = attempt(ATTACKER, STONE, ClaimAction.BREAK, "dirt", 1001);
    assertThat(result.allowed()).isTrue();
    assertThat(result.credited()).isFalse();
    assertThat(manager.progress(STONE)).isEmpty();
  }

  @Test
  void nonFounderMembersMustRaidMainAnchor() {
    found();
    joinMember();
    manager.setOnline(MEMBER, true, 1000);
    assertThat(manager.canEdit(MEMBER, ANCHOR)).isFalse();
    assertThat(manager.canEdit(FOUNDER, ANCHOR)).isTrue();
    assertThat(attempt(MEMBER, ANCHOR, ClaimAction.BREAK, "lodestone", 1000).allowed()).isFalse();
  }

  @Test
  void personalAnchorDestructionReturnsLandToCityAndMainAnchorReleasesAllOwnership() {
    ClaimCity city = found();
    joinMember();
    ClaimPlot plot = personalPlot();
    manager.destroyAnchor(plot.anchor());
    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cityAt(plot.anchor().chunk())).isPresent();
    assertThat(manager.canEdit(FOUNDER, plot.anchor())).isTrue();
    personalPlot();
    manager.destroyAnchor(ANCHOR);
    assertThat(manager.snapshot().cities()).doesNotContainKey(city.id());
    assertThat(manager.snapshot().plots()).isEmpty();
    assertThat(manager.snapshot().cityOf(MEMBER)).isEmpty();
  }

  @Test
  void simultaneousFoundersCannotClaimOverlappingLand() throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> first = () -> tryFound(FOUNDER);
      Callable<Boolean> second = () -> tryFound(MEMBER);
      List<Future<Boolean>> results = executor.invokeAll(List.of(first, second));
      int successes = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          successes++;
        }
      }
      assertThat(successes).isEqualTo(1);
      assertThat(manager.snapshot().cities()).hasSize(1);
    }
  }

  @Test
  void configurationRejectsUnsafeLimitsAndInvalidResistance() {
    config.placementAttemptsPerSecond = 5;
    assertThatThrownBy(config::validate).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ClaimResistance(0, 128, 64, 512)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ClaimRanks(true, 5, 0, 0, false)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ClaimProgress(STONE, "stone", Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  private ClaimCity found() {
    return manager.found(FOUNDER, "Town", ANCHOR, RANKS, OVERWORLD);
  }

  private void joinMember() {
    manager.invite(FOUNDER, MEMBER, 1000);
    manager.accept(MEMBER, FOUNDER, 1001);
  }

  private ClaimPlot personalPlot() {
    expandEast(130, 132);
    return manager.createPlot(MEMBER, new ClaimBlock(WORLD, 131 * 16, 64, 2048), RANKS);
  }

  private void expandEast(int from, int through) {
    for (int x = from; x <= through; x++) {
      for (int z = 127; z <= 129; z++) {
        ClaimChunk chunk = new ClaimChunk(WORLD, x, z);
        if (manager.snapshot().cityAt(chunk).isEmpty()) {
          manager.expand(FOUNDER, chunk, OVERWORLD);
        }
      }
    }
  }

  private ClaimAttemptResult attempt(UUID player, ClaimBlock block, ClaimAction action, String state, long now) {
    return manager.attempt(new ClaimAttempt(player, block, action, state, now, true));
  }

  private boolean tryFound(UUID player) {
    try {
      manager.found(player, "Town", ANCHOR, RANKS, OVERWORLD);
      return true;
    } catch (ClaimException exception) {
      assertThat(exception.reason()).isEqualTo(ClaimException.Reason.LAND_OCCUPIED);
      return false;
    }
  }

  private void assertReason(Runnable action, ClaimException.Reason reason) {
    assertThatExceptionOfType(ClaimException.class).isThrownBy(action::run)
        .satisfies(exception -> assertThat(exception.reason()).isEqualTo(reason));
  }
}
