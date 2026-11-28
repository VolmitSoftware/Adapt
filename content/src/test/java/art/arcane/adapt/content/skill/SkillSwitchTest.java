package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.adaptation.agility.AgilityArmorUp;
import art.arcane.adapt.content.adaptation.rift.RiftConduit;
import art.arcane.adapt.content.adaptation.rift.RiftDescent;
import art.arcane.adapt.content.adaptation.rift.upgraded.Voidborn;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The switch beside Back on a skill page turns off every adaptation that page shows for the player: a skill's own, and
 * on a mutated page the pair's own, the parents' upgrades and the originals it keeps; what they applied goes with it.
 */
class SkillSwitchTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static int players;

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
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void everyAdaptationOfEveryBuiltInSkillStopsWithItsSwitchAndReturnsWithIt() {
    for (Skill<?> skill : registry.getSkills()) {
      FakePlayer player = player();
      AdaptPlayer profile = adapt.join(player);
      List<Adaptation<?>> page = new ArrayList<>();
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.isEnabled()) {
          profile.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation, adaptation.getMaxLevel());
          page.add(adaptation);
        }
      }
      assertSwitchGovernsPage(skill, profile, player, page);
    }
  }

  @Test
  void everyAdaptationOnEveryMutatedPageStopsWithThatPagesSwitchAndReturnsWithIt() {
    int mutations = 0;
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner) {
          continue;
        }
        FakePlayer player = player();
        AdaptPlayer profile = adapt.join(player);
        MutatedSkill mutated = mutate(profile, lead, partner);
        assertSwitchGovernsPage(mutated, profile, player, learnMutatedPage(profile, mutated));
        mutations++;
      }
    }
    assertThat(mutations).isEqualTo(registry.getSkills().size() * (registry.getSkills().size() - 1));
  }

  @Test
  void aRiftMutationsKeptPassiveLosesItsArmorWhenThePageSwitchGoesOffAndRegainsItWhenItComesBack() {
    FakePlayer player = player();
    AdaptPlayer profile = adapt.join(player);
    MutatedSkill mutated = mutate(profile, registry.getSkill("rift"), registry.getSkill("agility"));
    Adaptation<?> armorUp = mutated.findKept(AgilityArmorUp.ID.toString());
    Adaptation<?> conduit = mutated.findKept(RiftConduit.ID.toString());
    assertThat(armorUp).isNotNull();
    assertThat(conduit).isNotNull();
    assertThat(armorUp.getActiveLevel(player)).isPositive();
    assertThat(conduit.getActiveLevel(player)).isPositive();

    sprint(player);
    assertThat(armor(player)).as("kept Armor Up plates a sprinting player").isPositive();

    assertThat(PlayerPreferences.setSkillEnabled(mutated, profile, CommonPreferences.Toggle.OFF)).isTrue();
    assertThat(armorUp.getActiveLevel(player)).isZero();
    assertThat(conduit.getActiveLevel(player)).isZero();
    assertThat(player.modifiers(Attributes.ARMOR)).as("plating removed with the switch").isEmpty();
    sprint(player);
    assertThat(player.modifiers(Attributes.ARMOR)).as("no plating while the switch is off").isEmpty();

    assertThat(PlayerPreferences.setSkillEnabled(mutated, profile, CommonPreferences.Toggle.ON)).isTrue();
    assertThat(armorUp.getActiveLevel(player)).isPositive();
    assertThat(conduit.getActiveLevel(player)).isPositive();
    sprint(player);
    assertThat(armor(player)).as("plating returns with the switch").isPositive();
  }

  @Test
  void aRiftUpgradeOnTheMutatedPageStopsReleasingLevitationWhileThePageSwitchIsOff() {
    FakePlayer player = player();
    AdaptPlayer profile = adapt.join(player);
    MutatedSkill mutated = mutate(profile, registry.getSkill("rift"), registry.getSkill("stealth"));
    Adaptation<?> voidborn = SkillHost.of(registry.getSkill("rift")).getUpgrades().stream()
        .filter((Adaptation<?> adaptation) -> adaptation.getId().equals(Voidborn.ID)).findFirst().orElseThrow();
    profile.getData().getSkillLine("rift").setAdaptation(voidborn, 1);
    assertThat(voidborn.getActiveLevel(player)).isPositive();

    assertThat(PlayerPreferences.setSkillEnabled(mutated, profile, CommonPreferences.Toggle.OFF)).isTrue();
    levitate(player);
    sneak(player);
    assertThat(player.hasEffect(RiftDescent.LEVITATION)).as("switch off keeps the levitation").isTrue();

    assertThat(PlayerPreferences.setSkillEnabled(mutated, profile, CommonPreferences.Toggle.ON)).isTrue();
    sneak(player);
    assertThat(player.hasEffect(RiftDescent.LEVITATION)).as("switch on releases it").isFalse();
  }

  @Test
  void anAdaptationsOwnToggleStillWorksAloneUnderAnOnSwitch() {
    FakePlayer player = player();
    AdaptPlayer profile = adapt.join(player);
    MutatedSkill mutated = mutate(profile, registry.getSkill("rift"), registry.getSkill("agility"));
    Adaptation<?> armorUp = mutated.findKept(AgilityArmorUp.ID.toString());
    Adaptation<?> conduit = mutated.findKept(RiftConduit.ID.toString());

    assertThat(PlayerPreferences.set(armorUp, profile, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF))
        .isTrue();
    assertThat(armorUp.getActiveLevel(player)).isZero();
    assertThat(conduit.getActiveLevel(player)).isPositive();

    assertThat(PlayerPreferences.setSkillEnabled(mutated, profile, CommonPreferences.Toggle.OFF)).isTrue();
    assertThat(PlayerPreferences.setSkillEnabled(mutated, profile, CommonPreferences.Toggle.ON)).isTrue();
    assertThat(armorUp.getActiveLevel(player)).as("own toggle outlives the switch").isZero();
    assertThat(conduit.getActiveLevel(player)).isPositive();

    assertThat(PlayerPreferences.set(armorUp, profile, CommonPreferences.ENABLED, CommonPreferences.Toggle.ON))
        .isTrue();
    assertThat(armorUp.getActiveLevel(player)).isPositive();
  }

  private static void assertSwitchGovernsPage(Skill<?> skill, AdaptPlayer profile, FakePlayer player,
                                              List<Adaptation<?>> page) {
    assertThat(page).as("%s shows adaptations", skill.getId()).isNotEmpty();
    for (Adaptation<?> adaptation : page) {
      assertThat(adaptation.getActiveLevel(player)).as("%s on %s before the switch", adaptation.getId(),
          SkillHost.lineKey(skill)).isPositive();
    }
    assertThat(PlayerPreferences.setSkillEnabled(skill, profile, CommonPreferences.Toggle.OFF))
        .as("%s switch off", SkillHost.lineKey(skill)).isTrue();
    stir(player);
    for (Adaptation<?> adaptation : page) {
      assertThat(adaptation.getActiveLevel(player)).as("%s on %s with the switch off", adaptation.getId(),
          SkillHost.lineKey(skill)).isZero();
      assertThat(adaptation.isPlayerEnabled(player)).as("%s enabled with the switch off", adaptation.getId())
          .isFalse();
    }
    assertThat(player.effects()).as("effects on %s with the switch off", SkillHost.lineKey(skill)).isEmpty();
    for (Id attribute : Attributes.ALL) {
      assertThat(player.modifiers(attribute)).as("%s modifiers on %s with the switch off", attribute,
          SkillHost.lineKey(skill)).isEmpty();
    }
    assertThat(PlayerPreferences.setSkillEnabled(skill, profile, CommonPreferences.Toggle.ON))
        .as("%s switch on", SkillHost.lineKey(skill)).isTrue();
    for (Adaptation<?> adaptation : page) {
      assertThat(adaptation.getActiveLevel(player)).as("%s on %s after the switch returns", adaptation.getId(),
          SkillHost.lineKey(skill)).isPositive();
    }
  }

  /** Learns every upgrade and pair adaptation the mutated page lists, and returns them with the originals it keeps. */
  private static List<Adaptation<?>> learnMutatedPage(AdaptPlayer profile, MutatedSkill mutated) {
    List<Adaptation<?>> page = new ArrayList<>();
    for (Adaptation<?> adaptation : SkillHost.of(mutated).adaptations()) {
      if (!adaptation.isEnabled() || adaptation instanceof MutationBlock<?> || adaptation instanceof Signature<?>) {
        continue;
      }
      PlayerSkillLine line = profile.getData().getSkillLine(mutated.lineKey());
      line.setAdaptation(adaptation, adaptation instanceof MutationAdaptation ? 1 : adaptation.getMaxLevel());
      page.add(adaptation);
    }
    page.addAll(mutated.getKept());
    return page;
  }

  private static MutatedSkill mutate(AdaptPlayer profile, Skill<?> lead, Skill<?> partner) {
    profile.getData().setMasterXp(1_000_000D);
    for (Skill<?> skill : List.of(lead, partner)) {
      PlayerSkillLine line = profile.getData().getSkillLine(SkillHost.lineKey(skill));
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    SkillMutations.Result result = SkillMutations.mutate(profile.getData(), lead, partner, "Tester");
    assertThat(result).as("%s + %s", lead.getId(), partner.getId()).isEqualTo(SkillMutations.Result.MUTATED);
    return registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId()));
  }

  private static FakePlayer player() {
    return platform.player("Switch" + players++, 0.5D, 61D, 0.5D);
  }

  /** Moves, sprints and sneaks the player and lets a few ticks pass, so event and ticking passives get their chance. */
  private static void stir(FakePlayer player) {
    sprint(player);
    sneak(player);
    player.setSprinting(false);
    adapt.post(new PlayerToggleSprintEvent(player, false));
  }

  private static void sprint(FakePlayer player) {
    player.setSprinting(true);
    adapt.post(new PlayerToggleSprintEvent(player, true));
    long until = System.currentTimeMillis() + 250L;
    adapt.awaitTicks(() -> {
      Loc from = player.location();
      Loc to = from.add(0.01D, 0D, 0D);
      player.moveTo(to);
      adapt.post(new PlayerMoveEvent(player, from, to));
      return System.currentTimeMillis() >= until;
    }, 5_000L);
  }

  private static void sneak(FakePlayer player) {
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));
  }

  private static void levitate(FakePlayer player) {
    player.addEffect(EffectInstance.of(RiftDescent.LEVITATION, 200, 0));
  }

  private static double armor(FakePlayer player) {
    adapt.awaitTicks(() -> !player.modifiers(Attributes.ARMOR).isEmpty(), 5_000L);
    return player.attributeValue(Attributes.ARMOR) - player.attributeBase(Attributes.ARMOR);
  }
}
