package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Paper 1.20.1 has no server-side experience total, so it is derived from the level and the progress bar. */
class BukkitComponentPortTest {
  private final BukkitComponentPort port = new BukkitComponentPort();

  @Test
  void recalculateDerivesTheTotalFromLevelAndProgress() {
    Player player = player();
    when(player.getLevel()).thenReturn(10);
    when(player.getExp()).thenReturn(0.5F);
    PlayerRef ref = BukkitRefs.player(player);

    port.setTotalExperience(ref, port.totalExperiencePoints(ref));

    verify(player).setTotalExperience(174);
  }

  @Test
  void earnedExperienceHonorsTheExperienceEventAmount() {
    Player player = player();
    Server server = mock(Server.class);
    PluginManager events = mock(PluginManager.class);
    when(player.getServer()).thenReturn(server);
    when(server.getPluginManager()).thenReturn(events);
    doAnswer(invocation -> {
      PlayerExpChangeEvent event = invocation.getArgument(0);
      assertThat(event.getAmount()).isEqualTo(12);
      assertThat(event.getPlayer()).isSameAs(player);
      event.setAmount(6);
      return null;
    }).when(events).callEvent(any(PlayerExpChangeEvent.class));

    port.awardExperience(BukkitRefs.player(player), 12);

    verify(events).callEvent(any(PlayerExpChangeEvent.class));
    verify(player).giveExp(6);
  }

  private static Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    return player;
  }
}
