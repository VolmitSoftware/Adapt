package art.arcane.adapt.content;

import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots every skill and adaptation against the registries of each supported Minecraft version and writes the
 * per-version catalog (adaptation, works / emulated / partial / disabled, reason) to {@code build/reports/platform-support}.
 * The 1.21.1 and 1.20.1 catalogs are kept under {@code src/test/resources/platform-support}; run with
 * {@code -Dadapt.updatePlatformCatalog=true} to rewrite them after an intended change. Mutation blocks are left out
 * here and checked per skill by {@code MutationBlockVersionsTest}.
 */
class PlatformSupportCatalogTest {
  private static final Path REPORTS = Path.of("build/reports/platform-support");
  private static final Path GOLDEN = Path.of("../core/src/test/resources/platform-support");
  private static final String HEADER = "skill\tadaptation\tstatus\treason";

  @TempDir
  Path dataFolder;

  @BeforeEach
  void forgetEarlierRuns() {
    PlatformSupport.reset();
  }

  @AfterEach
  void forgetThisRun() {
    PlatformSupport.reset();
  }

  @Test
  void nothingIsDisabledOrEmulatedOn26_3() throws IOException {
    List<String> catalog = catalog("26.3", FakeGameContent.v26_3());

    assertThat(PlatformSupport.leftOut()).isEmpty();
    assertThat(catalog).allMatch((String row) -> row.equals(HEADER) || row.contains("\tworks\t"));
  }

  @Test
  void nothingIsDisabledOrEmulatedOn26_1_2() throws IOException {
    List<String> catalog = catalog("26.1.2", FakeGameContent.v26_1_2());

    assertThat(PlatformSupport.leftOut()).isEmpty();
    assertThat(catalog).allMatch((String row) -> row.equals(HEADER) || row.contains("\tworks\t"));
  }

  @Test
  void catalogFor1_21_1MatchesTheCheckedInCatalog() throws IOException {
    assertGolden("1.21.1", catalog("1.21.1", FakeGameContent.v1_21_1()));
  }

  @Test
  void catalogFor1_20_1MatchesTheCheckedInCatalog() throws IOException {
    assertGolden("1.20.1", catalog("1.20.1", FakeGameContent.v1_20_1()));
  }

  private List<String> catalog(String version, FakeGameContent game) throws IOException {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(game);
    List<String> rows = new ArrayList<>();
    rows.add(HEADER);
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      PlatformHost host = platform.host();
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      Set<String> listed = new TreeSet<>();
      List<String> missingIcons = new ArrayList<>();
      for (Skill<?> skill : registry.getSkills()) {
        requireIcon(host, "skill " + SkillHost.lineKey(skill), skill.getIcon().item(), missingIcons);
        if (skill instanceof SimpleSkill<?> simple) {
          requireAdvancementIcons(host, ComponentHost.of(simple).buildAdvancements(), missingIcons);
        }
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          requireIcon(host, "adaptation " + adaptation.getId().toString(), adaptation.getIcon().item(), missingIcons);
          PlatformSupport.Verdict verdict = PlatformSupport.check(adaptation.getPlatformNeeds(), host);
          String status = verdict.isPartial()
              ? "partial\t" + verdict.partialText()
              : verdict.isEmulated() ? "emulated\t" + verdict.emulatedText() : "works\t";
          listed.add(SkillHost.lineKey(skill) + "\t" + adaptation.getId().toString() + "\t" + status);
        }
      }
      for (PlatformSupport.LeftOut disabled : PlatformSupport.leftOut()) {
        if (disabled.kind().equals("mutation-block")) {
          continue;
        }
        listed.add(disabled.owner() + "\t" + disabled.name() + "\tdisabled\t" + disabled.verdict().missingText());
      }
      rows.addAll(listed);
      assertThat(missingIcons).as("icons missing on %s", version).isEmpty();
      platform.scheduler().advance(200);
      List<String> brokenRecipes = new ArrayList<>();
      for (Id key : platform.recipes().registered()) {
        RecipeSpec recipe = platform.recipes().spec(key);
        if (recipe.result().isEmpty()) {
          brokenRecipes.add(key + " has no result");
        }
        for (Ingredient ingredient : ingredients(recipe)) {
          if (!usable(host, ingredient)) {
            brokenRecipes.add(key + " needs " + ingredient);
          }
        }
      }
      assertThat(platform.recipes().registered()).as("recipes registered on %s", version).isNotEmpty();
      assertThat(brokenRecipes).as("recipes with items missing on %s", version).isEmpty();
      assertThat(adapt.handlerFailures()).as("handler failures on %s", version).isEmpty();
      // The registry snapshots list tag names, not tag contents, so item tags in recipes cannot resolve here; slow-path
      // notes measure this machine, not content.
      assertThat(platform.log().warnings())
          .filteredOn((String warning) -> !warning.endsWith("is not a known item tag") && !warning.contains("slow-path"))
          .as("warnings at enable on %s", version).isEmpty();
    }
    Files.createDirectories(REPORTS);
    Files.write(REPORTS.resolve(version + ".tsv"), rows, StandardCharsets.UTF_8);
    return rows;
  }

  private static List<Ingredient> ingredients(RecipeSpec recipe) {
    return switch (recipe) {
      case RecipeSpec.Shaped shaped -> List.copyOf(shaped.ingredients().values());
      case RecipeSpec.Shapeless shapeless -> shapeless.ingredients();
      case RecipeSpec.Cooking cooking -> List.of(cooking.input());
      case RecipeSpec.Stonecutting stonecutting -> List.of(stonecutting.input());
      case RecipeSpec.Smithing smithing -> List.of(smithing.template(), smithing.base(), smithing.addition());
    };
  }

  private static boolean usable(PlatformHost host, Ingredient ingredient) {
    return switch (ingredient) {
      case Ingredient.AnyOf anyOf -> anyOf.types().stream().allMatch((Id type) -> host.registries().exists(RegistryKind.ITEM, type));
      case Ingredient.Tag tag -> true;
      case Ingredient.Exact exact -> !exact.item().isEmpty();
      case Ingredient.Matching matching -> host.registries().exists(RegistryKind.ITEM, matching.base()) && !matching.example().isEmpty();
    };
  }

  private static void requireAdvancementIcons(PlatformHost host, AdaptAdvancement advancement, List<String> missing) {
    requireIcon(host, "advancement " + advancement.getKey(), advancement.getIcon(), missing);
    for (AdaptAdvancement child : advancement.getChildren()) {
      requireAdvancementIcons(host, child, missing);
    }
  }

  private static void requireIcon(PlatformHost host, String owner, Id item, List<String> missing) {
    if (item == null || !host.registries().exists(RegistryKind.ITEM, item)) {
      missing.add(owner + " -> " + item);
    }
  }

  private static void assertGolden(String version, List<String> actual) throws IOException {
    Path golden = GOLDEN.resolve(version + ".tsv");
    if (Boolean.getBoolean("adapt.updatePlatformCatalog")) {
      Files.createDirectories(GOLDEN);
      Files.write(golden, actual, StandardCharsets.UTF_8);
    }
    assertThat(golden).as("checked-in catalog for %s", version).exists();
    assertThat(actual).as("catalog for %s (actual in %s)", version, REPORTS.resolve(version + ".tsv"))
        .containsExactlyElementsOf(Files.readAllLines(golden, StandardCharsets.UTF_8));
  }
}
