package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AxeRecoveryJournalTest extends AdaptTestBase {
  @Test
  void entriesRoundTripIdempotentlyAndRejectConflictingPayloads() throws Exception {
    AxeRecoveryJournal journal = new AxeRecoveryJournal(dataFolder.toPath());
    UUID ownerId = UUID.randomUUID();
    NamespacedKey recoveryKey = new NamespacedKey("adapt", "throwing_axe_recovery_1234");
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};

    journal.persist(ownerId, recoveryKey, encoded);
    journal.persist(ownerId, recoveryKey, encoded);

    assertThat(journal.keys(ownerId)).containsExactly(recoveryKey.getKey());
    assertThat(journal.read(ownerId, recoveryKey.getKey())).containsExactly(encoded);
    assertThatThrownBy(() -> journal.persist(
        ownerId,
        recoveryKey,
        new byte[]{1, 2, 3}
    )).isInstanceOf(IOException.class)
        .hasMessageContaining("Conflicting");

    journal.delete(ownerId, recoveryKey.getKey());
    assertThat(journal.keys(ownerId)).isEmpty();
  }

  @Test
  void corruptEntriesRemainPresentAndCannotBeImported() throws Exception {
    AxeRecoveryJournal journal = new AxeRecoveryJournal(dataFolder.toPath());
    UUID ownerId = UUID.randomUUID();
    NamespacedKey recoveryKey = new NamespacedKey("adapt", "throwing_axe_recovery_5678");
    journal.persist(ownerId, recoveryKey, new byte[]{1, 2, 3, 4});
    Path entry = dataFolder.toPath()
        .resolve(ownerId.toString())
        .resolve(recoveryKey.getKey() + ".axe");
    Files.write(entry, new byte[]{9, 9, 9});

    assertThatThrownBy(() -> journal.read(ownerId, recoveryKey.getKey()))
        .isInstanceOf(IOException.class);
    assertThat(Files.exists(entry)).isTrue();
  }
}
