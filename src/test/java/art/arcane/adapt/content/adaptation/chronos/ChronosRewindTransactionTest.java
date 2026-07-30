package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.Adaptation;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChronosRewindTransactionTest {
  @Test
  void scheduledAuthorizationRequiresBothEndsAndUsesTheLowerCurrentLevel() {
    Player player = mock(Player.class);
    Location source = mock(Location.class);
    Location destination = mock(Location.class);
    Adaptation.BlockActionContext sourceContext =
        new Adaptation.BlockActionContext(player, source, 4);
    Adaptation.BlockActionContext destinationContext =
        new Adaptation.BlockActionContext(player, destination, 2);

    assertThat(ChronosRewind.scheduledRewindLevel(sourceContext, destinationContext))
        .isEqualTo(2);
    assertThat(ChronosRewind.scheduledRewindLevel(null, destinationContext)).isZero();
    assertThat(ChronosRewind.scheduledRewindLevel(sourceContext, null)).isZero();
  }

  @Test
  void rewindCommitsOnlyAnOwnedSuccessfulTeleport() {
    assertThat(ChronosRewind.shouldCommitRewind(true, null, true)).isTrue();
    assertThat(ChronosRewind.shouldCommitRewind(false, null, true)).isFalse();
    assertThat(ChronosRewind.shouldCommitRewind(null, null, true)).isFalse();
    assertThat(ChronosRewind.shouldCommitRewind(
        true,
        new IllegalStateException("teleport failed"),
        true
    )).isFalse();
    assertThat(ChronosRewind.shouldCommitRewind(true, null, false)).isFalse();
  }
}
