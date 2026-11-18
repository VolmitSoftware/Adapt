package art.arcane.adapt.util.config;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigHotloadGuiStalenessTest {
  @Test
  void entryIsStaleWhenPlayerIsMissing() {
    assertThat(ConfigHotload.isStaleGuiEntry(null, visibleWindow(UUID.randomUUID()))).isTrue();
  }

  @Test
  void entryIsStaleWhenWindowIsMissing() {
    assertThat(ConfigHotload.isStaleGuiEntry(onlinePlayer(UUID.randomUUID()), null)).isTrue();
  }

  @Test
  void entryIsStaleWhenPlayerIsOffline() {
    UUID id = UUID.randomUUID();
    PlayerRef player = onlinePlayer(id);
    when(player.isOnline()).thenReturn(false);

    assertThat(ConfigHotload.isStaleGuiEntry(player, visibleWindow(id))).isTrue();
  }

  @Test
  void entryIsStaleWhenTheWindowIsNoLongerVisible() {
    UUID id = UUID.randomUUID();
    GuiWindow window = visibleWindow(id);
    when(window.isVisible()).thenReturn(false);

    assertThat(ConfigHotload.isStaleGuiEntry(onlinePlayer(id), window)).isTrue();
  }

  @Test
  void entryIsStaleWhenTheWindowBelongsToAnotherViewer() {
    GuiWindow window = visibleWindow(UUID.randomUUID());

    assertThat(ConfigHotload.isStaleGuiEntry(onlinePlayer(UUID.randomUUID()), window)).isTrue();
  }

  @Test
  void entryIsStaleWhenTheWindowHasNoViewer() {
    UUID id = UUID.randomUUID();
    GuiWindow window = mock(GuiWindow.class);
    when(window.isVisible()).thenReturn(true);
    when(window.getViewer()).thenReturn(null);

    assertThat(ConfigHotload.isStaleGuiEntry(onlinePlayer(id), window)).isTrue();
  }

  @Test
  void entryIsFreshWhenTheOnlineViewerStillHasTheWindowOpen() {
    UUID id = UUID.randomUUID();

    assertThat(ConfigHotload.isStaleGuiEntry(onlinePlayer(id), visibleWindow(id))).isFalse();
  }

  private PlayerRef onlinePlayer(UUID id) {
    PlayerRef player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    return player;
  }

  private GuiWindow visibleWindow(UUID viewerId) {
    PlayerRef viewer = onlinePlayer(viewerId);
    GuiWindow window = mock(GuiWindow.class);
    lenient().when(window.isVisible()).thenReturn(true);
    lenient().when(window.getViewer()).thenReturn(viewer);
    return window;
  }
}
