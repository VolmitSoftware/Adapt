package art.arcane.adapt.localization;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageEditorInputRegistrationTest {
  @TempDir
  Path data;

  @AfterEach
  void uninstall() {
    AdaptPorts.uninstall(LanguageSwitcherPort.class);
  }

  @Test
  void editorChatListensOnlyWhereTheCoreEditorIsUsed() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data.resolve("plugin")).build(), List.of())) {
      assertThat(adapt.bus().hasListeners(PlayerChatEvent.class, EventPriority.LOWEST)).isFalse();
    }

    AdaptPorts.install(LanguageSwitcherPort.class, new AdaptLanguageSwitcher());
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data.resolve("core-editor")).build(), List.of())) {
      assertThat(adapt.bus().hasListeners(PlayerChatEvent.class, EventPriority.LOWEST)).isTrue();
    }
  }
}
