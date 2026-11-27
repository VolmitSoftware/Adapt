package art.arcane.adapt.localization;

import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptLanguageEditorPresentationTest {
  @TempDir
  Path data;

  @Test
  void skillAndAdaptationMessagesUseTheirOwnersIcons() {
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      AdaptLanguageEditor editor = new AdaptLanguageEditor(() -> registry);

      Optional<ItemRef> skill = editor.icon("adapt.axe");
      Optional<ItemRef> adaptation = editor.icon("adapt.axe.wood_miner.name");
      assertThat(skill).isPresent();
      assertThat(editor.icon("adapt.skill.axes.description")).isPresent();
      assertThat(adaptation).isPresent();
      assertThat(editor.icon("adapt.axe.wood_miner.lore.details")).isPresent();
      assertThat(editor.icon("adapt.axe.other")).isPresent();
      assertThat(editor.icon("adapt.command.help")).isEmpty();
    }
  }

  @Test
  void unavailableRegistryUsesTheSharedDefaultIcons() {
    assertThat(new AdaptLanguageEditor(() -> null).icon("adapt.skill.axes.name")).isEmpty();
  }
}
