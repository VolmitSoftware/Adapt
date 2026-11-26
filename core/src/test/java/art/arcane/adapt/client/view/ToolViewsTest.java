package art.arcane.adapt.client.view;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.misc.ItemLooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolViewsTest {
  @TempDir
  Path dataFolder;

  @Test
  void everyToolSitsOnAnAdaptationPageWithItsLookAndARecipe() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, CoreContent.skillTypes())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      List<ToolView> tools = ToolViews.build(registry, UUID.randomUUID());
      assertThat(tools).hasSize(ItemLooks.LOOKS.stream().filter((ItemLooks.Look look) -> look.key().startsWith("tool-")).toList().size());
      Set<String> pages = new HashSet<>();
      for (Skill<?> skill : registry.getSkills()) {
        for (Adaptation<?> upgrade : skill.getUpgrades()) {
          pages.add(upgrade.getName());
        }
      }
      for (ToolView tool : tools) {
        assertThat(tool.name()).as(tool.toolId()).isNotBlank().doesNotContain("§");
        assertThat(tool.ingredients()).as(tool.toolId()).isNotEmpty();
        assertThat(tool.icon().customModelData()).isEqualTo(ItemLooks.number(tool.toolId()));
        assertThat(tool.icon().spriteId()).isEqualTo(IconSprites.texture("tool", tool.toolId()));
      }
      assertThat(tools).filteredOn((ToolView tool) -> tool.toolId().equals("tool-signature-crafting-enchanting-runed-book"))
          .extracting(ToolView::adaptationId).containsExactly("mutated-crafting-enchanting-convergence");
      assertThat(tools).filteredOn((ToolView tool) -> tool.toolId().startsWith("tool-swords-whetstone"))
          .extracting(ToolView::adaptationId).allMatch((String page) -> page.startsWith("upgraded-swords-"));
      assertThat(tools).extracting(ToolView::adaptationId)
          .allMatch((String page) -> pages.contains(page) || MutationAdaptation.parse(page) != null);
    }
  }
}
