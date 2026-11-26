package art.arcane.adapt.content.adaptation.sword.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.sword.SwordsBloodyBlade;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SwordUpgradesRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WITHER = Id.minecraft("wither");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Duelist", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void upgradesStayInertAndUnlearnableWithoutAMutation() {
    Witherblade witherblade = upgrade(Witherblade.class);
    profile.getData().getSkillLine("swords").setAdaptation(witherblade, 1);
    profile.getData().getSkillLine("swords").setKnowledge(100);

    assertThat(witherblade.getLevel(player)).isZero();
    assertThat(AdaptationLearningTransaction.learn(witherblade, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);
    assertThat(registry.getSkill("swords").getAdaptations()).doesNotContain(witherblade);
  }

  @Test
  void theMutatedSkillOffersTheUpgradesAndLearnsThemOnItsOwnLine() {
    mutate();
    PlayerData playerData = profile.getData();
    PlayerSkillLine line = playerData.getSkillLine("swords");
    line.setKnowledge(100);
    Witherblade witherblade = upgrade(Witherblade.class);
    MutatedSkill mutated = registry.getMutatedSkill("mutated-swords-stealth");

    assertThat(mutated.getAdaptations()).extracting(Adaptation::getName).startsWith(
        "upgraded-swords-witherblade", "upgraded-swords-tempest-flow", "upgraded-swords-duelists-verdict",
        "upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy");
    assertThat(List.of(witherblade.getCostFor(1), witherblade.getCostFor(2), witherblade.getCostFor(3)))
        .containsExactly(13, 13, 16);
    assertThat(AdaptationLearningTransaction.learn(witherblade, player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.LEARNED);

    assertThat(line.getLine()).isEqualTo("mutated-swords-stealth");
    assertThat(line.getKnowledge()).isEqualTo(87L);
    assertThat(playerData.getUsedPower()).isEqualTo(1);
    assertThat(witherblade.getLevel(player)).isEqualTo(1);
    assertThat(AdaptationLearningTransaction.learn(original(SwordsBloodyBlade.class), player, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);
  }

  @Test
  void resetSplitsTheUpgradeRefundBetweenBothParents() {
    mutate();
    PlayerSkillLine line = profile.getData().getSkillLine("swords");
    line.setKnowledge(100);
    AdaptationLearningTransaction.learn(upgrade(Witherblade.class), player, 2, false);

    SkillMutations.unmutateAll(profile.getData(), "Reset");

    assertThat(profile.getData().getSkillLine("swords").getKnowledge()).isEqualTo(50L);
    assertThat(profile.getData().getSkillLine("stealth").getKnowledge()).isEqualTo(50L);
  }

  @Test
  void witherbladeWithersAndBleedsAndStopsTheTargetHealing() {
    mutate();
    learn(Witherblade.class, 1);
    FakeLiving target = zombie();

    hit(target, 4D, false);
    platform.scheduler().advance(6);

    assertThat(target.effect(WITHER)).isNotNull();
    assertThat(target.lastDamageType()).isEqualTo(MAGIC);
    assertThat(target.lastDamageSource()).isSameAs(player);
    EntityRegainHealthEvent heal = adapt.post(new EntityRegainHealthEvent(target, 2D, EntityRegainHealthEvent.Reason.REGEN));
    assertThat(heal.isCancelled()).isTrue();
  }

  @Test
  void duelistsVerdictRaisesDuelHitsAndAddsTheFinishingBonus() {
    mutate();
    DuelistsVerdict verdict = learn(DuelistsVerdict.class, 1);
    FakeLiving target = platform.world().add(zombie());
    target.setAttributeBase(Attributes.MAX_HEALTH, 20D);

    double duel = hit(target, 10D, false).damage();
    target.setHealth(target.maxHealth() * 0.2D);
    double finishing = hit(target, 10D, false).damage();

    assertThat(duel).isCloseTo(10D * (1D + verdict.getBonusDamage(1)), within(1.0E-9D));
    assertThat(finishing).isCloseTo(10D * (1D + verdict.getBonusDamage(1) + verdict.getFinishingBonus(1)), within(1.0E-9D));
  }

  @Test
  void duelistsVerdictDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("swords").setAdaptation(upgrade(DuelistsVerdict.class), 1);
    FakeLiving target = platform.world().add(zombie());

    assertThat(hit(target, 10D, false).damage()).isEqualTo(10D);
  }

  @Test
  void tempestFlowReleasesTheSlashOnACritAtFullStacks() {
    mutate();
    TempestFlow tempest = learn(TempestFlow.class, 1);
    FakeLiving target = zombie();

    assertThat(hit(target, 5D, true).damage()).isEqualTo(5D);
    for (int stack = 1; stack < tempest.getStackCap(1); stack++) {
      hit(target, 5D, false);
    }
    double released = hit(target, 5D, true).damage();

    assertThat(released).isCloseTo(5D + tempest.getSlashDamage(1), within(1.0E-9D));
  }

  @Test
  void stopHealingKeepsTheLongerWoundAndBlocksHealingWhileItLasts() {
    Witherblade witherblade = upgrade(Witherblade.class);
    FakeLiving target = zombie();

    witherblade.stopHealing(target, 60_000L);
    witherblade.stopHealing(target, 0L);

    assertThat(adapt.post(new EntityRegainHealthEvent(target, 2D, EntityRegainHealthEvent.Reason.REGEN)).isCancelled())
        .isTrue();
    FakeLiving other = zombie();
    witherblade.stopHealing(other, 0L);
    assertThat(adapt.post(new EntityRegainHealthEvent(other, 2D, EntityRegainHealthEvent.Reason.REGEN)).isCancelled())
        .isFalse();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("swords", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("swords"), registry.getSkill("stealth"), "Duelist"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("swords").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("swords").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private <A extends Adaptation<?>> A original(Class<A> type) {
    return registry.getSkill("swords").getAdaptations().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return zombie;
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage, boolean critical) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, critical, FinalDamage.IDENTITY));
  }
}
