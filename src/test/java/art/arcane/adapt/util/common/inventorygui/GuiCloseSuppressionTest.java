package art.arcane.adapt.util.common.inventorygui;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuiCloseSuppressionTest {
  @Test
  void consumeReturnsFalseWhenNothingWasSuppressed() {
    UUID player = UUID.randomUUID();

    assertThat(GuiCloseSuppression.consume(player, 0L)).isFalse();
  }

  @Test
  void consumeReturnsTrueOnceAfterSuppress() {
    UUID player = UUID.randomUUID();
    GuiCloseSuppression.suppress(player, 1_000L);

    assertThat(GuiCloseSuppression.consume(player, 1_000L)).isTrue();
    assertThat(GuiCloseSuppression.consume(player, 1_000L)).isFalse();
  }

  @Test
  void consumeReturnsTrueAtTheExactTtlBoundary() {
    UUID player = UUID.randomUUID();
    GuiCloseSuppression.suppress(player, 1_000L);

    assertThat(GuiCloseSuppression.consume(player, 1_000L + GuiCloseSuppression.SUPPRESS_MS)).isTrue();
  }

  @Test
  void consumeReturnsFalseAndClearsTheEntryAfterTtlExpiry() {
    UUID player = UUID.randomUUID();
    GuiCloseSuppression.suppress(player, 1_000L);

    assertThat(GuiCloseSuppression.consume(player, 1_000L + GuiCloseSuppression.SUPPRESS_MS + 1L)).isFalse();
    assertThat(GuiCloseSuppression.consume(player, 1_000L)).isFalse();
  }

  @Test
  void suppressionIsIsolatedPerPlayer() {
    UUID suppressed = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    GuiCloseSuppression.suppress(suppressed, 1_000L);

    assertThat(GuiCloseSuppression.consume(other, 1_000L)).isFalse();
    assertThat(GuiCloseSuppression.consume(suppressed, 1_000L)).isTrue();
  }

  @Test
  void reSuppressExtendsTheExistingWindow() {
    UUID player = UUID.randomUUID();
    GuiCloseSuppression.suppress(player, 1_000L);
    GuiCloseSuppression.suppress(player, 2_000L);

    assertThat(GuiCloseSuppression.consume(player, 2_000L + GuiCloseSuppression.SUPPRESS_MS)).isTrue();
  }

  @Test
  void nullInputsAreIgnored() {
    GuiCloseSuppression.suppress((Player) null);
    GuiCloseSuppression.suppress((UUID) null, 0L);

    assertThat(GuiCloseSuppression.consume((Player) null)).isFalse();
    assertThat(GuiCloseSuppression.consume((UUID) null, 0L)).isFalse();
  }

  @Test
  void playerOverloadsShareTheSameState() {
    UUID playerId = UUID.randomUUID();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);

    GuiCloseSuppression.suppress(player);

    assertThat(GuiCloseSuppression.consume(player)).isTrue();
    assertThat(GuiCloseSuppression.consume(playerId, System.currentTimeMillis())).isFalse();
  }
}
