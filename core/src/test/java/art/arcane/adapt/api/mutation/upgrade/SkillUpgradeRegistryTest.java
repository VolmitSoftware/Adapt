package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.blocks.MutationPrice;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every upgrade any skill registers keeps the shared conventions; runs for each skill lane without edits. */
class SkillUpgradeRegistryTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void theSpecsCoverAll72UpgradesWithDistinctIdsAndClasses() {
    List<UpgradeSpec> specs = SkillUpgrades.all();
    Set<String> classes = new HashSet<>();

    assertThat(specs).hasSize(72);
    for (UpgradeSpec spec : specs) {
      assertThat(spec.id()).matches("upgraded-" + spec.skillId() + "-[a-z0-9]+(-[a-z0-9]+)*");
      assertThat(classes.add(spec.className())).as(spec.className()).isTrue();
    }
    assertThat(SkillUpgrades.require("upgraded-swords-duelists-verdict").className())
        .isEqualTo("art.arcane.adapt.content.adaptation.sword.upgraded.DuelistsVerdict");
    assertThat(MutationPrice.UPGRADED.costs()).containsExactly(13, 13, 16);
    assertThat(MutationPrice.TOOL.costs()).containsExactly(15, 14, 18);
    assertThat(MutationPrice.TOOL.totalCost()).isEqualTo(47);
    assertThat(specs.stream().filter((UpgradeSpec spec) -> spec.upgrade().tool()).map(UpgradeSpec::id))
        .containsExactlyInAnyOrder("upgraded-architect-master-builder", "upgraded-architect-skyhook",
            "upgraded-architect-wrecking-hand", "upgraded-rift-pocket-realm", "upgraded-pickaxe-earthshaker",
            "upgraded-excavation-landshaper", "upgraded-crafting-field-workshop", "upgraded-herbalism-harvest-moon",
            "upgraded-seaborne-leviathan", "upgraded-taming-one-mind", "upgraded-agility-skyrunner",
            "upgraded-discovery-omniscience", "upgraded-golemancy-forgemaster", "upgraded-ranged-stormshot",
            "upgraded-hunter-apex-predator", "upgraded-swords-tempest-flow", "upgraded-brewing-master-distiller");
  }

  @Test
  void everyRegisteredUpgradeFollowsItsSpec() {
    int checked = 0;
    for (Skill<?> skill : registry.getAllSkills()) {
      List<Adaptation<?>> upgrades = skill.getUpgrades();
      assertThat(upgrades).as("upgrades of %s", skill.getName()).hasSizeLessThanOrEqualTo(3);
      for (Adaptation<?> upgrade : upgrades) {
        assertThat(upgrade).isInstanceOf(SkillUpgrade.class);
        UpgradeSpec spec = ((SkillUpgrade<?>) upgrade).getUpgradeSpec();
        assertThat(spec.skillId()).isEqualTo(skill.getName());
        assertThat(upgrade.getClass().getName()).isEqualTo(spec.className());
        assertThat(upgrade.getSkill()).isSameAs(skill);
        assertThat(skill.getAdaptations()).doesNotContain(upgrade);
        assertThat(upgrade.getMaxLevel()).isEqualTo(3);
        assertThat(List.of(upgrade.getCostFor(1), upgrade.getCostFor(2), upgrade.getCostFor(3)))
            .as("price of %s", spec.id()).isEqualTo(spec.upgrade().price().costs());
        assertThat(((SkillUpgrade<?>) upgrade).getConfig() instanceof SkillUpgrade.ToolConfig)
            .as("%s config tier", spec.id()).isEqualTo(spec.upgrade().tool());
        assertThat(C.stripColor(upgrade.getDisplayName())).isEqualTo(spec.upgrade().name());
        assertThat(upgrade.getDescription()).isEqualTo(spec.upgrade().description());
        assertThat(ConfigFieldMessages.has("adaptations." + spec.id() + ".enabled")).as(spec.id()).isTrue();
        for (int level = 1; level <= 3; level++) {
          StatLines stats = new StatLines();
          upgrade.addStats(level, stats);
          assertThat(stats.lines()).as("stat lines of %s at level %s", spec.id(), level).isNotEmpty();
        }
        checked++;
      }
    }
    assertThat(checked).isGreaterThanOrEqualTo(3);
  }

  @Test
  void everyUpgradeFusesOneOriginalPerCatalogSource() {
    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> upgrade : skill.getUpgrades()) {
        SkillUpgrade<?> fusing = (SkillUpgrade<?>) upgrade;
        List<String> fused = skill.getAdaptations().stream().filter(fusing::fuses)
            .map((Adaptation<?> original) -> C.stripColor(original.getDisplayName())).toList();
        assertThat(fused).as("originals %s fuses", upgrade.getName())
            .containsExactlyInAnyOrderElementsOf(fusing.getSourceNames());
        assertThat(skill.getUpgrades()).noneMatch(fusing::fuses);
      }
    }
  }

  @Test
  void everyAdaptationAndUpgradeIsFoundByItsId() {
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        assertThat(registry.getAdaptation(adaptation.getName())).isSameAs(adaptation);
      }
      for (Adaptation<?> upgrade : skill.getUpgrades()) {
        assertThat(registry.getAdaptation(upgrade.getName())).isSameAs(upgrade);
      }
    }
    assertThat(registry.getAdaptation("no-such-adaptation")).isNull();
  }

  @Test
  void everyUpgradeLevelReadsDifferentlyFromTheLevelBelow() {
    List<String> flat = new ArrayList<>();
    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> upgrade : skill.getUpgrades()) {
        for (int level = 2; level <= 3; level++) {
          StatLines below = new StatLines();
          StatLines at = new StatLines();
          upgrade.addStats(level - 1, below);
          upgrade.addStats(level, at);
          if (below.lines().equals(at.lines())) {
            flat.add(upgrade.getName() + " " + (level - 1) + "->" + level);
          }
        }
      }
    }
    assertThat(flat).isEmpty();
  }
}
