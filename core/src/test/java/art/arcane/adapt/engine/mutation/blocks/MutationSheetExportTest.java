package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes the blocks and every mutated adaptation, before and after pair claims, to {@code build/mutation-sheet} for
 * the review sheet.
 */
class MutationSheetExportTest {
  private static final Path OUT = Path.of(System.getProperty("user.dir")).resolve("build/mutation-sheet");

  @TempDir
  Path dataFolder;

  @Test
  void exportsBlocksAndMutations() throws IOException {
    List<String> blocks = new ArrayList<>();
    blocks.add("Skill id,Skill,Lead noun,Partner adjective,Trigger kind,Trigger,Strike fight,Strike work,Boon,Fuel,State");
    List<String> mutations = new ArrayList<>();
    mutations.add("Lead id,Partner id,Lead,Partner,Mutation,Recipe,Generated name,Generated description,Generated blocks,"
        + "Name,Description,Blocks,Form,Overridden");
    List<Class<? extends SimpleSkill<?>>> types = BuiltinAddon.skills().stream()
        .<Class<? extends SimpleSkill<?>>>map(BuiltinAddon.Entry::type).toList();
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), types)) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      PairRegistry claims = registry.getPairRegistry();
      List<Skill<?>> skills = new ArrayList<>(registry.getSkills());
      for (Skill<?> skill : skills) {
        MutationKit kit = Kits.of(skill);
        List<String> row = new ArrayList<>(List.of(skill.getId().path(), english(skill),
            MutatedText.noun(MutatedText.ENGLISH, skill), MutatedText.adjective(MutatedText.ENGLISH, skill),
            kit.triggerKind().name().toLowerCase(Locale.ROOT)));
        for (BlockSlot slot : BlockSlot.values()) {
          row.add(MutatedText.phrase(MutatedText.ENGLISH, skill, slot));
        }
        blocks.add(csv(row));
      }
      for (Skill<?> lead : skills) {
        for (Skill<?> partner : skills) {
          if (lead == partner) {
            continue;
          }
          MutatedText.Parents parents = parents(lead, partner, claims);
          List<MutatedAdaptation> generated = MutationRecipes.generate(parents.pair(), Kits.of(lead).triggerKind(),
              Kits.of(partner).triggerKind());
          List<MutatedAdaptation> composed = MutationRecipes.compose(parents.pair(), Kits.of(lead).triggerKind(),
              Kits.of(partner).triggerKind(), claims);
          for (int i = 0; i < generated.size(); i++) {
            MutatedAdaptation raw = generated.get(i);
            MutatedAdaptation result = composed.get(i);
            Recipe recipe = raw.recipe();
            String rawDescription = MutatedText.generated(MutatedText.ENGLISH, parents, recipe);
            String description = MutatedText.description(MutatedText.ENGLISH, parents, recipe);
            mutations.add(csv(List.of(lead.getId().path(), partner.getId().path(), english(lead), english(partner),
                MutatedText.mutationName(MutatedText.ENGLISH, parents),
                MutatedText.message(MutatedText.ENGLISH, "mutated.recipe." + recipe.name().toLowerCase(Locale.ROOT)),
                MutatedText.adaptationName(MutatedText.ENGLISH, parents, recipe), rawDescription, refs(raw.blocks()),
                MutatedText.name(MutatedText.ENGLISH, parents, recipe), description, refs(result.blocks()),
                result.form().name().toLowerCase(Locale.ROOT),
                raw.equals(result) && rawDescription.equals(description) ? "no" : "yes")));
          }
        }
      }
    }
    Files.createDirectories(OUT);
    Files.write(OUT.resolve("blocks.csv"), blocks, StandardCharsets.UTF_8);
    Files.write(OUT.resolve("mutations.csv"), mutations, StandardCharsets.UTF_8);
    assertThat(mutations).hasSize(2761);
  }

  private static MutatedText.Parents parents(Skill<?> lead, Skill<?> partner, PairRegistry claims) {
    return new MutatedText.Parents(lead, partner, claims);
  }

  private static String english(Skill<?> skill) {
    return MutatedText.skillName(MutatedText.ENGLISH, skill);
  }

  private static String refs(List<BlockKey> blocks) {
    List<String> parts = new ArrayList<>();
    for (BlockKey block : blocks) {
      parts.add(block.toString());
    }
    return String.join(" ", parts);
  }

  private static String csv(List<String> fields) {
    List<String> quoted = new ArrayList<>();
    for (String field : fields) {
      quoted.add("\"" + field.replace("\"", "\"\"") + "\"");
    }
    return String.join(",", quoted);
  }
}
