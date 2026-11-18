package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AbilityApiRuntimeThreadGuardTest {
  @Test
  void offTheServerTickThreadNoProviderIsConsulted() {
    Player player = mock(Player.class);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

      assertThat(AbilityApiRuntime.ownsPlayerRegion(player)).isFalse();
      assertThat(AbilityApiRuntime.ownsPlayerRegion(null)).isFalse();
    }
  }

  @Test
  void onPaperTheServerTickThreadIsTheOwningThread() {
    Player player = mock(Player.class);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<J> scheduling = mockStatic(J.class)) {
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      scheduling.when(J::isFoliaThreading).thenReturn(false);

      assertThat(AbilityApiRuntime.ownsPlayerRegion(player)).isTrue();
    }
  }

  @Test
  void onFoliaARegionThreadThatDoesNotOwnThePlayerIsNotTheOwningThread() {
    Player player = mock(Player.class);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<J> scheduling = mockStatic(J.class)) {
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(false);

      assertThat(AbilityApiRuntime.ownsPlayerRegion(player)).isFalse();

      scheduling.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);

      assertThat(AbilityApiRuntime.ownsPlayerRegion(player)).isTrue();
    }
  }
}
