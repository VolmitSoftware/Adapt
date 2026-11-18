package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGuiSupportTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Test
  void actionBarsResolveInsideTheViewersLanguageScope() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(VIEWER);
    AtomicReference<UUID> audience = new AtomicReference<>();
    AtomicReference<String> shown = new AtomicReference<>();
    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class); MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      hud.when(() -> AdaptHud.actionBar(eq(player), anyString())).thenAnswer(invocation -> {
        audience.set(LanguageAudience.current());
        shown.set(invocation.getArgument(1));
        return null;
      });

      GolemGuiSupport.notify(player, GolemancyMessages.GUI_CHANNEL, trusted("channel", GolemGuiSupport.localized(GolemancyMessages.CHANNEL_RED)));
    }

    assertThat(audience.get()).isEqualTo(VIEWER);
    assertThat(shown.get()).contains("Channel: Red");
  }

  @Test
  void aLocalizedArgumentResolvesWhenItsMessageIsFormatted() {
    assertThat(String.valueOf(GolemGuiSupport.localized(GolemancyMessages.CHANNEL_LIME))).isEqualTo(AdaptLanguage.text(GolemancyMessages.CHANNEL_LIME));
  }
}
