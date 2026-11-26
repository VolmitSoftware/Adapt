package art.arcane.adapt.api.mutation.tool;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationSpreadViews;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.client.view.ToolViews;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every tool the mutation spread promises exists after the merge: a registered tool with recipes gated by its owner,
 * listed on the mutated skill's page under the same name the item carries, and its recipes reach a player's recipe
 * book once the owner is learned and leave it on unmutate.
 */
class MutationToolReachTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;

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
  void everyToolTheSpreadListsForEveryPairIsObtainableAndOnTheMutatedPage() {
    AdaptPlayer player = adapt.join(platform.player("Spread", 0.5D, 64D, 0.5D));
    for (Skill<?> skill : registry.getSkills()) {
      raise(player, skill.getName(), 30);
    }
    Map<String, ToolView> views = new HashMap<>();
    for (ToolView view : ToolViews.build(registry, UUID.randomUUID())) {
      views.put(view.toolId(), view);
    }
    Set<String> listed = new HashSet<>();
    int pairs = 0;
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner || SkillMutations.check(player.getData(), lead, partner, AdaptConfig.get().getMutations())
            != SkillMutations.Result.MUTATED) {
          continue;
        }
        pairs++;
        MutationSpreadView spread = MutationSpreadViews.build(registry, player.getData(), lead, partner,
            AdaptConfig.get().getMutations(), "");
        MutatedSkill mutated = registry.getMutatedSkill(new SkillMutation(lead.getName(), partner.getName()).id());
        Set<String> page = new HashSet<>();
        for (Adaptation<?> adaptation : mutated.getAdaptations()) {
          page.add(adaptation.getName());
        }
        List<MutationEntry> tools = new ArrayList<>(spread.leadSide().tools());
        tools.addAll(spread.partnerSide().tools());
        for (MutationEntry entry : tools) {
          String where = lead.getName() + " + " + partner.getName() + ": " + entry.id();
          MutationTool<?> tool = MutationTools.find(entry.id());
          assertThat(tool).as(where).isNotNull();
          ToolView view = views.get(entry.id());
          assertThat(view).as(where).isNotNull();
          assertThat(page).as("%s shows on the mutated page", where).contains(view.adaptationId());
          assertThat(view.requiredLevel()).as(where).isEqualTo(tool.requiredLevel());
          assertThat(tool.recipes()).as(where).isNotEmpty();
          for (RecipeSpec recipe : tool.recipes()) {
            assertThat(platform.recipes().registered()).as("%s recipe %s", where, recipe.key()).contains(recipe.key());
            assertThat(registry.getRequiredAdaptation(recipe.key())).as(where).isSameAs(tool.owner());
            assertThat(C.stripColor(recipe.result().displayName())).as("%s item name", where).isEqualTo(entry.name());
          }
          assertThat(view.name()).as("%s page name", where).isEqualTo(entry.name());
          assertThat(entry.detail()).as("%s says what unlocks it", where)
              .contains(C.stripColor(tool.owner().getDisplayName())).doesNotContain("{");
          listed.add(entry.id());
        }
      }
    }
    assertThat(pairs).isGreaterThan(400);
    List<String> every = new ArrayList<>();
    for (MutationTool<?> tool : MutationTools.all(registry)) {
      every.add(tool.id());
    }
    assertThat(listed).as("every registered tool is promised by some pair").containsExactlyInAnyOrderElementsOf(every);
  }

  @Test
  void learningTheOwnerAfterMutatingUnlocksTheRecipesAndUnmutatingTakesThemBack() {
    FakePlayer player = platform.player("Runner", 4.5D, 64D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    raise(profile, "agility", 26);
    raise(profile, "stealth", 26);
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("agility"), registry.getSkill("stealth"), "Runner"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    List<MutationTool<?>> tools = new ArrayList<>();
    tools.addAll(MutationTools.handedOut(registry.getAdaptation("upgraded-agility-skyrunner")));
    tools.addAll(MutationTools.handedOut(registry.getAdaptation("upgraded-stealth-phantom-decoy")));
    assertThat(tools).extracting(MutationTool::id).containsExactly("tool-agility-grappling-line", "tool-stealth-smoke-pellet");
    List<Id> keys = new ArrayList<>();
    for (MutationTool<?> tool : tools) {
      for (RecipeSpec recipe : tool.recipes()) {
        keys.add(recipe.key());
      }
    }
    synchronize(player, profile);
    assertThat(platform.recipes().discovered(player)).doesNotContainAnyElementsOf(keys);

    for (MutationTool<?> tool : tools) {
      assertThat(AdaptationLearningTransaction.learn(tool.owner(), player, tool.requiredLevel(), true))
          .isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    }
    synchronize(player, profile);
    assertThat(platform.recipes().discovered(player)).containsAll(keys);

    assertThat(SkillMutations.unmutate(profile.getData(), new SkillMutation("agility", "stealth"), "Runner")).isTrue();
    synchronize(player, profile);
    assertThat(platform.recipes().discovered(player)).doesNotContainAnyElementsOf(keys);
  }

  private static void raise(AdaptPlayer player, String skill, int level) {
    player.getData().setMasterXp(1_000_000D);
    PlayerSkillLine line = player.getData().getSkillLine(skill);
    line.setXp(XP.getXpForLevel(level) + 1D);
    line.settleWithoutRewards();
  }

  private static void synchronize(FakePlayer player, AdaptPlayer profile) {
    adapt.core().getAdaptServer().synchronizeRecipeBook(player.uuid(), profile);
    platform.scheduler().advance(2);
  }
}
