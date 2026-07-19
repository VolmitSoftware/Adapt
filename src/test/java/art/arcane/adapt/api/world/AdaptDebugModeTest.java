package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public class AdaptDebugModeTest extends AdaptTestBase {
  private final UUID uuid = UUID.randomUUID();

  @AfterEach
  void deactivate() {
    AdaptDebugMode.setActive(uuid, false);
    PlayerStateRegistry.reset();
  }

  @Test
  public void isActive_defaultsToFalse_forUnknownUuid() {
    assertThat(AdaptDebugMode.isActive(UUID.randomUUID())).isFalse();
  }

  @Test
  public void isActive_nullUuid_returnsFalse() {
    assertThat(AdaptDebugMode.isActive((UUID) null)).isFalse();
  }

  @Test
  public void isActive_nullPlayer_returnsFalse() {
    assertThat(AdaptDebugMode.isActive((Player) null)).isFalse();
  }

  @Test
  public void setActive_enablesAndDisables() {
    AdaptDebugMode.setActive(uuid, true);
    assertThat(AdaptDebugMode.isActive(uuid)).isTrue();

    AdaptDebugMode.setActive(uuid, false);
    assertThat(AdaptDebugMode.isActive(uuid)).isFalse();
  }

  @Test
  public void toggle_flipsStateAndReturnsNewState() {
    assertThat(AdaptDebugMode.toggle(uuid)).isTrue();
    assertThat(AdaptDebugMode.isActive(uuid)).isTrue();

    assertThat(AdaptDebugMode.toggle(uuid)).isFalse();
    assertThat(AdaptDebugMode.isActive(uuid)).isFalse();
  }

  @Test
  public void isActive_player_resolvesThroughUniqueId() {
    Player player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(uuid);

    AdaptDebugMode.setActive(uuid, true);
    assertThat(AdaptDebugMode.isActive(player)).isTrue();
  }
}
