package art.arcane.adapt.api.mutation.blocks;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.localization.AdaptLanguage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MutatedTextTest extends AdaptTestBase {
  /**
   * SHA-256 of every composed English mutation name, generic adaptation name, description as the recipes compose it
   * before override rows, and block text. Any change to the English parts changes it; update it only for an intended
   * text change. Override and signature rows are checked against the composed adaptations below, so adding one does
   * not change this.
   */
  private static final String ENGLISH_DIGEST = "fb5c5df69b73e6d30796968881860354191633277601b0777f3a8b6666c1473b";

  @Test
  void composedEnglishIsByteIdenticalToTheEnglishWrittenPerPair() throws Exception {
    StringBuilder all = new StringBuilder();
    int adaptations = 0;
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        all.append(MutatedText.mutationName(MutatedText.ENGLISH, lead.skillId(), partner.skillId())).append('\n');
        for (Recipe recipe : Recipe.values()) {
          all.append(MutatedText.adaptationName(MutatedText.ENGLISH, partner.skillId(), recipe)).append('\u0000')
              .append(MutatedText.generated(MutatedText.ENGLISH, lead, partner, recipe)).append('\n');
          adaptations++;
        }
      }
      for (BlockSlot slot : BlockSlot.values()) {
        all.append(MutatedText.blockName(MutatedText.ENGLISH, lead.ref(slot))).append('\u0000')
            .append(lead.text(slot)).append('\n');
      }
    }
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(all.toString().getBytes(StandardCharsets.UTF_8)));

    assertThat(adaptations).isEqualTo(552 * 5);
    assertThat(digest).isEqualTo(ENGLISH_DIGEST);
  }

  @Test
  void composedEnglishMatchesTheRecipesWithOverridesApplied() {
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        for (MutatedAdaptation adaptation : MutationRecipes.compose(lead, partner)) {
          assertThat(MutatedText.description(MutatedText.ENGLISH, lead, partner, adaptation.recipe()))
              .isEqualTo(adaptation.description());
          assertThat(AdaptLanguage.render(MutatedText.description(MutatedText.VIEWER, lead, partner, adaptation.recipe())))
              .isEqualTo(adaptation.description());
          assertThat(AdaptLanguage.render(MutatedText.name(MutatedText.VIEWER, lead.skillId(), partner.skillId(), adaptation.recipe())))
              .isEqualTo(adaptation.name());
        }
      }
    }
  }

  @Test
  void englishSamplesReadAsBefore() {
    SkillBlocks swords = SkillBlockCatalog.require("swords");
    SkillBlocks stealth = SkillBlockCatalog.require("stealth");
    SkillBlocks agility = SkillBlockCatalog.require("agility");
    SkillBlocks hunter = SkillBlockCatalog.require("hunter");

    assertThat(MutatedText.mutationName(MutatedText.ENGLISH, "swords", "stealth")).isEqualTo("Silent Duelist");
    assertThat(MutatedText.adaptationName(MutatedText.ENGLISH, "stealth", Recipe.CONVERGENCE))
        .isEqualTo("Silent Convergence");
    assertThat(MutatedText.blockName(MutatedText.ENGLISH, new BlockRef("pickaxe", BlockSlot.STRIKE_FIGHT)))
        .isEqualTo("Pickaxes Fight Strike");
    assertThat(MutatedText.description(MutatedText.ENGLISH, agility, swords, Recipe.GRAFT))
        .isEqualTo("When you sprint-jump, the target bleeds for 4 s if a hostile is within 6 blocks; otherwise "
            + "grass, leaves and crops in a 3-block arc are cut and collected.");
    assertThat(MutatedText.description(MutatedText.ENGLISH, swords, stealth, Recipe.BARTER))
        .isEqualTo("Sneak and left-click the air to spend your cover and 2 points of hunger (you are revealed): you gain "
            + "+15% attack speed for 5 s.");
    assertThat(MutatedText.description(MutatedText.ENGLISH, swords, hunter, Recipe.ECHO))
        .isEqualTo("When you kill a natural mob, the nearest other hostile within 6 blocks bleeds for 4 s.");
  }
}
