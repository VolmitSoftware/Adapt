package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every signature Adapt registers replaces a live mutated adaptation, has its own text and stat labels, runs only for
 * the learned slot and keeps the replaced recipe's price.
 */
class BuiltinSignaturesTest {
  @TempDir
  static Path data;

  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static TestPlatform platform;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    adapt.awaitTicks(registry::isRecipeRegistrationReady, 10_000L);
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void everyBuiltInSignatureReplacesALiveAdaptationOfItsPair() {
    List<MutationSlot> slots = builtIn();
    assertThat(slots).hasSize(70);
    Set<String> ids = new HashSet<>();
    for (MutationSlot slot : slots) {
      String id = Signature.id(slot).toString();
      assertThat(ids.add(id)).as("one claim for %s", id).isTrue();
      MutationAdaptation replaced = replaced(slot);
      assertThat(replaced).as("%s replaces a live adaptation", id).isNotNull();
      Signature<?> signature = replaced.getSignature();
      assertThat(signature).as(id).isNotNull();
      assertThat(signature.getId().toString()).isEqualTo(id);
      assertThat(signature.getClass().getPackageName()).isEqualTo(BuiltinPairs.class.getPackageName());
      assertThat(signature.getSlot()).isEqualTo(slot);
      assertThat(signature.getConfig()).isInstanceOf(Signature.Config.class);
      assertThat(replaced.getBlocks()).isEmpty();
    }
  }

  @Test
  void everyBuiltInSignatureHasItsOwnTextAndStatLabels() {
    for (MutationSlot slot : builtIn()) {
      Signature<?> signature = replaced(slot).getSignature();
      String prefix = PairMessages.signatureKey(slot);
      assertThat(signature.getLocalizationKey()).isEqualTo(PairMessages.signatureKey(slot));
      assertThat(AdaptLanguage.builtInCatalog().require(prefix + ".name")).isNotNull();
      assertThat(AdaptLanguage.builtInCatalog().require(prefix + ".description")).isNotNull();
      int labels = 0;
      for (String id : AdaptLanguage.builtInCatalog().ids()) {
        labels += id.startsWith(prefix + ".lore") ? 1 : 0;
      }
      assertThat(labels).as("%s labels", prefix).isPositive();
      for (int level = 1; level <= signature.getMaxLevel(); level++) {
        int current = level;
        StatLines stats = new StatLines();
        assertThatCode(() -> signature.addSignatureStats(current, stats)).as("%s stat labels", prefix)
            .doesNotThrowAnyException();
        assertThat(stats.lines().size()).as("%s uses every label", prefix).isEqualTo(labels);
      }
    }
  }

  @Test
  void aBuiltInSignatureRunsOnlyForTheLearnedPairAdaptation() {
    int index = 0;
    for (MutationSlot slot : builtIn()) {
      FakePlayer player = platform.player("Sig" + index++, 0.5D, 61D, 0.5D);
      AdaptPlayer profile = adapt.join(player);
      Signature<?> signature = replaced(slot).getSignature();
      assertThat(signature.getLevel(player)).isZero();
      MutationKit kit = new MutationKit(adapt, profile);
      String lead = slot.pair().lead().path();
      String partner = slot.pair().partner().path();
      kit.mutate(lead, partner);
      assertThat(signature.getLevel(player)).isZero();
      kit.learn(lead, partner, slot.recipe(), 2);
      PlayerData stored = profile.getData();
      assertThat(signature.getLevel(player)).isEqualTo(2);
      assertThat(SkillUpgrades.holdable(signature, stored)).isFalse();
      assertThat(MutationEngine.heldSignatures(stored)).contains(signature.getId().toString());
    }
  }

  @Test
  void aBuiltInSignatureKeepsThePriceOfTheRecipeItReplaces() {
    for (MutationSlot slot : builtIn()) {
      MutationAdaptation replaced = replaced(slot);
      assertThat(replaced.getSignature().getMaxLevel()).isEqualTo(slot.recipe().price().maxLevel());
      for (int level = 1; level <= slot.recipe().price().maxLevel(); level++) {
        assertThat(replaced.getCostFor(level)).isEqualTo(slot.recipe().price().costFor(level));
      }
    }
  }

  private static List<MutationSlot> builtIn() {
    PairRegistry pairs = registry.getPairRegistry();
    return pairs.signatureSlots().stream()
        .filter((MutationSlot slot) -> pairs.signature(slot).orElseThrow().owner().equals("adapt"))
        .toList();
  }

  private static MutationAdaptation replaced(MutationSlot slot) {
    MutatedSkill mutated = registry.getMutatedSkill(slot.pair());
    return mutated == null ? null : mutated.getMutationAdaptation(slot.recipe());
  }
}
