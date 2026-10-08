package art.arcane.adapt.util.common.plugin;

import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class VolmitSenderColorTest {
  @ParameterizedTest
  @EnumSource(Surface.class)
  void localizedResetProtectsExplicitColorsFromSenderGradients(Surface surface) {
    ComponentText rendered = render(surface, "§e§r§cRed text");

    assertThat(rendered.legacy()).isEqualTo("§cRed text");
  }

  @ParameterizedTest
  @EnumSource(Surface.class)
  void defaultJavaColorsStillReceiveAutomaticGradients(Surface surface) {
    ComponentText rendered = render(surface, "§eDefault text");

    assertThat(rendered.plain()).isEqualTo("Default text");
    assertThat(rendered.legacy()).contains("§x");
  }

  @ParameterizedTest
  @EnumSource(Surface.class)
  void renderedRgbColorsAreNotPassedThroughAuraAgain(Surface surface) {
    String message = "§x§1§2§3§a§b§cRGB text";

    assertThat(render(surface, message).legacy()).isEqualTo(message);
  }

  private static ComponentText render(Surface surface, String message) {
    Player player = mock(Player.class);
    VolmitSender sender = new VolmitSender(player, "§r");
    ArgumentCaptor<ComponentText> captured = ArgumentCaptor.forClass(ComponentText.class);
    try (MockedStatic<ComponentMessenger> messenger = mockStatic(ComponentMessenger.class)) {
      switch (surface) {
        case CHAT -> {
          sender.sendMessage(message);
          messenger.verify(() -> ComponentMessenger.send(eq(player), captured.capture()));
        }
        case ACTION -> {
          sender.sendAction(message);
          messenger.verify(() -> ComponentMessenger.sendActionBar(eq(player), captured.capture()));
        }
        case TITLE -> {
          sender.sendTitle(message, "", 0, 500, 250);
          messenger.verify(() -> ComponentMessenger.showTitle(eq(player), captured.capture(), any(), any(), any(), any()));
        }
      }
    }
    return captured.getValue();
  }

  private enum Surface {
    CHAT,
    ACTION,
    TITLE
  }
}
