package art.arcane.adapt.content.gui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.SpendViews;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The skills menu's seats keep roster order with a mutation in its lead's seat, an empty slot in its partner's and greyed
 * unstarted seats, the last two of which the server option hides by closing up; an unmutate fills both seats again; and what the player spends knowledge on equals the learn cost and the unlearn
 * refund of every held adaptation, upgrades and mutated ones included.
 */
class SeatsAndSpendingTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static String lead;
  private static String partner;
  private static int counter;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    for (SkillBlocks blocks : SkillBlockCatalog.all()) {
      if (lead == null && !SkillUpgrades.kept(registry.getSkill(blocks.skillId())).isEmpty()) {
        lead = blocks.skillId();
      }
    }
    for (SkillBlocks blocks : SkillBlockCatalog.all()) {
      if (partner == null && !blocks.skillId().equals(lead)) {
        partner = blocks.skillId();
      }
    }
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @AfterEach
  void showInactive() throws ReflectiveOperationException {
    setShowInactive(true);
  }

  private static void setShowInactive(boolean shown) throws ReflectiveOperationException {
    Field field = AdaptConfig.Gui.class.getDeclaredField("showInactiveSkills");
    field.setAccessible(true);
    field.setBoolean(AdaptConfig.get().getGui(), shown);
  }

  private static FakePlayer player() {
    return platform.player("Seat" + counter++, 0.5D, 61D, 0.5D);
  }

  @Test
  void aMergedPartnersSeatIsEmptyAndEveryOtherSeatKeepsItsPlace() {
    FakePlayer player = player();
    AdaptPlayer profile = adapt.join(player);
    List<String> roster = names(SkillsGui.seats(profile, player));
    List<String> enabled = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (skill.isEnabled()) {
        enabled.add(skill.getName());
      }
    }
    assertThat(roster).containsExactlyInAnyOrderElementsOf(enabled);

    new MutationKit(adapt, profile).mutate(lead, partner);
    platform.scheduler().advance(20);
    List<SkillsGui.Seat> seats = SkillsGui.seats(profile, player);

    assertThat(seats).hasSize(roster.size());
    assertThat(seats.get(roster.indexOf(lead)).skill()).isInstanceOf(MutatedSkill.class);
    assertThat(seats.get(roster.indexOf(partner)).empty()).isTrue();
    assertThat(seats).filteredOn(SkillsGui.Seat::empty).hasSize(1);
    for (int index = 0; index < roster.size(); index++) {
      if (index != roster.indexOf(lead) && index != roster.indexOf(partner)) {
        assertThat(seats.get(index).skill().getName()).isEqualTo(roster.get(index));
      }
    }
    assertThat(seats).anyMatch((SkillsGui.Seat seat) -> !seat.empty() && !seat.started());
  }

  @Test
  void anUnmutateFillsBothParentSeatsAgain() {
    FakePlayer player = player();
    AdaptPlayer profile = adapt.join(player);
    List<String> roster = names(SkillsGui.seats(profile, player));
    new MutationKit(adapt, profile).mutate(lead, partner);
    platform.scheduler().advance(20);

    SkillMutations.unmutateAll(profile.getData(), player.name());
    List<SkillsGui.Seat> seats = SkillsGui.seats(profile, player);

    assertThat(names(seats)).containsExactlyElementsOf(roster);
    assertThat(seats.get(roster.indexOf(lead)).started()).isTrue();
    assertThat(seats.get(roster.indexOf(partner)).started()).isTrue();
  }

  private static List<String> names(List<SkillsGui.Seat> seats) {
    List<String> names = new ArrayList<>();
    for (SkillsGui.Seat seat : seats) {
      names.add(seat.empty() ? "" : seat.skill().getName());
    }
    return names;
  }

  @Test
  void theServerOptionHidesInactiveSeatsAndClosesUp() throws ReflectiveOperationException {
    FakePlayer player = player();
    AdaptPlayer profile = adapt.join(player);
    new MutationKit(adapt, profile).mutate(lead, partner);
    platform.scheduler().advance(20);
    List<SkillsGui.Seat> shown = SkillsGui.seats(profile, player);

    setShowInactive(false);
    List<SkillsGui.Seat> hidden = SkillsGui.seats(profile, player);

    List<SkillsGui.Seat> active = new ArrayList<>();
    for (SkillsGui.Seat seat : shown) {
      if (!seat.empty() && seat.started()) {
        active.add(seat);
      }
    }
    assertThat(hidden).extracting(SkillsGui.Seat::skill).containsExactlyElementsOf(active.stream().map(SkillsGui.Seat::skill).toList());
    assertThat(hidden).noneMatch((SkillsGui.Seat seat) -> seat.empty() || !seat.started());
  }

  @Test
  void spentKnowledgeEqualsLearnCostsAndRefundsForAnyHeldSet() {
    Random random = new Random(7L);
    for (int trial = 0; trial < 6; trial++) {
      FakePlayer player = player();
      AdaptPlayer profile = adapt.join(player);
      MutationKit kit = new MutationKit(adapt, profile);
      kit.mutate(lead, partner);
      PlayerData data = profile.getData();
      for (String skill : List.of(lead, partner)) {
        for (Adaptation<?> upgrade : registry.getSkill(skill).getUpgrades()) {
          if (random.nextBoolean()) {
            data.getSkillLine(skill).setAdaptation(upgrade, 1 + random.nextInt(upgrade.getMaxLevel()));
          }
        }
      }
      Recipe recipe = Recipe.values()[random.nextInt(Recipe.values().length)];
      Adaptation<?> mutatedAdaptation = kit.learn(lead, partner, recipe, 1 + random.nextInt(3));
      for (Skill<?> skill : registry.getSkills()) {
        if (skill.getName().equals(lead) || skill.getName().equals(partner) || !skill.isEnabled()) {
          continue;
        }
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (random.nextInt(8) == 0) {
            data.getSkillLine(skill.getName()).setAdaptation(adaptation, 1 + random.nextInt(adaptation.getMaxLevel()));
          }
        }
      }
      platform.scheduler().advance(5);

      List<SpendView> spends = SpendViews.build(registry, profile);
      int power = 0;
      boolean sawUpgrade = false;
      boolean sawMutated = false;
      for (SpendView spend : spends) {
        if (spend.source() != SpendView.Source.LEARNED) {
          assertThat(spend.knowledge()).isZero();
          assertThat(spend.power()).isZero();
          continue;
        }
        Adaptation<?> adaptation = registry.getAdaptation(spend.adaptationId());
        assertThat(adaptation).as(spend.adaptationId()).isNotNull();
        assertThat(spend.knowledge()).as(spend.adaptationId()).isEqualTo(adaptation.getCostFor(spend.level(), 0))
            .isEqualTo(adaptation.getRefundCostFor(0, spend.level()));
        power += spend.power();
        sawUpgrade |= adaptation instanceof SkillUpgrade<?>;
        sawMutated |= adaptation == mutatedAdaptation;
      }
      assertThat(power).isEqualTo(data.getUsedPower());
      assertThat(sawMutated).isTrue();
      assertThat(spends).anyMatch((SpendView spend) -> spend.source() == SpendView.Source.KEPT);
      if (trial == 0) {
        assertThat(sawUpgrade || data.getUsedPower() > 0).isTrue();
      }
    }
  }
}
