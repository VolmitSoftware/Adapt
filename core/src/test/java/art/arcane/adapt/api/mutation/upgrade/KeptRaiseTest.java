package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction.Result;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PlayerViews;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.SpendViews;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A kept original raised above its free level with the mutated line's knowledge: the levels above it cost their normal
 * price, the first takes a power slot, refunds stop at the kept level, and an unmutate returns what was paid.
 */
class KeptRaiseTest {
  private static final SkillMutation MUTATION = new SkillMutation("swords", "stealth");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Keeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class, SkillAxes.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    new MutationKit(adapt, profile).mutate("swords", "stealth");
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void raisingPaysOnlyTheLevelsAboveTheKeptOneAndTakesOneSlot() {
    Adaptation<?> kept = raisable(0);
    int floor = SkillUpgrades.keptLevel(kept);
    PlayerSkillLine line = line();
    line.setKnowledge(10_000L);
    assertThat(data().getUsedPower()).isZero();

    assertThat(AdaptationLearningTransaction.learn(kept, player, floor + 1, false)).isEqualTo(Result.LEARNED);
    assertThat(line.getKnowledge()).isEqualTo(10_000L - kept.getCostFor(floor + 1));
    assertThat(data().getUsedPower()).isEqualTo(1);
    assertThat(kept.getLevel(player)).isEqualTo(floor + 1);

    assertThat(AdaptationLearningTransaction.learn(kept, player, floor + 2, false)).isEqualTo(Result.LEARNED);
    assertThat(line.getKnowledge()).isEqualTo(10_000L - kept.getCostFor(floor + 2, floor));
    assertThat(data().getUsedPower()).isEqualTo(1);
    assertThat(SkillUpgrades.heritageLevel(kept, data())).isEqualTo(floor + 2);

    assertThat(AdaptationLearningTransaction.unlearn(kept, player, floor + 1, false)).isEqualTo(Result.UNLEARNED);
    assertThat(data().getUsedPower()).isEqualTo(1);
    assertThat(AdaptationLearningTransaction.unlearn(kept, player, 0, false)).isEqualTo(Result.UNLEARNED);
    assertThat(line.getKnowledge()).isEqualTo(10_000L);
    assertThat(data().getUsedPower()).isZero();
    assertThat(line.getAdaptation(kept.getName())).isNull();
    assertThat(kept.getLevel(player)).isEqualTo(floor);
  }

  @Test
  void aKeptOriginalGoesStraightToAChosenLevelButNeverBelowKeptOrAboveItsMaximum() {
    Adaptation<?> kept = raisable(0);
    int floor = SkillUpgrades.keptLevel(kept);
    line().setKnowledge(1_000_000L);

    assertThat(AdaptationLearningTransaction.unlearn(kept, player, 0, false)).isEqualTo(Result.NO_CHANGE);
    assertThat(AdaptationLearningTransaction.learn(kept, player, floor, false)).isEqualTo(Result.NO_CHANGE);
    assertThat(AdaptationLearningTransaction.learn(kept, player, kept.getMaxLevel() + 5, false)).isEqualTo(Result.LEARNED);
    assertThat(line().getKnowledge()).isEqualTo(1_000_000L - kept.getCostFor(kept.getMaxLevel(), floor));
    assertThat(kept.getLevel(player)).isEqualTo(kept.getMaxLevel());
    assertThat(AdaptationLearningTransaction.learn(kept, player, kept.getMaxLevel() + 1, false)).isEqualTo(Result.NO_CHANGE);
    assertThat(AdaptationLearningTransaction.unlearn(kept, player, floor - 1, false)).isEqualTo(Result.UNLEARNED);
    assertThat(kept.getLevel(player)).isEqualTo(floor);
    assertThat(line().getKnowledge()).isEqualTo(1_000_000L);
  }

  @Test
  void raisingIsRefusedWithoutKnowledgeOrAFreeSlot() {
    Adaptation<?> kept = raisable(0);
    int floor = SkillUpgrades.keptLevel(kept);
    line().setKnowledge(kept.getCostFor(floor + 1) - 1L);
    assertThat(AdaptationLearningTransaction.learn(kept, player, floor + 1, false)).isEqualTo(Result.INSUFFICIENT_KNOWLEDGE);

    line().setKnowledge(100_000L);
    data().setMasterXp(XP.getXpForLevel(1) + 1D);
    Adaptation<?> upgrade = registry.getSkill("swords").getUpgrades().getFirst();
    line().setAdaptation(upgrade, 1);
    assertThat(data().getAvailablePower()).isZero();
    assertThat(AdaptationLearningTransaction.learn(kept, player, floor + 1, false)).isEqualTo(Result.INSUFFICIENT_POWER);
    assertThat(line().getKnowledge()).isEqualTo(100_000L);
    assertThat(kept.getLevel(player)).isEqualTo(floor);
  }

  @Test
  void lentOriginalsCannotBeRaised() {
    Adaptation<?> lent = null;
    for (Adaptation<?> original : registry.getSkill("swords").getAdaptations()) {
      if (UpgradeLoans.fused(original)) {
        lent = original;
        break;
      }
    }
    assertThat(lent).isNotNull();
    line().setKnowledge(100_000L);
    assertThat(SkillUpgrades.keptFloor(lent, data())).isZero();
    assertThat(AdaptationLearningTransaction.learn(lent, player, 2, false)).isEqualTo(Result.SKILL_LINE_UNAVAILABLE);
    assertThat(line().getKnowledge()).isEqualTo(100_000L);
  }

  @Test
  void aRaisedLevelSurvivesASaveAndShowsInTheViews() {
    Adaptation<?> kept = raisable(0);
    int floor = SkillUpgrades.keptLevel(kept);
    line().setKnowledge(100_000L);
    AdaptationLearningTransaction.learn(kept, player, floor + 2, false);

    PlayerData loaded = PlayerData.fromJson(data().toJson(true));
    assertThat(SkillUpgrades.heritageLevel(kept, loaded)).isEqualTo(floor + 2);
    assertThat(SkillUpgrades.raisedLevel(kept, loaded)).isEqualTo(floor + 2);

    PlayerView view = PlayerViews.build(registry, profile);
    assertThat(view.adaptationLevels()).containsEntry(kept.getName(), floor + 2);
    List<SpendView> spends = SpendViews.build(registry, profile);
    assertThat(spends).contains(
        new SpendView(MUTATION.id(), kept.getName(), floor + 2, kept.getRefundCostFor(floor, floor + 2), 1, SpendView.Source.LEARNED),
        new SpendView(MUTATION.id(), kept.getName(), floor, 0, 0, SpendView.Source.KEPT));
  }

  @Test
  void unmutateRefundsTheRaisedLevelsWithEverythingElse() {
    Adaptation<?> kept = raisable(0);
    int floor = SkillUpgrades.keptLevel(kept);
    line().setKnowledge(50_000L);
    AdaptationLearningTransaction.learn(kept, player, floor + 2, false);

    assertThat(SkillMutations.unmutate(data(), MUTATION, "Keeper")).isTrue();

    long total = data().getSkillLine("swords").getKnowledge() + data().getSkillLine("stealth").getKnowledge();
    assertThat(total).isEqualTo(50_000L);
    assertThat(data().getUsedPower()).isZero();
  }

  @Test
  void theTrimToThePowerBudgetDropsARaisedOriginalBackToItsKeptLevel() {
    Adaptation<?> first = raisable(0);
    Adaptation<?> second = raisable(1);
    line().setKnowledge(1_000_000L);
    AdaptationLearningTransaction.learn(first, player, SkillUpgrades.keptLevel(first) + 1, false);
    AdaptationLearningTransaction.learn(second, player, SkillUpgrades.keptLevel(second) + 1, false);
    assertThat(data().getUsedPower()).isEqualTo(2);

    data().setMasterXp(XP.getXpForLevel(1) + 1D);
    data().pruneAdaptationsForPowerBudget();

    assertThat(data().getUsedPower()).isEqualTo(data().getMaxPower()).isEqualTo(1);
    int dropped = 0;
    for (Adaptation<?> kept : List.of(first, second)) {
      if (kept.getLevel(player) == SkillUpgrades.keptLevel(kept)) {
        dropped++;
      }
    }
    assertThat(dropped).isEqualTo(1);
  }

  @Test
  void knowledgeIsConservedAcrossRandomRaisesRefundsAndAnUnmutate() {
    List<Adaptation<?>> raisable = raisables();
    assertThat(raisable).isNotEmpty();
    Random random = new Random(26_11_25L);
    for (int round = 0; round < 40; round++) {
      long start = 2_000_000L;
      line().setKnowledge(start);
      for (int step = 0; step < 12; step++) {
        Adaptation<?> kept = raisable.get(random.nextInt(raisable.size()));
        int target = random.nextInt(kept.getMaxLevel() + 2);
        if (random.nextBoolean()) {
          AdaptationLearningTransaction.learn(kept, player, target, false);
        } else {
          AdaptationLearningTransaction.unlearn(kept, player, target, false);
        }
        long invested = 0L;
        for (Adaptation<?> each : raisable) {
          invested += each.getCostFor(SkillUpgrades.heritageLevel(each, data()), SkillUpgrades.keptLevel(each));
          assertThat(each.getLevel(player)).isBetween(SkillUpgrades.keptLevel(each), each.getMaxLevel());
        }
        assertThat(line().getKnowledge() + invested).isEqualTo(start);
      }
      assertThat(SkillMutations.unmutate(data(), MUTATION, "Keeper")).isTrue();
      assertThat(data().getSkillLine("swords").getKnowledge() + data().getSkillLine("stealth").getKnowledge()).isEqualTo(start);
      new MutationKit(adapt, profile).mutate("swords", "stealth");
    }
  }

  private PlayerData data() {
    return profile.getData();
  }

  private PlayerSkillLine line() {
    return data().getSkillLine("swords");
  }

  private Adaptation<?> raisable(int index) {
    List<Adaptation<?>> raisable = raisables();
    assertThat(raisable).hasSizeGreaterThan(index);
    return raisable.get(index);
  }

  private List<Adaptation<?>> raisables() {
    MutatedSkill mutated = registry.getMutatedSkill(MUTATION.id());
    List<Adaptation<?>> raisable = new ArrayList<>();
    for (Adaptation<?> kept : mutated.getKept()) {
      if (kept.getMaxLevel() - SkillUpgrades.keptLevel(kept) >= 2 && kept.getRequirements().isEmpty() && !kept.isPermanent()) {
        raisable.add(kept);
      }
    }
    return raisable;
  }
}
