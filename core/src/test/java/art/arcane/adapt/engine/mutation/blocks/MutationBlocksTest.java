package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.MutationPrice;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.mutation.UpgradeDeclaration;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MutationBlocksTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static PairRegistry claims;

  @BeforeAll
  static void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    claims = registry.getPairRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void catalogHas24SkillsWith144BlocksAnd72Upgrades() {
    List<Skill<?>> skills = builtIn();
    Set<BlockKey> blocks = new HashSet<>();
    Set<String> upgradeNames = new HashSet<>();
    int upgrades = 0;
    for (Skill<?> skill : skills) {
      MutationKit kit = skill.getMutationKit().orElseThrow();
      for (BlockSlot slot : BlockSlot.values()) {
        assertThat(MutatedText.phrase(MutatedText.ENGLISH, skill, slot)).as("%s %s", skill.getId(), slot).isNotBlank();
      }
      for (MutationBlock<?> block : SkillHost.of(skill).getBlocks()) {
        blocks.add(block.getKey());
      }
      assertThat(kit.upgrades()).as("%s upgrades", skill.getId()).hasSize(3);
      for (UpgradeDeclaration upgrade : kit.upgrades()) {
        Adaptation<?> built = registry.getAdaptation(upgrade.id());
        String name = english(built, ".name");
        assertThat(name).isNotBlank();
        assertThat(english(built, ".description")).isNotBlank();
        assertThat(upgrade.sources()).as(name).isNotEmpty();
        assertThat(upgrade.price().costs()).as(name)
            .containsExactlyElementsOf(upgrade.price().equals(MutationPrice.TOOL) ? List.of(15, 14, 18) : List.of(13, 13, 16));
        upgradeNames.add(name);
        upgrades++;
      }
    }
    assertThat(skills).hasSize(24);
    assertThat(skills).extracting(Skill::getId).doesNotHaveDuplicates();
    assertThat(blocks).hasSize(144);
    assertThat(upgrades).isEqualTo(72);
    assertThat(upgradeNames).hasSize(72);
  }

  @Test
  void every552MutationHasAUniqueNameAndFiveWrittenAdaptations() {
    Set<String> names = new HashSet<>();
    int mutations = 0;
    int adaptations = 0;
    for (Skill<?> lead : builtIn()) {
      for (Skill<?> partner : builtIn()) {
        if (lead == partner) {
          continue;
        }
        mutations++;
        MutatedText.Parents parents = new MutatedText.Parents(lead, partner, claims);
        names.add(MutatedText.mutationName(MutatedText.ENGLISH, parents));
        List<MutatedAdaptation> composed = compose(lead, partner);
        assertThat(composed).extracting(MutatedAdaptation::recipe).containsExactly(Recipe.values());
        for (MutatedAdaptation adaptation : composed) {
          adaptations++;
          String name = MutatedText.name(MutatedText.ENGLISH, parents, adaptation.recipe());
          assertThat(name).isNotBlank();
          assertThat(MutatedText.description(MutatedText.ENGLISH, parents, adaptation.recipe())).as(name)
              .isNotBlank().endsWith(".");
          if (claims.signature(adaptation.slot()).isPresent()) {
            assertThat(adaptation.blocks()).as("a signature replaces the blocks").isEmpty();
            continue;
          }
          assertThat(adaptation.blocks()).isNotEmpty()
              .allSatisfy(block -> assertThat(block.skill()).isIn(lead.getId(), partner.getId()));
        }
      }
    }
    assertThat(mutations).isEqualTo(552);
    assertThat(names).hasSize(552);
    assertThat(adaptations).isEqualTo(2760);
  }

  @Test
  void strikeFormFollowsTheTrigger() {
    for (Skill<?> lead : builtIn()) {
      for (Skill<?> partner : builtIn()) {
        if (lead == partner) {
          continue;
        }
        for (MutatedAdaptation adaptation : generate(lead, partner)) {
          List<BlockSlot> slots = new ArrayList<>();
          for (BlockKey block : adaptation.blocks()) {
            slots.add(block.slot());
          }
          switch (adaptation.form()) {
            case FIGHT -> assertThat(slots).contains(BlockSlot.STRIKE_FIGHT).doesNotContain(BlockSlot.STRIKE_WORK);
            case WORK -> assertThat(slots).contains(BlockSlot.STRIKE_WORK).doesNotContain(BlockSlot.STRIKE_FIGHT);
            case EITHER -> assertThat(slots).contains(BlockSlot.STRIKE_FIGHT, BlockSlot.STRIKE_WORK);
            case NONE -> assertThat(slots).doesNotContain(BlockSlot.STRIKE_FIGHT, BlockSlot.STRIKE_WORK);
          }
        }
      }
    }
  }

  @Test
  void recipesComposeThePlanExample() {
    Skill<?> herbalism = skill("herbalism");
    Skill<?> chronos = skill("chronos");
    MutatedText.Parents parents = new MutatedText.Parents(herbalism, chronos, claims);
    List<MutatedAdaptation> generated = generate(herbalism, chronos);

    assertThat(MutatedText.mutationName(MutatedText.ENGLISH, parents)).isEqualTo("Timeless Warden");
    assertThat(generated).extracting((MutatedAdaptation adaptation) ->
            MutatedText.adaptationName(MutatedText.ENGLISH, parents, adaptation.recipe()))
        .containsExactly("Timeless Graft", "Timeless Echo", "Timeless Barter", "Timeless Attunement",
            "Timeless Convergence");
    assertThat(generated).extracting((MutatedAdaptation adaptation) ->
            MutatedText.generated(MutatedText.ENGLISH, parents, adaptation.recipe())).containsExactly(
        "When you harvest a mature crop, crops, furnaces and brewing stands within 4 blocks advance 10 s.",
        "When you sneak and swap hands while carrying a clock, the target is rooted by vines for 2 s if a hostile is"
            + " within 6 blocks; otherwise crops and saplings within 4 blocks grow one stage.",
        "Double-tap sneak to spend a 20 s cooldown on your clock: you gain 2 points of hunger restored.",
        "While you stand in crops, grass or flowers, every 20 s you gain 5 s added to your active potion effects.",
        "When you harvest a mature crop while a clock is in your off hand: crops and saplings within 4 blocks grow one"
            + " stage. Also, crops, furnaces and brewing stands within 4 blocks advance 10 s. 45 s cooldown.");
    assertThat(generated.getFirst().blocks()).containsExactly(
        new BlockKey(Id.adapt("herbalism"), BlockSlot.TRIGGER), new BlockKey(Id.adapt("chronos"), BlockSlot.STRIKE_WORK));
    assertThat(generated).extracting(MutatedAdaptation::form)
        .containsExactly(StrikeForm.WORK, StrikeForm.EITHER, StrikeForm.NONE, StrikeForm.NONE, StrikeForm.WORK);
  }

  @Test
  void swappingTheLeadTradesGraftAndEcho() {
    Skill<?> swords = skill("swords");
    Skill<?> stealth = skill("stealth");
    MutatedText.Parents forward = new MutatedText.Parents(swords, stealth, claims);
    MutatedText.Parents reverse = new MutatedText.Parents(stealth, swords, claims);

    assertThat(MutatedText.generated(MutatedText.ENGLISH, forward, Recipe.GRAFT))
        .isEqualTo(MutatedText.generated(MutatedText.ENGLISH, reverse, Recipe.ECHO));
    assertThat(MutatedText.generated(MutatedText.ENGLISH, forward, Recipe.ECHO))
        .isEqualTo(MutatedText.generated(MutatedText.ENGLISH, reverse, Recipe.GRAFT));
    assertThat(MutatedText.mutationName(MutatedText.ENGLISH, forward)).isEqualTo("Silent Duelist");
  }

  @Test
  void pricesMatchThePlan() {
    assertThat(Recipe.GRAFT.price().costs()).containsExactly(9, 8, 11, 13, 16);
    assertThat(Recipe.ECHO.price().costs()).containsExactly(9, 8, 11, 13, 16);
    assertThat(Recipe.BARTER.price().costs()).containsExactly(6, 6, 7);
    assertThat(Recipe.ATTUNEMENT.price().costs()).containsExactly(13, 13, 16);
    assertThat(Recipe.CONVERGENCE.price().costs()).containsExactly(22, 21, 27);
    assertThat(MutationPrice.UPGRADED.costs()).containsExactly(13, 13, 16);

    assertThat(total(Recipe.GRAFT.price())).isEqualTo(57);
    assertThat(total(Recipe.BARTER.price())).isEqualTo(19);
    assertThat(total(Recipe.ATTUNEMENT.price())).isEqualTo(42);
    assertThat(total(Recipe.CONVERGENCE.price())).isEqualTo(70);
    assertThat(total(MutationPrice.UPGRADED)).isEqualTo(42);
    assertThat(Recipe.CONVERGENCE.price().cooldownSeconds()).isEqualTo(45);
    assertThat(6 * total(MutationPrice.UPGRADED) + 2 * total(Recipe.GRAFT.price())
        + total(Recipe.BARTER.price()) + total(Recipe.ATTUNEMENT.price())
        + total(Recipe.CONVERGENCE.price())).isEqualTo(497);
  }

  @Test
  void composeAppliesThePairTextForItsPairOnly() {
    Skill<?> herbalism = skill("herbalism");
    Skill<?> hunter = skill("hunter");
    MutatedText.Parents parents = new MutatedText.Parents(herbalism, hunter, claims);
    MutatedAdaptation barter = compose(herbalism, hunter).get(Recipe.BARTER.ordinal());
    MutatedAdaptation reverse = compose(hunter, herbalism).get(Recipe.BARTER.ordinal());

    assertThat(MutatedText.name(MutatedText.ENGLISH, parents, Recipe.BARTER)).isEqualTo("Feral Barter");
    assertThat(MutatedText.description(MutatedText.ENGLISH, parents, Recipe.BARTER)).isEqualTo(
        "Sneak and swap hands to spend 2 points of hunger: crops and saplings within 4 blocks grow one stage.");
    assertThat(barter.blocks()).containsExactly(
        new BlockKey(Id.adapt("hunter"), BlockSlot.FUEL), new BlockKey(Id.adapt("herbalism"), BlockSlot.STRIKE_WORK));
    assertThat(reverse).isEqualTo(MutationRecipes.generate(new SkillPair(hunter.getId(), herbalism.getId()),
        Kits.of(hunter).triggerKind(), Kits.of(herbalism).triggerKind(), Recipe.BARTER));
  }

  @Test
  void pairTextsNameRealPairsOnce() {
    int texts = 0;
    for (Skill<?> lead : builtIn()) {
      for (Skill<?> partner : builtIn()) {
        if (lead == partner) {
          continue;
        }
        MutatedText.Parents parents = new MutatedText.Parents(lead, partner, claims);
        for (Recipe recipe : Recipe.values()) {
          MutationSlot slot = parents.slot(recipe);
          Optional<PairRegistry.Claim<PairText>> claim = claims.text(slot);
          if (claim.isEmpty()) {
            continue;
          }
          texts++;
          String description = MutatedText.description(MutatedText.ENGLISH, parents, recipe);
          assertThat(description).as(slot.toString()).isNotBlank();
          assertThat(description).as("pair text that changes nothing for %s", slot)
              .isNotEqualTo(MutatedText.generated(MutatedText.ENGLISH, parents, recipe));
          Optional<List<BlockKey>> blocks = claim.get().value().blocks();
          if (blocks.isPresent()) {
            assertThat(blocks.get()).isNotEmpty()
                .allSatisfy(block -> assertThat(block.skill()).isIn(lead.getId(), partner.getId()));
          }
        }
      }
    }
    assertThat(texts).isPositive();
  }

  private static List<Skill<?>> builtIn() {
    return registry.getSkills().stream().filter((Skill<?> skill) -> Id.ADAPT.equals(skill.getId().namespace())).toList();
  }

  private static Skill<?> skill(String path) {
    return registry.getSkill(Id.adapt(path));
  }

  private static List<MutatedAdaptation> compose(Skill<?> lead, Skill<?> partner) {
    return MutationRecipes.compose(new SkillPair(lead.getId(), partner.getId()), Kits.of(lead).triggerKind(),
        Kits.of(partner).triggerKind(), claims);
  }

  private static List<MutatedAdaptation> generate(Skill<?> lead, Skill<?> partner) {
    return MutationRecipes.generate(new SkillPair(lead.getId(), partner.getId()), Kits.of(lead).triggerKind(),
        Kits.of(partner).triggerKind());
  }

  private static String english(Adaptation<?> adaptation, String suffix) {
    return ((TextKey) AdaptLanguage.declared(((SimpleAdaptation<?>) adaptation).getLocalizationKey() + suffix)).english();
  }

  private static int total(MutationPrice price) {
    int total = 0;
    for (int cost : price.costs()) {
      total += cost;
    }
    return total;
  }
}
