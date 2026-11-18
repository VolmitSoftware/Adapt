package art.arcane.adapt.api.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FencedSnapshotSelectorTest {
  @Test
  void selectsHighestMatchingSequenceAboveCommittedSql() {
    UUID playerId = UUID.randomUUID();
    UUID predecessor = UUID.randomUUID();
    FencedPlayerSnapshot wrongOwner = snapshot(playerId, UUID.randomUUID(), 4L, 100L, "wrong");
    FencedPlayerSnapshot older = snapshot(playerId, predecessor, 4L, 7L, "older");
    FencedPlayerSnapshot newest = snapshot(playerId, predecessor, 4L, 9L, "newest");

    FencedSnapshotSelector.Selection selected = FencedSnapshotSelector.select(
        playerId,
        predecessor, 4L, 8L, "sql", List.of(wrongOwner, older, newest));

    assertThat(selected.json()).isEqualTo("newest");
    assertThat(selected.source()).isSameAs(newest);
  }

  @Test
  void committedSqlWinsWhenCandidatesAreNotNewer() {
    UUID playerId = UUID.randomUUID();
    UUID predecessor = UUID.randomUUID();

    FencedSnapshotSelector.Selection selected = FencedSnapshotSelector.select(
        playerId,
        predecessor,
        2L,
        5L,
        "sql",
        List.of(snapshot(playerId, predecessor, 2L, 4L, "older"),
            snapshot(playerId, predecessor, 2L, 5L, "sql"))
    );

    assertThat(selected.json()).isEqualTo("sql");
    assertThat(selected.source()).isNull();
  }

  @Test
  void ignoresFencedCandidatesWhenThereIsNoPredecessor() {
    FencedSnapshotSelector.Selection selected = FencedSnapshotSelector.select(
        UUID.randomUUID(),
        null,
        0L,
        0L,
        null,
        List.of(snapshot(UUID.randomUUID(), UUID.randomUUID(), 1L, 1L, "stale"))
    );

    assertThat(selected.json()).isNull();
    assertThat(selected.source()).isNull();
  }

  @Test
  void rejectsDifferentPayloadsAtTheSamePendingSequence() {
    UUID playerId = UUID.randomUUID();
    UUID predecessor = UUID.randomUUID();

    assertThatThrownBy(() -> FencedSnapshotSelector.select(
        playerId,
        predecessor,
        3L,
        0L,
        null,
        List.of(snapshot(playerId, predecessor, 3L, 8L, "first"),
            snapshot(playerId, predecessor, 3L, 8L, "second"))
    )).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("pending sequence 8");
  }

  @Test
  void rejectsDifferentPayloadAtCommittedSequence() {
    UUID playerId = UUID.randomUUID();
    UUID predecessor = UUID.randomUUID();

    assertThatThrownBy(() -> FencedSnapshotSelector.select(
        playerId,
        predecessor,
        3L,
        8L,
        "sql",
        List.of(snapshot(playerId, predecessor, 3L, 8L, "different"))
    )).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("committed sequence 8");
  }

  @Test
  void ignoresMatchingFenceFromAnotherPlayer() {
    UUID playerId = UUID.randomUUID();
    UUID predecessor = UUID.randomUUID();

    FencedSnapshotSelector.Selection selected = FencedSnapshotSelector.select(
        playerId,
        predecessor,
        3L,
        8L,
        "sql",
        List.of(snapshot(UUID.randomUUID(), predecessor, 3L, 9L, "other-player"))
    );

    assertThat(selected.json()).isEqualTo("sql");
    assertThat(selected.source()).isNull();
  }

  private static FencedPlayerSnapshot snapshot(UUID playerId, UUID token, long epoch,
                                                long sequence, String json) {
    return new FencedPlayerSnapshot(playerId, token, epoch, sequence, json);
  }
}
