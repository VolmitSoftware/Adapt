package art.arcane.adapt.api.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlRecoveryEnvelopeTest {
  @TempDir
  File directory;

  @Test
  void roundTripsFenceMetadataAndMultilineJson() throws Exception {
    File recovery = new File(directory, "player.json.pending-sql");
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        UUID.randomUUID(), UUID.randomUUID(), 7L, 42L, "{\n  \"value\": 3\n}");

    PlayerDataPersistenceQueue.writeSqlRecovery(recovery, snapshot);

    PlayerDataPersistenceQueue.SqlRecoverySnapshot restored =
        PlayerDataPersistenceQueue.readSqlRecovery(recovery);
    assertThat(restored.valid()).isTrue();
    assertThat(restored.snapshot()).isEqualTo(snapshot);
  }

  @Test
  void rejectsAndPreservesRawLegacyJson() throws Exception {
    File recovery = new File(directory, "player.json.pending-sql");
    Files.writeString(recovery.toPath(), "{\"legacy\":true}");

    PlayerDataPersistenceQueue.SqlRecoverySnapshot restored =
        PlayerDataPersistenceQueue.readSqlRecovery(recovery);

    assertThat(restored.valid()).isFalse();
    assertThat(recovery).hasContent("{\"legacy\":true}");
  }

  @Test
  void rejectsAndPreservesEnvelopeWithInvalidFenceMetadata() throws Exception {
    File recovery = new File(directory, "invalid.json.pending-sql");
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    String invalid = "ADAPT_SQL_RECOVERY_V1\n"
        + playerId + "\n"
        + ownerToken + "\n"
        + "-1\n"
        + "2\n"
        + "{}";
    Files.writeString(recovery.toPath(), invalid);

    PlayerDataPersistenceQueue.SqlRecoverySnapshot restored =
        PlayerDataPersistenceQueue.readSqlRecovery(recovery);

    assertThat(restored.valid()).isFalse();
    assertThat(recovery).hasContent(invalid);
  }

  @Test
  void adoptedRecoveryCleanupRequiresTheExactPredecessorFence() throws Exception {
    File recovery = new File(directory, "owned.json.pending-sql");
    UUID predecessor = UUID.randomUUID();
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        UUID.randomUUID(), predecessor, 9L, 17L, "{}");
    PlayerDataPersistenceQueue.writeSqlRecovery(recovery, snapshot);

    PlayerDataPersistenceQueue.deleteAdoptedRecovery(
        recovery, UUID.randomUUID(), snapshot.epoch());
    assertThat(recovery).exists();

    PlayerDataPersistenceQueue.deleteAdoptedRecovery(
        recovery, predecessor, snapshot.epoch());
    assertThat(recovery).doesNotExist();
  }

  @Test
  void sqlAdoptionRejectsAndPreservesEveryLegacyDeleteJournal() throws Exception {
    File playerFile = new File(directory, "player.json");
    File journal = PlayerDataPersistenceQueue.deleteMarkerFile(playerFile);
    Files.writeString(journal.toPath(), "legacy-delete-state");

    assertThatThrownBy(() -> AdaptPlayer.rejectIncompatibleSqlDeleteJournal(playerFile))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Incompatible pre-fence deletion journal");
    assertThat(journal).hasContent("legacy-delete-state");

    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/world/AdaptPlayer.java"));
    int adoption = source.indexOf("private static LoadedPlayerData adoptClaimedPlayerData");
    int rejection = source.indexOf("rejectIncompatibleSqlDeleteJournal(localFile)", adoption);
    int predecessor = source.indexOf("claim.effectivePredecessor()", adoption);
    assertThat(rejection).isGreaterThan(adoption).isLessThan(predecessor);
  }
}
