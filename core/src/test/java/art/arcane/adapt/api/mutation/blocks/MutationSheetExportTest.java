package art.arcane.adapt.api.mutation.blocks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes the blocks and every mutated adaptation, before and after overrides, to {@code build/mutation-sheet} for
 * the review sheet.
 */
class MutationSheetExportTest {
  private static final Path OUT = Path.of(System.getProperty("user.dir")).resolve("build/mutation-sheet");

  @Test
  void exportsBlocksAndMutations() throws IOException {
    List<String> blocks = new ArrayList<>();
    blocks.add("Skill id,Skill,Lead noun,Partner adjective,Trigger kind,Trigger,Strike fight,Strike work,Boon,Fuel,State");
    List<String> mutations = new ArrayList<>();
    mutations.add("Lead id,Partner id,Lead,Partner,Mutation,Recipe,Generated name,Generated description,Generated blocks,"
        + "Name,Description,Blocks,Form,Overridden");
    for (SkillBlocks skill : SkillBlockCatalog.all()) {
      blocks.add(csv(skill.skillId(), skill.skillName(), skill.leadNoun(), skill.partnerAdjective(),
          skill.triggerKind().name().toLowerCase(Locale.ROOT), skill.trigger(), skill.strikeFight(), skill.strikeWork(),
          skill.boon(), skill.fuel(), skill.state()));
    }
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        List<MutatedAdaptation> generated = MutationRecipes.generate(lead, partner);
        List<MutatedAdaptation> composed = MutationRecipes.compose(lead, partner);
        for (int i = 0; i < generated.size(); i++) {
          MutatedAdaptation raw = generated.get(i);
          MutatedAdaptation result = composed.get(i);
          mutations.add(csv(lead.skillId(), partner.skillId(), lead.skillName(), partner.skillName(),
              MutationRecipes.mutationName(lead, partner), raw.recipe().displayName(), raw.name(), raw.description(),
              refs(raw.blocks()), result.name(), result.description(), refs(result.blocks()),
              result.form().name().toLowerCase(Locale.ROOT), raw.equals(result) ? "no" : "yes"));
        }
      }
    }
    Files.createDirectories(OUT);
    Files.write(OUT.resolve("blocks.csv"), blocks, StandardCharsets.UTF_8);
    Files.write(OUT.resolve("mutations.csv"), mutations, StandardCharsets.UTF_8);
    assertThat(mutations).hasSize(2761);
  }

  private static String refs(List<BlockRef> blocks) {
    List<String> parts = new ArrayList<>();
    for (BlockRef block : blocks) {
      parts.add(block.toString());
    }
    return String.join(" ", parts);
  }

  private static String csv(String... fields) {
    List<String> quoted = new ArrayList<>();
    for (String field : fields) {
      quoted.add("\"" + field.replace("\"", "\"\"") + "\"");
    }
    return String.join(",", quoted);
  }
}
