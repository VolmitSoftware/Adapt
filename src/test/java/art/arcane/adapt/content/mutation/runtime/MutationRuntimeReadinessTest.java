package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.world.AdaptPlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MutationRuntimeReadinessTest {
  @Test
  void mutationRuntimeRequiresTheCurrentReadyProfile() {
    Player player = mock(Player.class);
    Player otherPlayer = mock(Player.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);

    assertThat(MutationRuntimeAccess.isReadyRuntimePlayer(player, adaptPlayer)).isTrue();
    assertThat(MutationRuntimeAccess.isReadyRuntimePlayer(otherPlayer, adaptPlayer)).isFalse();
    assertThat(MutationRuntimeAccess.isReadyRuntimePlayer(player, null)).isFalse();
    assertThat(MutationRuntimeAccess.isReadyRuntimePlayer(null, adaptPlayer)).isFalse();

    when(adaptPlayer.isRuntimeReady()).thenReturn(false);
    assertThat(MutationRuntimeAccess.isReadyRuntimePlayer(player, adaptPlayer)).isFalse();
  }
}
