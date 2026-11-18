package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RiftEnderTaglockAdmissionTest {
  @Test
  void enforcesOneOperationPerOwnerAndTheGlobalCapacity() {
    RiftEnderTaglock.TeleportAdmission admission = new RiftEnderTaglock.TeleportAdmission(2);
    UUID firstOwner = new UUID(1L, 1L);
    UUID secondOwner = new UUID(1L, 2L);
    UUID thirdOwner = new UUID(1L, 3L);

    long firstToken = admission.admit(firstOwner, 100L, 1_000L);

    assertThat(firstToken).isPositive();
    assertThat(admission.admit(firstOwner, 101L, 1_000L)).isEqualTo(-1L);
    assertThat(admission.admit(secondOwner, 101L, 1_000L)).isPositive();
    assertThat(admission.admit(thirdOwner, 101L, 1_000L)).isEqualTo(-1L);
    assertThat(admission.size()).isEqualTo(2);
  }

  @Test
  void expiredOperationsReleaseCapacityWithoutRevivingStaleTokens() {
    RiftEnderTaglock.TeleportAdmission admission = new RiftEnderTaglock.TeleportAdmission(2);
    UUID firstOwner = new UUID(2L, 1L);
    UUID secondOwner = new UUID(2L, 2L);
    UUID thirdOwner = new UUID(2L, 3L);
    long firstToken = admission.admit(firstOwner, 0L, 10L);
    admission.admit(secondOwner, 5L, 100L);

    long thirdToken = admission.admit(thirdOwner, 10L, 100L);

    assertThat(thirdToken).isPositive();
    assertThat(admission.isCurrent(firstOwner, firstToken, 10L)).isFalse();
    assertThat(admission.size()).isEqualTo(2);
  }

  @Test
  void aNewGenerationRejectsCompletionFromAnExpiredOperation() {
    RiftEnderTaglock.TeleportAdmission admission = new RiftEnderTaglock.TeleportAdmission(1);
    UUID ownerId = new UUID(3L, 1L);
    long expiredToken = admission.admit(ownerId, 0L, 10L);
    long currentToken = admission.admit(ownerId, 10L, 100L);

    assertThat(currentToken).isGreaterThan(expiredToken);
    assertThat(admission.complete(ownerId, expiredToken)).isFalse();
    assertThat(admission.isCurrent(ownerId, currentToken, 10L)).isTrue();
  }

  @Test
  void teleportPhaseCanBeginOnlyOnceAndExtendsItsDeadline() {
    RiftEnderTaglock.TeleportAdmission admission = new RiftEnderTaglock.TeleportAdmission(1);
    UUID ownerId = new UUID(4L, 1L);
    long token = admission.admit(ownerId, 0L, 10L);

    assertThat(admission.markTeleporting(ownerId, token, 5L, 100L)).isTrue();
    assertThat(admission.markTeleporting(ownerId, token, 6L, 100L)).isFalse();
    assertThat(admission.isCurrent(ownerId, token, 50L)).isTrue();
    assertThat(admission.complete(ownerId, token)).isTrue();
    assertThat(admission.size()).isZero();
  }

  @Test
  void ownerCancellationKeepsAnAlreadyStartedTeleportTracked() {
    RiftEnderTaglock.TeleportAdmission admission = new RiftEnderTaglock.TeleportAdmission(1);
    UUID ownerId = new UUID(5L, 1L);
    long pendingToken = admission.admit(ownerId, 0L, 100L);

    admission.cancel(ownerId);

    assertThat(admission.isCurrent(ownerId, pendingToken, 1L)).isFalse();
    long teleportingToken = admission.admit(ownerId, 1L, 100L);
    assertThat(admission.markTeleporting(ownerId, teleportingToken, 2L, 100L)).isTrue();

    admission.cancel(ownerId);

    assertThat(admission.isCurrent(ownerId, teleportingToken, 3L)).isTrue();
  }
}
