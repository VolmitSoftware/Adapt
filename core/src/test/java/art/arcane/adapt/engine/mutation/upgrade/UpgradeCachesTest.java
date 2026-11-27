package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;

class UpgradeCachesTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void cachedFactsMatchAFreshScanForEveryOriginal() {
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> original : skill.getAdaptations()) {
        assertThat(Lending.isSource(original)).as(original.getId().toString()).isEqualTo(scanFused(original));
        assertThat(SkillUpgrades.keptLevel(original)).as(original.getId().toString()).isEqualTo(scanKeptLevel(original));
        assertThat(Lending.fused(original)).as(original.getId().toString()).isEqualTo(scanFusedBy(original));
      }
    }
  }

  @Test
  void levelsFollowMutateLearnUnlearnResetAndQuit() {
    Pair pair = pairWithKeptAndFused();
    Skill<?> skill = registry.getSkill(pair.skill());
    Adaptation<?> kept = SkillUpgrades.kept(skill).getFirst();
    Adaptation<?> fused = firstFused(skill);
    SkillUpgrade<?> upgrade = fusing(skill, fused);
    FakePlayer player = platform.player("Cache", 0.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    ToIntFunction<Adaptation<?>> levelOf = (Adaptation<?> held) -> held.getLevel(player);

    assertThat(kept.getLevel(player)).isZero();
    assertThat(Lending.level(fused, levelOf)).isZero();

    new MutationKit(adapt, profile).mutate(pair.lead(), pair.partner());
    assertThat(kept.getLevel(player)).isEqualTo(SkillUpgrades.keptLevel(kept));
    assertThat(Lending.level(fused, levelOf)).isZero();

    profile.getData().getSkillLine(pair.skill()).setAdaptation(upgrade, upgrade.getMaxLevel());
    assertThat(Lending.level(fused, levelOf)).isEqualTo(upgrade.lend(fused, upgrade.getMaxLevel()));

    profile.getData().getSkillLine(pair.skill()).setAdaptation(upgrade, 0);
    assertThat(Lending.level(fused, levelOf)).isZero();

    profile.getData().getSkillLine(pair.skill()).setAdaptation(upgrade, 1);
    adapt.post(new PlayerQuitEvent(player));
    adapt.awaitTicks(() -> adapt.core().getAdaptServer().getPlayer(player) == null, 10_000L);
    AdaptPlayer rejoined = adapt.join(player);
    assertThat(kept.getLevel(player)).isEqualTo(SkillUpgrades.keptLevel(kept));
    assertThat(Lending.level(fused, levelOf)).isEqualTo(upgrade.lend(fused, 1));

    SkillMutations.unmutateAll(rejoined.getData(), "Reset");
    assertThat(kept.getLevel(player)).isZero();
    assertThat(Lending.level(fused, levelOf)).isZero();
  }

  @Test
  void keptLevelFollowsConfigReloadAndNewRecipes() {
    SimpleAdaptation<?> kept = (SimpleAdaptation<?>) SkillUpgrades.kept(registry.getSkill(pairWithKeptAndFused().skill())).getFirst();
    int top = kept.getMaxLevel();
    assertThat(SkillUpgrades.keptLevel(kept)).isEqualTo(scanKeptLevel(kept));

    assertThat(ComponentHost.of(kept).reloadConfigSnapshot("maxLevel = 1\n", data.resolve("kept.toml").toFile(), false)).isTrue();
    assertThat(SkillUpgrades.keptLevel(kept)).isEqualTo(1);

    assertThat(ComponentHost.of(kept).reloadConfigSnapshot("maxLevel = " + top + "\n", data.resolve("kept.toml").toFile(), false)).isTrue();
    assertThat(SkillUpgrades.keptLevel(kept)).isEqualTo(scanKeptLevel(kept));

    if (top > SkillUpgrades.keptLevel(kept)) {
      RecipeSpec recipe = kept.getRecipes().isEmpty() ? null : kept.getRecipes().getFirst();
      if (recipe != null) {
        kept.registerRecipe(recipe.withRequiredLevel(top));
        assertThat(SkillUpgrades.keptLevel(kept)).isEqualTo(top);
      }
    }
  }

  private record Pair(String skill, String lead, String partner) {
  }

  private static Pair pairWithKeptAndFused() {
    List<Skill<?>> skills = registry.getSkills();
    for (int index = 0; index + 1 < skills.size(); index += 2) {
      String lead = skills.get(index).getId().path();
      String partner = skills.get(index + 1).getId().path();
      for (String candidate : List.of(lead, partner)) {
        Skill<?> skill = registry.getSkill(candidate);
        if (!SkillUpgrades.kept(skill).isEmpty() && firstFused(skill) != null) {
          return new Pair(candidate, lead, partner);
        }
      }
    }
    throw new IllegalStateException("No skill keeps one original and fuses another");
  }

  private static Adaptation<?> firstFused(Skill<?> skill) {
    for (Adaptation<?> original : skill.getAdaptations()) {
      if (scanFused(original)) {
        return original;
      }
    }
    return null;
  }

  private static SkillUpgrade<?> fusing(Skill<?> skill, Adaptation<?> original) {
    for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.isSource(original)) {
        return skillUpgrade;
      }
    }
    throw new IllegalStateException(original.getId().toString() + " has no fusing upgrade");
  }

  private static boolean scanFused(Adaptation<?> original) {
    for (Adaptation<?> upgrade : SkillHost.of(original.getSkill()).getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.isSource(original)) {
        return true;
      }
    }
    return false;
  }

  private static boolean scanFusedBy(Adaptation<?> original) {
    for (Adaptation<?> upgrade : SkillHost.of(original.getSkill()).getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.fuses(original)) {
        return true;
      }
    }
    return false;
  }

  private static int scanKeptLevel(Adaptation<?> adaptation) {
    int level = adaptation.getKeptLevel();
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      level = Math.max(level, recipe.requiredLevel());
    }
    return Math.max(1, Math.min(level, adaptation.getMaxLevel()));
  }
}
