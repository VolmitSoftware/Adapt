package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MutatedTextTest {
  /**
   * SHA-256 of every composed English mutation name, generic adaptation name, description as the recipes compose it
   * before pair texts and signatures, and block text. Any change to the English parts changes it; update it only for an
   * intended text change. Pair texts and signatures are checked against the composed adaptations below, so adding one
   * does not change this.
   */
  private static final String ENGLISH_DIGEST = "fb5c5df69b73e6d30796968881860354191633277601b0777f3a8b6666c1473b";
  /** The built-in skills in the order of the mutation plan, which the digest is taken in. */
  private static final List<String> PLAN = List.of("swords", "axes", "unarmed", "ranged", "blocking", "stealth", "hunter",
      "tragoul", "agility", "kinetics", "rift", "chronos", "pickaxe", "excavation", "herbalism", "architect", "crafting",
      "enchanting", "brewing", "discovery", "taming", "golemancy", "seaborne", "nether");

  @TempDir
  Path dataFolder;

  @Test
  void composedEnglishIsByteIdenticalToTheEnglishWrittenPerPair() throws Exception {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      StringBuilder all = new StringBuilder();
      int adaptations = 0;
      for (String lead : PLAN) {
        for (String partner : PLAN) {
          if (lead.equals(partner)) {
            continue;
          }
          MutatedText.Parents parents = parents(registry, lead, partner);
          all.append(MutatedText.mutationName(MutatedText.ENGLISH, parents)).append('\n');
          for (Recipe recipe : Recipe.values()) {
            all.append(MutatedText.adaptationName(MutatedText.ENGLISH, parents, recipe)).append('\u0000')
                .append(MutatedText.generated(MutatedText.ENGLISH, parents, recipe)).append('\n');
            adaptations++;
          }
        }
        for (BlockSlot slot : BlockSlot.values()) {
          all.append(MutatedText.blockName(MutatedText.ENGLISH, registry.getSkill(Id.adapt(lead)), slot)).append('\u0000')
              .append(MutatedText.phrase(MutatedText.ENGLISH, registry.getSkill(Id.adapt(lead)), slot)).append('\n');
        }
      }
      String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(all.toString().getBytes(StandardCharsets.UTF_8)));

      assertThat(adaptations).isEqualTo(552 * 5);
      assertThat(digest).isEqualTo(ENGLISH_DIGEST);
    }
  }

  @Test
  void composedEnglishMatchesTheMutatedAdaptationsWithClaimsApplied() {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      PairRegistry claims = registry.getPairRegistry();
      int claimed = 0;
      for (String lead : PLAN) {
        for (String partner : PLAN) {
          if (lead.equals(partner)) {
            continue;
          }
          MutatedText.Parents parents = parents(registry, lead, partner);
          MutatedSkill mutated = registry.getMutatedSkill(parents.pair());
          for (Recipe recipe : Recipe.values()) {
            MutationSlot slot = parents.slot(recipe);
            String english = MutatedText.description(MutatedText.ENGLISH, parents, recipe);
            assertThat(AdaptLanguage.render(MutatedText.description(MutatedText.VIEWER, parents, recipe))).isEqualTo(english);
            assertThat(AdaptLanguage.render(MutatedText.name(MutatedText.VIEWER, parents, recipe)))
                .isEqualTo(MutatedText.name(MutatedText.ENGLISH, parents, recipe));
            Optional<PairRegistry.Claim<PairText>> text = claims.text(slot);
            boolean signature = claims.signature(slot).isPresent();
            if (text.isPresent() || signature) {
              claimed++;
              assertThat(english).as(slot.toString()).isNotEqualTo(MutatedText.generated(MutatedText.ENGLISH, parents, recipe));
            } else {
              assertThat(english).isEqualTo(MutatedText.generated(MutatedText.ENGLISH, parents, recipe));
            }
            MutationAdaptation built = mutated.getMutationAdaptation(recipe);
            if (built != null) {
              assertThat(built.getDescription()).as(slot.toString()).isEqualTo(AdaptLanguage.render(
                  MutatedText.description(MutatedText.VIEWER, parents, recipe)));
            }
          }
        }
      }
      assertThat(claimed).isPositive();
    }
  }

  @Test
  void englishSamplesReadAsBefore() {
    try (TestAdapt adapt = start()) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();

      assertThat(MutatedText.mutationName(MutatedText.ENGLISH, parents(registry, "swords", "stealth")))
          .isEqualTo("Silent Duelist");
      assertThat(MutatedText.adaptationName(MutatedText.ENGLISH, parents(registry, "swords", "stealth"), Recipe.CONVERGENCE))
          .isEqualTo("Silent Convergence");
      assertThat(MutatedText.blockName(MutatedText.ENGLISH, registry.getSkill(Id.adapt("pickaxe")), BlockSlot.STRIKE_FIGHT))
          .isEqualTo("Pickaxes Fight Strike");
      assertThat(MutatedText.description(MutatedText.ENGLISH, parents(registry, "agility", "swords"), Recipe.GRAFT))
          .isEqualTo("When you sprint-jump, the target bleeds for 4 s if a hostile is within 6 blocks; otherwise "
              + "grass, leaves and crops in a 3-block arc are cut and collected.");
      assertThat(MutatedText.description(MutatedText.ENGLISH, parents(registry, "swords", "stealth"), Recipe.BARTER))
          .isEqualTo("Sneak and left-click the air to spend your cover and 2 points of hunger (you are revealed): you gain "
              + "+15% attack speed for 5 s.");
      assertThat(MutatedText.description(MutatedText.ENGLISH, parents(registry, "swords", "hunter"), Recipe.ECHO))
          .isEqualTo("When you kill a natural mob, the nearest other hostile within 6 blocks bleeds for 4 s.");
    }
  }

  private TestAdapt start() {
    List<Class<? extends SimpleSkill<?>>> skills = BuiltinAddon.skills().stream()
        .<Class<? extends SimpleSkill<?>>>map(BuiltinAddon.Entry::type).toList();
    return TestAdapt.start(TestPlatform.builder(dataFolder).build(), skills);
  }

  private static MutatedText.Parents parents(SkillRegistry registry, String lead, String partner) {
    return new MutatedText.Parents(registry.getSkill(Id.adapt(lead)), registry.getSkill(Id.adapt(partner)),
        registry.getPairRegistry());
  }
}
