package art.arcane.adapt.api.mutation.blocks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MutationBlocksTest {
  @Test
  void catalogHas24SkillsWith144BlocksAnd72Upgrades() {
    List<SkillBlocks> skills = SkillBlockCatalog.all();
    Set<BlockRef> blocks = new HashSet<>();
    Set<String> upgradeNames = new HashSet<>();
    int upgrades = 0;
    for (SkillBlocks skill : skills) {
      for (BlockSlot slot : BlockSlot.values()) {
        assertThat(skill.text(slot)).as("%s %s", skill.skillId(), slot).isNotBlank();
      }
      blocks.addAll(skill.blocks());
      assertThat(skill.upgrades()).as("%s upgrades", skill.skillId()).hasSize(3);
      for (UpgradedAdaptation upgrade : skill.upgrades()) {
        assertThat(upgrade.name()).isNotBlank();
        assertThat(upgrade.description()).isNotBlank();
        assertThat(upgrade.sources()).as(upgrade.name()).isNotEmpty();
        assertThat(upgrade.price().costs()).as(upgrade.name())
            .containsExactlyElementsOf(upgrade.tool() ? List.of(15, 14, 18) : List.of(13, 13, 16));
        upgradeNames.add(upgrade.name());
        upgrades++;
      }
    }
    assertThat(skills).hasSize(24);
    assertThat(skills).extracting(SkillBlocks::skillId).doesNotHaveDuplicates();
    assertThat(blocks).hasSize(144);
    assertThat(upgrades).isEqualTo(72);
    assertThat(upgradeNames).hasSize(72);
  }

  @Test
  void every552MutationHasAUniqueNameAndFiveWrittenAdaptations() {
    Set<String> names = new HashSet<>();
    int mutations = 0;
    int adaptations = 0;
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        mutations++;
        names.add(MutationRecipes.mutationName(lead, partner));
        List<MutatedAdaptation> composed = MutationRecipes.compose(lead, partner);
        assertThat(composed).extracting(MutatedAdaptation::recipe).containsExactly(Recipe.values());
        for (MutatedAdaptation adaptation : composed) {
          adaptations++;
          assertThat(adaptation.name()).isNotBlank();
          assertThat(adaptation.description()).as(adaptation.name()).isNotBlank().endsWith(".");
          MutationOverride row = MutationOverrides.find(lead.skillId(), partner.skillId(), adaptation.recipe());
          if (row != null && row.isSignature()) {
            assertThat(adaptation.blocks()).as("a signature replaces the blocks").isEmpty();
            continue;
          }
          assertThat(adaptation.blocks()).isNotEmpty()
              .allSatisfy(block -> assertThat(block.skillId()).isIn(lead.skillId(), partner.skillId()));
        }
      }
    }
    assertThat(mutations).isEqualTo(552);
    assertThat(names).hasSize(552);
    assertThat(adaptations).isEqualTo(2760);
  }

  @Test
  void strikeFormFollowsTheTrigger() {
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        for (MutatedAdaptation adaptation : MutationRecipes.generate(lead, partner)) {
          List<BlockSlot> slots = new ArrayList<>();
          for (BlockRef block : adaptation.blocks()) {
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
    SkillBlocks herbalism = SkillBlockCatalog.require("herbalism");
    SkillBlocks chronos = SkillBlockCatalog.require("chronos");
    List<MutatedAdaptation> generated = MutationRecipes.generate(herbalism, chronos);

    assertThat(MutationRecipes.mutationName(herbalism, chronos)).isEqualTo("Timeless Warden");
    assertThat(generated).extracting(MutatedAdaptation::name)
        .containsExactly("Timeless Graft", "Timeless Echo", "Timeless Barter", "Timeless Attunement",
            "Timeless Convergence");
    assertThat(generated).extracting(MutatedAdaptation::description).containsExactly(
        "When you harvest a mature crop, crops, furnaces and brewing stands within 4 blocks advance 10 s.",
        "When you sneak and swap hands while carrying a clock, the target is rooted by vines for 2 s if a hostile is"
            + " within 6 blocks; otherwise crops and saplings within 4 blocks grow one stage.",
        "Double-tap sneak to spend a 20 s cooldown on your clock: you gain 2 points of hunger restored.",
        "While you stand in crops, grass or flowers, every 20 s you gain 5 s added to your active potion effects.",
        "When you harvest a mature crop while a clock is in your off hand: crops and saplings within 4 blocks grow one"
            + " stage. Also, crops, furnaces and brewing stands within 4 blocks advance 10 s. 45 s cooldown.");
    assertThat(generated.getFirst().blocks()).containsExactly(
        new BlockRef("herbalism", BlockSlot.TRIGGER), new BlockRef("chronos", BlockSlot.STRIKE_WORK));
    assertThat(generated).extracting(MutatedAdaptation::form)
        .containsExactly(StrikeForm.WORK, StrikeForm.EITHER, StrikeForm.NONE, StrikeForm.NONE, StrikeForm.WORK);
  }

  @Test
  void swappingTheLeadTradesGraftAndEcho() {
    SkillBlocks swords = SkillBlockCatalog.require("swords");
    SkillBlocks stealth = SkillBlockCatalog.require("stealth");
    List<MutatedAdaptation> forward = MutationRecipes.generate(swords, stealth);
    List<MutatedAdaptation> reverse = MutationRecipes.generate(stealth, swords);

    assertThat(forward.get(0).description()).isEqualTo(reverse.get(1).description());
    assertThat(forward.get(1).description()).isEqualTo(reverse.get(0).description());
    assertThat(MutationRecipes.mutationName(swords, stealth)).isEqualTo("Silent Duelist");
  }

  @Test
  void pricesMatchThePlan() {
    assertThat(Recipe.GRAFT.price().costs()).containsExactly(9, 8, 11, 13, 16);
    assertThat(Recipe.ECHO.price().costs()).containsExactly(9, 8, 11, 13, 16);
    assertThat(Recipe.BARTER.price().costs()).containsExactly(6, 6, 7);
    assertThat(Recipe.ATTUNEMENT.price().costs()).containsExactly(13, 13, 16);
    assertThat(Recipe.CONVERGENCE.price().costs()).containsExactly(22, 21, 27);
    assertThat(MutationPrice.UPGRADED.costs()).containsExactly(13, 13, 16);

    assertThat(Recipe.GRAFT.price().totalCost()).isEqualTo(57);
    assertThat(Recipe.BARTER.price().totalCost()).isEqualTo(19);
    assertThat(Recipe.ATTUNEMENT.price().totalCost()).isEqualTo(42);
    assertThat(Recipe.CONVERGENCE.price().totalCost()).isEqualTo(70);
    assertThat(MutationPrice.UPGRADED.totalCost()).isEqualTo(42);
    assertThat(Recipe.CONVERGENCE.price().cooldownSeconds()).isEqualTo(45);
    assertThat(6 * MutationPrice.UPGRADED.totalCost() + 2 * Recipe.GRAFT.price().totalCost()
        + Recipe.BARTER.price().totalCost() + Recipe.ATTUNEMENT.price().totalCost()
        + Recipe.CONVERGENCE.price().totalCost()).isEqualTo(497);
  }

  @Test
  void composeAppliesTheOverrideForItsPairOnly() {
    SkillBlocks herbalism = SkillBlockCatalog.require("herbalism");
    SkillBlocks hunter = SkillBlockCatalog.require("hunter");
    MutatedAdaptation barter = MutationRecipes.compose(herbalism, hunter).get(Recipe.BARTER.ordinal());
    MutatedAdaptation reverse = MutationRecipes.compose(hunter, herbalism).get(Recipe.BARTER.ordinal());

    assertThat(barter.name()).isEqualTo("Feral Barter");
    assertThat(barter.description()).isEqualTo(
        "Sneak and swap hands to spend 2 points of hunger: crops and saplings within 4 blocks grow one stage.");
    assertThat(barter.blocks()).containsExactly(
        new BlockRef("hunter", BlockSlot.FUEL), new BlockRef("herbalism", BlockSlot.STRIKE_WORK));
    assertThat(reverse).isEqualTo(MutationRecipes.generate(hunter, herbalism, Recipe.BARTER));
  }

  @Test
  void overridesNameRealPairsOnce() {
    for (MutationOverride override : MutationOverrides.all()) {
      assertThat(SkillBlockCatalog.byId(override.leadId())).as(override.leadId()).isPresent();
      assertThat(SkillBlockCatalog.byId(override.partnerId())).as(override.partnerId()).isPresent();
      assertThat(override.leadId()).isNotEqualTo(override.partnerId());
      assertThat(override.description()).isNotBlank();
      MutatedAdaptation generated = MutationRecipes.generate(SkillBlockCatalog.require(override.leadId()),
          SkillBlockCatalog.require(override.partnerId()), override.recipe());
      assertThat(override.description()).as("override that changes nothing").isNotEqualTo(generated.description());
      if (override.blocks() != null) {
        assertThat(override.blocks()).isNotEmpty()
            .allSatisfy(block -> assertThat(block.skillId()).isIn(override.leadId(), override.partnerId()));
      }
    }
  }
}
