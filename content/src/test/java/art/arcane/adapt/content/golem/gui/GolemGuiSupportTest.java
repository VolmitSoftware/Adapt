package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.LanguageAudience;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGuiSupportTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Test
  void actionBarsResolveInsideTheViewersLanguageScope() {
    AtomicReference<UUID> audience = new AtomicReference<>();
    String shown;
    try (MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class, CALLS_REAL_METHODS)) {
      language.when(() -> AdaptLanguage.text(any(MessageKey.class), any(MessageArgs.class))).thenAnswer(invocation -> {
        audience.set(LanguageAudience.current());
        return invocation.callRealMethod();
      });

      shown = showChannel(Text.key("adapt.golemancy.gui.channel").with("channel", Text.key("adapt.golemancy.channel.red")));
    }

    assertThat(audience.get()).isEqualTo(VIEWER);
    assertThat(shown).contains("Channel: Red");
  }

  @Test
  void aLocalizedArgumentResolvesWhenItsMessageIsFormatted() {
    String shown = showChannel(Text.key("adapt.golemancy.gui.channel").with("channel", Text.key("adapt.golemancy.channel.lime")));

    assertThat(shown).contains("Channel: " + Text.key("adapt.golemancy.channel.lime").resolve());
  }

  private static String showChannel(Text message) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(VIEWER);
    AtomicReference<String> shown = new AtomicReference<>();
    AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
    try (MockedStatic<AdaptApi> scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS); MockedStatic<HudStatus> hud = mockStatic(HudStatus.class)) {
      scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
      when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      hud.when(() -> HudStatus.show(eq(player), anyString())).thenAnswer(invocation -> {
        shown.set(invocation.getArgument(1));
        return null;
      });

      GolemGuiSupport.notify(player, message);
    }
    return shown.get();
  }
}
