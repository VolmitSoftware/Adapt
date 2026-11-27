package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.MutationPrice;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.UpgradeDeclaration;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
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
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void theKitsDeclareAll72UpgradesWithDistinctIdsAndClasses() {
    List<UpgradeDeclaration> declarations = new ArrayList<>();
    Set<String> classes = new HashSet<>();
    for (Skill<?> skill : registry.getAllSkills()) {
      for (UpgradeDeclaration declaration : Kits.of(skill).upgrades()) {
        declarations.add(declaration);
        assertThat(declaration.id().toString()).matches("adapt:" + skill.getId().path() + "/upgrade/[a-z0-9]+(_[a-z0-9]+)*");
        assertThat(classes.add(registry.getAdaptation(declaration.id()).getClass().getName()))
            .as(declaration.id().toString()).isTrue();
      }
    }

    assertThat(declarations).hasSize(72);
    assertThat(registry.getAdaptation(Id.adapt("swords/upgrade/duelists_verdict")).getClass().getName())
        .isEqualTo("art.arcane.adapt.content.adaptation.sword.upgraded.DuelistsVerdict");
    assertThat(MutationPrice.UPGRADED.costs()).containsExactly(13, 13, 16);
    assertThat(MutationPrice.TOOL.costs()).containsExactly(15, 14, 18);
    assertThat(total(MutationPrice.TOOL)).isEqualTo(47);
    assertThat(declarations.stream().filter((UpgradeDeclaration declaration) -> declaration.price().equals(MutationPrice.TOOL))
        .map((UpgradeDeclaration declaration) -> declaration.id().toString()))
        .containsExactlyInAnyOrder("adapt:architect/upgrade/master_builder", "adapt:architect/upgrade/skyhook",
            "adapt:architect/upgrade/wrecking_hand", "adapt:rift/upgrade/pocket_realm", "adapt:pickaxe/upgrade/earthshaker",
            "adapt:excavation/upgrade/landshaper", "adapt:crafting/upgrade/field_workshop", "adapt:herbalism/upgrade/harvest_moon",
            "adapt:seaborne/upgrade/leviathan", "adapt:taming/upgrade/one_mind", "adapt:agility/upgrade/skyrunner",
            "adapt:discovery/upgrade/omniscience", "adapt:golemancy/upgrade/forgemaster", "adapt:ranged/upgrade/stormshot",
            "adapt:hunter/upgrade/apex_predator", "adapt:swords/upgrade/tempest_flow", "adapt:brewing/upgrade/master_distiller");
  }

  @Test
  void everyRegisteredUpgradeFollowsItsDeclaration() {
    int checked = 0;
    for (Skill<?> skill : registry.getAllSkills()) {
      List<Adaptation<?>> upgrades = SkillHost.of(skill).getUpgrades();
      assertThat(upgrades).as("upgrades of %s", SkillHost.lineKey(skill)).hasSizeLessThanOrEqualTo(3);
      for (Adaptation<?> upgrade : upgrades) {
        assertThat(upgrade).isInstanceOf(SkillUpgrade.class);
        UpgradeDeclaration declaration = declaration(skill, upgrade.getId());
        String english = english(upgrade, ".name");
        assertThat(upgrade.getClass().getPackageName()).endsWith(".upgraded");
        assertThat(upgrade.getClass().getSimpleName()).isEqualTo(className(english));
        assertThat(upgrade.getSkill()).isSameAs(skill);
        assertThat(skill.getAdaptations()).doesNotContain(upgrade);
        assertThat(upgrade.getMaxLevel()).isEqualTo(3);
        assertThat(List.of(upgrade.getCostFor(1), upgrade.getCostFor(2), upgrade.getCostFor(3)))
            .as("price of %s", declaration.id()).isEqualTo(declaration.price().costs());
        assertThat(((SkillUpgrade<?>) upgrade).getConfig() instanceof SkillUpgrade.ToolConfig)
            .as("%s config tier", declaration.id()).isEqualTo(declaration.price().equals(MutationPrice.TOOL));
        assertThat(C.stripColor(upgrade.getDisplayName())).isEqualTo(english);
        assertThat(upgrade.getDescription()).isEqualTo(english(upgrade, ".description"));
        assertThat(ConfigFieldMessages.declaresLabel("adaptations." + upgrade.getId() + ".enabled")).as(declaration.id().toString()).isTrue();
        for (int level = 1; level <= 3; level++) {
          StatLines stats = new StatLines();
          upgrade.addStats(level, stats);
          assertThat(stats.lines()).as("stat lines of %s at level %s", declaration.id(), level).isNotEmpty();
        }
        checked++;
      }
    }
    assertThat(checked).isGreaterThanOrEqualTo(3);
  }

  @Test
  void everyUpgradeStandsInForItsDeclaredSourcesAndFusesItsDeclaredOriginals() {
    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
        SkillUpgrade<?> standIn = (SkillUpgrade<?>) upgrade;
        UpgradeDeclaration declaration = declaration(skill, upgrade.getId());
        List<Id> sources = skill.getAdaptations().stream().filter(standIn::isSource).map(Adaptation::getId).toList();
        List<Id> fused = skill.getAdaptations().stream().filter(standIn::fuses).map(Adaptation::getId).toList();
        assertThat(sources).as("sources of %s", upgrade.getId().toString())
            .containsExactlyInAnyOrderElementsOf(declaration.sources());
        assertThat(fused).as("originals %s fuses", upgrade.getId().toString())
            .containsExactlyInAnyOrderElementsOf(declaration.fuses());
        assertThat(SkillHost.of(skill).getUpgrades()).noneMatch(standIn::isSource);
      }
    }
  }

  private static UpgradeDeclaration declaration(Skill<?> skill, Id id) {
    return Kits.of(skill).upgrades().stream().filter((UpgradeDeclaration declaration) -> declaration.id().equals(id))
        .findFirst().orElseThrow(() -> new AssertionError(id + " is not declared by " + skill.getId()));
  }

  private static String english(Adaptation<?> upgrade, String suffix) {
    String key = ((SimpleAdaptation<?>) upgrade).getLocalizationKey() + suffix;
    return ((TextKey) AdaptLanguage.declared(key)).english();
  }

  private static int total(MutationPrice price) {
    int total = 0;
    for (int cost : price.costs()) {
      total += cost;
    }
    return total;
  }

  /** "Duelist's Verdict" becomes DuelistsVerdict. */
  private static String className(String name) {
    StringBuilder builder = new StringBuilder();
    for (String word : name.replace("'", "").split("[^A-Za-z0-9]+")) {
      if (!word.isEmpty()) {
        builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return builder.toString();
  }

  @Test
  void everyAdaptationAndUpgradeIsFoundByItsId() {
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        assertThat(registry.getAdaptation(adaptation.getId())).isSameAs(adaptation);
      }
      for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
        assertThat(registry.getAdaptation(upgrade.getId())).isSameAs(upgrade);
      }
    }
    assertThat(registry.getAdaptation(Id.adapt("no-such-adaptation"))).isNull();
  }

  @Test
  void everyUpgradeLevelReadsDifferentlyFromTheLevelBelow() {
    List<String> flat = new ArrayList<>();
    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
        for (int level = 2; level <= 3; level++) {
          StatLines below = new StatLines();
          StatLines at = new StatLines();
          upgrade.addStats(level - 1, below);
          upgrade.addStats(level, at);
          if (below.lines().equals(at.lines())) {
            flat.add(upgrade.getId().toString() + " " + (level - 1) + "->" + level);
          }
        }
      }
    }
    assertThat(flat).isEmpty();
  }
}
