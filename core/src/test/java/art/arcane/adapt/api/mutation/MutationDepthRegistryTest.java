package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.MutationOverrides;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationEngine;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.signature.Signatures;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationToolGuard;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.mutation.tool.ToolOwner;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every declared signature has its handler, its text and its gate, every tool its owner, recipe gate and name, and every
 * overcharge its label and switch. Runs for every lane without edits.
 */
class MutationDepthRegistryTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static TestPlatform platform;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void everySignatureRowHasItsHandlerBuiltByTheLeadSkill() {
    assertThat(Signatures.rows()).isNotEmpty();
    Set<String> ids = new HashSet<>();
    for (MutationOverride row : Signatures.rows()) {
      String id = Signature.id(row);
      assertThat(ids.add(id)).as("one row for %s", id).isTrue();
      assertThat(row.handler().getPackageName()).as(id).isEqualTo(Signature.class.getPackageName());
      assertThat(MutationOverrides.find(row.leadId(), row.partnerId(), row.recipe())).as(id).isSameAs(row);
      Skill<?> lead = registry.getSkill(row.leadId());
      assertThat(lead.getSignatures()).as("%s built by %s", id, row.leadId())
          .anySatisfy((Signature<?> signature) -> {
            assertThat(signature.getName()).isEqualTo(id);
            assertThat(signature).isInstanceOf(row.handler());
            assertThat(signature.getConfig()).isInstanceOf(Signature.Config.class);
          });
      MutatedSkill mutated = registry.getMutatedSkill(new SkillMutation(row.leadId(), row.partnerId()).id());
      MutationAdaptation replaced = mutated.getMutationAdaptation(row.recipe());
      assertThat(replaced).as("%s replaces a live adaptation", id).isNotNull();
      assertThat(replaced.getSignature()).isNotNull();
      assertThat(replaced.getSignature().getName()).isEqualTo(id);
      assertThat(replaced.getBlocks()).isEmpty();
    }
  }

  @Test
  void everySignatureHasItsOwnTextAndStatLabels() {
    for (MutationOverride row : Signatures.rows()) {
      String prefix = MutatedMessages.signaturePrefix(row);
      assertThat(row.name()).as(prefix).isNotBlank();
      assertThat(row.description()).as(prefix).isNotBlank();
      assertThat(AdaptMessages.require(prefix + ".name")).isNotNull();
      assertThat(AdaptMessages.require(prefix + ".description")).isNotNull();
      for (int index = 1; index <= row.lore().size(); index++) {
        assertThat(AdaptMessages.require(prefix + ".lore" + index)).isNotNull();
      }
      Signature<?> signature = signature(row);
      for (int level = 1; level <= signature.getMaxLevel(); level++) {
        int current = level;
        StatLines stats = new StatLines();
        assertThatCode(() -> signature.addSignatureStats(current, stats)).as("%s stat labels", prefix).doesNotThrowAnyException();
        assertThat(stats.lines().size()).as("%s uses every label", prefix).isEqualTo(row.lore().size());
      }
    }
  }

  @Test
  void aSignatureRunsOnlyForTheLearnedPairAdaptation() {
    int index = 0;
    for (MutationOverride row : Signatures.rows()) {
      FakePlayer player = platform.player("Sig" + index++, 0.5D, 61D, 0.5D);
      AdaptPlayer profile = adapt.join(player);
      Signature<?> signature = signature(row);
      assertThat(signature.levelIn(profile.getData())).isZero();
      MutationKit kit = new MutationKit(adapt, profile);
      kit.mutate(row.leadId(), row.partnerId());
      assertThat(signature.levelIn(profile.getData())).isZero();
      kit.learn(row.leadId(), row.partnerId(), row.recipe(), 2);
      PlayerData stored = profile.getData();
      assertThat(signature.levelIn(stored)).isEqualTo(2);
      assertThat(signature.getLevel(player)).isEqualTo(2);
      assertThat(SkillUpgrades.holdable(signature, stored)).isFalse();
      assertThat(MutationEngine.heldSignatures(stored)).contains(signature.getName());
    }
  }

  @Test
  void everyToolHasItsOwnerRecipeGateAndName() {
    Set<String> ids = new HashSet<>();
    for (MutationTool<?> tool : MutationTools.all(registry)) {
      assertThat(ids.add(tool.id())).as("one tool %s", tool.id()).isTrue();
      Adaptation<?> owner = tool.owner();
      assertThat(owner).isInstanceOfAny(SkillUpgrade.class, Signature.class);
      assertThat(((ToolOwner) owner).getTools()).contains(tool);
      assertThat(registry.getAdaptation(owner.getName())).isSameAs(owner);
      assertThat(tool.requiredLevel()).isBetween(1, owner.getMaxLevel());
      assertThat(AdaptMessages.require(tool.nameKey().id())).isNotNull();
      assertThat(tool.recipes()).as("%s recipes", tool.id()).isNotEmpty();
      for (RecipeSpec recipe : tool.recipes()) {
        assertThat(MutationToolGuard.isToolRecipe(recipe.key())).as(recipe.key().toString()).isTrue();
        assertThat(recipe.key().path()).startsWith(tool.id());
        assertThat(tool.is(recipe.result())).as("%s makes its tool", recipe.key()).isTrue();
        assertThat(registry.getRequiredAdaptation(recipe.key())).as("%s gated by its owner", recipe.key()).isSameAs(owner);
        assertThat(registry.getRequiredRecipeLevel(recipe.key())).isGreaterThanOrEqualTo(tool.requiredLevel());
      }
    }
  }

  @Test
  void everyOverchargeHasItsLabelAndSwitch() {
    int declared = 0;
    for (Skill<?> skill : registry.getAllSkills()) {
      for (Adaptation<?> upgrade : skill.getUpgrades()) {
        SkillUpgrade<?> skillUpgrade = (SkillUpgrade<?>) upgrade;
        boolean offers = skillUpgrade.getPlayerPreferences().contains(SkillUpgrade.OVERCHARGE);
        assertThat(offers).as("%s switch", upgrade.getName()).isEqualTo(skillUpgrade.hasOvercharge());
        if (skillUpgrade.hasOvercharge()) {
          assertThat(AdaptMessages.require(skillUpgrade.overchargeKey())).isNotNull();
          declared++;
        }
      }
    }
    assertThat(declared).isPositive();
  }

  @Test
  void aSignatureKeepsThePriceOfTheRecipeItReplaces() {
    for (MutationOverride row : Signatures.rows()) {
      MutationAdaptation replaced = registry.getMutatedSkill(new SkillMutation(row.leadId(), row.partnerId()).id())
          .getMutationAdaptation(row.recipe());
      assertThat(signature(row).getMaxLevel()).isEqualTo(row.recipe().price().maxLevel());
      for (int level = 1; level <= row.recipe().price().maxLevel(); level++) {
        assertThat(replaced.getCostFor(level)).isEqualTo(row.recipe().price().costFor(level));
      }
    }
  }

  private static Signature<?> signature(MutationOverride row) {
    for (Signature<?> signature : registry.getSkill(row.leadId()).getSignatures()) {
      if (signature.getName().equals(Signature.id(row))) {
        return signature;
      }
    }
    throw new AssertionError("No signature for " + Signature.id(row));
  }
}
