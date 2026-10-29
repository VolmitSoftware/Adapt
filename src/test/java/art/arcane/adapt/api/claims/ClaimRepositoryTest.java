package art.arcane.adapt.api.claims;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimRepositoryTest {
  private static final UUID WORLD = UUID.randomUUID();
  private static final UUID FOUNDER = UUID.randomUUID();
  private static final UUID ATTACKER = UUID.randomUUID();
  private static final ClaimBlock ANCHOR = new ClaimBlock(WORLD, 2048, 64, 2048);
  private static final ClaimBlock STONE = new ClaimBlock(WORLD, 2049, 64, 2048);
  private static final ClaimRanks RANKS = new ClaimRanks(true, 1, 1, 4, true);
  private static final ClaimWorldAccess OVERWORLD = new ClaimWorldAccess("world", true);

  @TempDir
  Path directory;

  @Test
  void restartPreservesOwnershipRanksAndSeparateNormalizedProgress() throws IOException {
    Path data = directory.resolve("claims.json");
    UUID cityId;
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(data))) {
      cityId = manager.found(FOUNDER, "Town", ANCHOR, RANKS, OVERWORLD).id();
      manager.setOnline(FOUNDER, true, 1000);
      manager.invite(FOUNDER, ATTACKER, 1000);
      for (int index = 0; index < 8; index++) {
        manager.attempt(new ClaimAttempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000, true));
      }
      manager.attempt(new ClaimAttempt(ATTACKER, STONE, ClaimAction.PLACE, "stone", 1000, true));
      manager.updateRanks(FOUNDER, new ClaimRanks(true, 2, 1, 4, true));
      manager.flush();
      assertThat(Files.readString(data)).contains("Town");
    }
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(data))) {
      ClaimCity city = manager.snapshot().cities().get(cityId);
      assertThat(city.founder()).isEqualTo(FOUNDER);
      assertThat(city.ranks().fortification()).isEqualTo(2);
      assertThat(manager.progress(STONE).orElseThrow().breaking()).isEqualTo(0.5);
      assertThat(manager.progress(STONE).orElseThrow().placement()).isEqualTo(1 / 64D);
      assertThat(manager.invitation(ATTACKER)).isEmpty();
      manager.setOnline(FOUNDER, true, 1000);
      for (int index = 1; index <= 16; index++) {
        assertThat(manager.attempt(new ClaimAttempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000, true)).allowed()).isEqualTo(index == 16);
      }
    }
    try (Stream<Path> files = Files.list(directory)) {
      assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("claims.json");
    }
  }

  @Test
  void removedMemberPersonalPlotSurvivesRestart() throws IOException {
    Path data = directory.resolve("claims.json");
    ClaimBlock plotAnchor = new ClaimBlock(WORLD, 131 * 16, 64, 2048);
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(data))) {
      manager.found(FOUNDER, "Town", ANCHOR, RANKS, OVERWORLD);
      manager.invite(FOUNDER, ATTACKER, 1000);
      manager.accept(ATTACKER, FOUNDER, 1001);
      for (int x = 130; x <= 132; x++) {
        for (int z = 127; z <= 129; z++) {
          manager.expand(FOUNDER, new ClaimChunk(WORLD, x, z), OVERWORLD);
        }
      }
      manager.createPlot(ATTACKER, plotAnchor, RANKS);
      manager.removeMember(FOUNDER, ATTACKER);
    }
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(data))) {
      assertThat(manager.snapshot().cityOf(ATTACKER)).isEmpty();
      assertThat(manager.snapshot().plots()).containsKey(ATTACKER);
      assertThat(manager.canEdit(ATTACKER, plotAnchor)).isTrue();
      assertThat(manager.canEdit(FOUNDER, plotAnchor)).isFalse();
      manager.destroyAnchor(ANCHOR);
    }
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(data))) {
      assertThat(manager.snapshot().cities()).isEmpty();
      assertThat(manager.snapshot().plots()).isEmpty();
    }
  }

  @Test
  void flushIncludesMostRecentCoalescedChange() throws IOException {
    Path data = directory.resolve("claims.json");
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(data))) {
      manager.found(FOUNDER, "Town", ANCHOR, RANKS, OVERWORLD);
      manager.setOnline(FOUNDER, true, 1000);
      for (int index = 0; index < 12; index++) {
        manager.attempt(new ClaimAttempt(ATTACKER, STONE, ClaimAction.BREAK, "stone", 1000, true));
      }
      manager.flush();
      try (ClaimRepository reader = new ClaimRepository(data)) {
        assertThat(reader.load().progress()).extracting(ClaimProgress::breaking).containsExactly(0.75);
      }
    }
  }

  @Test
  void invalidCurrentDataFailsWithoutDiscardingTheFile() throws IOException {
    Path data = directory.resolve("claims.json");
    Files.writeString(data, "{broken");
    try (ClaimRepository repository = new ClaimRepository(data)) {
      assertThatThrownBy(() -> new ClaimManager(new ClaimsConfig(), repository)).isInstanceOf(IOException.class);
    }
    assertThat(Files.readString(data)).isEqualTo("{broken");
  }

  @Test
  void duplicatedOwnershipIsRejectedBeforeServingClaims() throws IOException {
    Path data = directory.resolve("claims.json");
    ClaimCity city;
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(directory.resolve("valid.json")))) {
      city = manager.found(FOUNDER, "Town", ANCHOR, RANKS, OVERWORLD);
    }
    try (ClaimRepository repository = new ClaimRepository(data)) {
      repository.requestSave(() -> new ClaimState(List.of(city, city), List.of(), List.of()));
      repository.flush();
      assertThatThrownBy(() -> new ClaimManager(new ClaimsConfig(), repository)).isInstanceOf(IOException.class)
          .hasMessageContaining("ownership");
    }
  }

  @Test
  void failedSaveRetriesRetainedStateWithoutAnotherDomainMutation() throws Exception {
    Path parent = directory.resolve("blocked");
    Files.writeString(parent, "unavailable directory");
    Path data = parent.resolve("claims.json");
    AtomicInteger captures = new AtomicInteger();
    CountDownLatch retry = new CountDownLatch(1);
    try (ClaimRepository repository = new ClaimRepository(data)) {
      repository.requestSave(() -> {
        if (captures.incrementAndGet() > 1) {
          retry.countDown();
        }
        return ClaimState.empty();
      });
      assertThatThrownBy(repository::flush).isInstanceOf(IOException.class);
      Files.delete(parent);
      Files.createDirectory(parent);
      assertThat(retry.await(5, TimeUnit.SECONDS)).isTrue();
      repository.flush();
      assertThat(repository.load()).isEqualTo(ClaimState.empty());
      assertThat(Files.isRegularFile(data)).isTrue();
    }
  }

  @Test
  void failedSaveDoesNotOverwriteANewerPendingState() throws IOException {
    Path parent = directory.resolve("blocked");
    Files.writeString(parent, "unavailable directory");
    ClaimCity city;
    try (ClaimManager manager = new ClaimManager(new ClaimsConfig(), new ClaimRepository(directory.resolve("source.json")))) {
      city = manager.found(FOUNDER, "Latest", ANCHOR, RANKS, OVERWORLD);
    }
    try (ClaimRepository repository = new ClaimRepository(parent.resolve("claims.json"))) {
      repository.requestSave(ClaimState::empty);
      assertThatThrownBy(repository::flush).isInstanceOf(IOException.class);
      repository.requestSave(() -> new ClaimState(List.of(city), List.of(), List.of()));
      Files.delete(parent);
      Files.createDirectory(parent);
      repository.flush();
      assertThat(repository.load().cities()).extracting(ClaimCity::name).containsExactly("Latest");
    }
  }
}
