package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.command.BukkitCommandSource;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitLanguageSwitcherPortTest extends AdaptTestBase {
  private static final String[] EDIT = {"server", "edit"};

  @Test
  void aPanelClientEditsInTheMenuEditorWhileEveryoneElseKeepsTheChestEditor() {
    BukkitLanguageSwitcher chest = mock(BukkitLanguageSwitcher.class);
    ClientUiService clients = mock(ClientUiService.class);
    Player panelPlayer = player();
    Player vanillaPlayer = player();
    CommandSender console = mock(CommandSender.class);
    when(clients.has(panelPlayer.getUniqueId(), ClientProtocol.PANEL)).thenReturn(true);
    BukkitLanguageSwitcherPort port = new BukkitLanguageSwitcherPort(plugin);
    try (MockedStatic<BukkitLanguageSwitcher> registration = mockStatic(BukkitLanguageSwitcher.class);
         MockedStatic<ClientUiService> sessions = mockStatic(ClientUiService.class);
         MockedStatic<Tasks> tasks = mockStatic(Tasks.class)) {
      registration.when(() -> BukkitLanguageSwitcher.register(any(), any(), any(), any())).thenReturn(chest);
      sessions.when(ClientUiService::current).thenReturn(clients);
      port.open(mock(PluginLanguageService.class));

      port.command(new BukkitCommandSource(vanillaPlayer), EDIT);
      port.command(new BukkitCommandSource(panelPlayer), EDIT);
      port.command(new BukkitCommandSource(panelPlayer), new String[] {"server", "de_DE"});
      port.command(new BukkitCommandSource(console), EDIT);

      verify(chest).command(vanillaPlayer, EDIT);
      verify(chest, never()).command(eq(panelPlayer), eq(EDIT));
      verify(chest).command(eq(panelPlayer), eq(new String[] {"server", "de_DE"}));
      verify(chest).command(console, EDIT);
      tasks.verify(() -> Tasks.runEntity(any(), any(Runnable.class)));
    } finally {
      port.close();
    }
  }

  private static Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getName()).thenReturn("Viewer");
    when(player.hasPermission(anyString())).thenReturn(true);
    return player;
  }
}
