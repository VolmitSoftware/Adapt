package art.arcane.adapt.content.adaptation.kinetics.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeKey;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMassShift;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class KineticsUpgradesRuntimeTest {
  private static final Id SLOWNESS = Id.minecraft("slowness");

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
    player = platform.player("Meteor", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillKinetics.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theMutatedSkillOffersAllThreeUpgrades() {
    mutate();

    assertThat(registry.getMutatedSkill("mutated-kinetics-stealth").getAdaptations()).extracting(Adaptation::getName)
        .startsWith("upgraded-kinetics-comet", "upgraded-kinetics-lancer", "upgraded-kinetics-featherweight",
            "upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy");
  }

  @Test
  void cometSmashShredsTheTargetAndHurlsAndShredsEverythingInTheWave() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.holding(FakeItem.tool("mace", 500));
    player.setFallDistance(6F);
    FakeLiving target = armored(zombie(1.5D));
    FakeLiving bystander = armored(zombie(4.0D));

    adapt.post(new EntityAttemptSmashAttackEvent(player, target, player.inventory().mainHand(), true, UseResult.DEFAULT));
    platform.scheduler().advance(2);

    assertThat(target.attributeValue(Attributes.ARMOR)).isCloseTo(10D - comet.getArmorShred(1), within(1.0E-9D));
    assertThat(bystander.attributeValue(Attributes.ARMOR)).isCloseTo(10D - comet.getArmorShred(1), within(1.0E-9D));
    assertThat(bystander.velocity().x()).isGreaterThan(0D);
  }

  @Test
  void cometShockwaveDamagesEverythingItHurls() {
    mutate();
    Comet comet = learn(Comet.class, 2);
    player.holding(FakeItem.tool("mace", 500));
    player.setFallDistance(6F);
    FakeLiving target = platform.world().add(zombie(1.5D));
    FakeLiving bystander = platform.world().add(zombie(4.0D));

    adapt.post(new EntityAttemptSmashAttackEvent(player, target, player.inventory().mainHand(), true, UseResult.DEFAULT));
    platform.scheduler().advance(2);

    assertThat(comet.getWaveDamage(2)).isGreaterThan(comet.getWaveDamage(1));
    assertThat(bystander.damageTaken()).isCloseTo(comet.getWaveDamage(2), within(1.0E-9D));
  }

  @Test
  void cometDivesPullTheFallingPlayerDown() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.holding(FakeItem.tool("mace", 500));
    player.setOnGround(false);
    player.setSneaking(true);
    player.setVelocity(new Vec3(0D, -0.5D, 0D));

    adapt.post(new PlayerMoveEvent(player, at(0.5D, 70D, 0.5D), at(0.5D, 69.5D, 0.5D)));

    assertThat(player.velocity().y()).isCloseTo(-0.5D - comet.getDownwardAcceleration(1), within(1.0E-9D));
  }

  @Test
  void cometTitanFormWidensAndHardensTheShockwaveForTwoHunger() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.holding(FakeItem.tool("mace", 500));
    player.setFoodLevel(20);
    player.setSaturation(0F);
    double scaleBefore = player.attributeValue(Attributes.SCALE);

    PlayerSwapHandItemsEvent swap = shiftForm(-60F);
    platform.scheduler().advance(1);

    assertThat(swap.isCancelled()).isTrue();
    assertThat(comet.form(player)).isEqualTo(KineticsMassShift.FORM_TITAN);
    assertThat(player.foodLevel()).isEqualTo(18);
    assertThat(player.attributeValue(Attributes.SCALE)).isCloseTo(scaleBefore + comet.getFormSize(1), within(1.0E-9D));

    double edge = 0.5D + comet.getRadius(1) + (comet.getTitanRadius(1) / 2D);
    FakeLiving target = platform.world().add(zombie(1.5D));
    FakeLiving far = platform.world().add(zombie(edge));
    player.setFallDistance(6F);
    adapt.post(new EntityAttemptSmashAttackEvent(player, target, player.inventory().mainHand(), true, UseResult.DEFAULT));
    platform.scheduler().advance(2);

    assertThat(far.damageTaken()).isCloseTo(comet.getWaveDamage(1) * (1D + comet.getFormSize(1)), within(1.0E-9D));
  }

  @Test
  void cometShockwaveKeepsItsRadiusOutsideTitanForm() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.holding(FakeItem.tool("mace", 500));
    FakeLiving target = platform.world().add(zombie(1.5D));
    FakeLiving far = platform.world().add(zombie(0.5D + comet.getRadius(1) + (comet.getTitanRadius(1) / 2D)));
    player.setFallDistance(6F);

    adapt.post(new EntityAttemptSmashAttackEvent(player, target, player.inventory().mainHand(), true, UseResult.DEFAULT));
    platform.scheduler().advance(2);

    assertThat(far.damageTaken()).isZero();
  }

  @Test
  void cometFormSwitchWaitsTenSecondsAndNeedsFood() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.setFoodLevel(20);
    player.setSaturation(0F);
    long start = System.currentTimeMillis();
    try (MockedStatic<M> clock = mockStatic(M.class, CALLS_REAL_METHODS)) {
      clock.when(M::ms).thenReturn(start);
      shiftForm(-60F);
      shiftForm(60F);

      assertThat(comet.form(player)).isEqualTo(KineticsMassShift.FORM_TITAN);
      assertThat(player.foodLevel()).isEqualTo(18);

      clock.when(M::ms).thenReturn(start + 10_001L);
      player.setFoodLevel(1);
      shiftForm(60F);

      assertThat(comet.form(player)).isEqualTo(KineticsMassShift.FORM_TITAN);
      assertThat(player.foodLevel()).isEqualTo(1);

      player.setFoodLevel(20);
      shiftForm(60F);

      assertThat(comet.form(player)).isEqualTo(KineticsMassShift.FORM_POCKET);
    }
  }

  @Test
  void cometFormSwitchesOnlyOnTheGround() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.setOnGround(false);

    shiftForm(-60F);

    assertThat(comet.form(player)).isEqualTo(KineticsMassShift.FORM_NORMAL);
  }

  @Test
  void cometPocketFormDivesFasterAndLandsLighterWithoutPayingXp() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.setFoodLevel(20);
    shiftForm(60F);
    double xpBefore = profile.getData().getSkillLine("kinetics").getXp();

    EntityDamageEvent fall = adapt.post(fall(10D));
    player.holding(FakeItem.tool("mace", 500));
    player.setOnGround(false);
    player.setSneaking(true);
    player.setVelocity(new Vec3(0D, -0.5D, 0D));
    adapt.post(new PlayerMoveEvent(player, at(0.5D, 70D, 0.5D), at(0.5D, 69.5D, 0.5D)));

    assertThat(comet.form(player)).isEqualTo(KineticsMassShift.FORM_POCKET);
    assertThat(fall.damage()).isCloseTo(10D * (1D - comet.getPocketLandingCut(1)), within(1.0E-9D));
    assertThat(profile.getData().getSkillLine("kinetics").getXp()).isEqualTo(xpBefore);
    assertThat(player.velocity().y())
        .isCloseTo(-0.5D - (comet.getDownwardAcceleration(1) * (1D + comet.getFormSize(1))), within(1.0E-9D));
  }

  @Test
  void cometFormIsKeptAndReappliedUntilSwitched() {
    mutate();
    Comet comet = learn(Comet.class, 2);
    player.setFoodLevel(20);
    double scaleBefore = player.attributeValue(Attributes.SCALE);
    shiftForm(60F);
    platform.scheduler().advance(1);
    AdaptAttributeService.get().removeAll(player, comet.getName());
    platform.scheduler().advance(1);

    comet.onPlayerPreferencesChanged(profile);
    platform.scheduler().advance(1);

    assertThat(player.attributeValue(Attributes.SCALE)).isCloseTo(scaleBefore - comet.getFormSize(2), within(1.0E-9D));
  }

  @Test
  void cometFormReplacesAKeptMassShiftFormInsteadOfStackingOnIt() {
    mutate();
    player.setFoodLevel(20);
    double scaleBefore = player.attributeValue(Attributes.SCALE);
    shiftForm(-60F);
    platform.scheduler().advance(1);
    assertThat(player.attributeValue(Attributes.SCALE)).isGreaterThan(scaleBefore);

    Comet comet = learn(Comet.class, 1);
    shiftForm(60F);
    platform.scheduler().advance(1);

    assertThat(player.attributeValue(Attributes.SCALE)).isCloseTo(scaleBefore - comet.getFormSize(1), within(1.0E-9D));
  }

  @Test
  void aKeptMassShiftStandsDownInTheAirWhileCometIsHeld() {
    mutate();
    learn(Comet.class, 1);
    player.setFoodLevel(20);
    player.setOnGround(false);
    double scaleBefore = player.attributeValue(Attributes.SCALE);
    double healthBefore = player.attributeValue(Attributes.MAX_HEALTH);

    shiftForm(-60F);
    platform.scheduler().advance(1);

    assertThat(player.attributeValue(Attributes.SCALE)).isCloseTo(scaleBefore, within(1.0E-9D));
    assertThat(player.attributeValue(Attributes.MAX_HEALTH)).isCloseTo(healthBefore, within(1.0E-9D));
  }

  @Test
  void aKeptMassShiftFormEndsOnceCometIsHeld() {
    mutate();
    player.setFoodLevel(20);
    double healthBefore = player.attributeValue(Attributes.MAX_HEALTH);
    shiftForm(-60F);
    platform.scheduler().advance(1);
    boolean shifted = player.attributeValue(Attributes.MAX_HEALTH) != healthBefore;

    learn(Comet.class, 1);
    KineticsMassShift.of(registry.getSkill("kinetics")).onTick();
    platform.scheduler().advance(1);

    assertThat(shifted).isTrue();
    assertThat(player.attributeValue(Attributes.MAX_HEALTH)).isCloseTo(healthBefore, within(1.0E-9D));
  }

  @Test
  void cometLeavesAHeldFormAloneAndRestoresOneThatWasStripped() {
    mutate();
    Comet comet = learn(Comet.class, 1);
    player.setFoodLevel(20);
    shiftForm(-60F);
    platform.scheduler().advance(1);
    Id key = AdaptAttributeKey.of(comet.getName(), "form-scale").key();
    AttributeMod held = player.modifier(Attributes.SCALE, key);

    comet.onPlayerPreferencesChanged(profile);
    platform.scheduler().advance(1);

    assertThat(held).isNotNull();
    assertThat(player.modifier(Attributes.SCALE, key)).isSameAs(held);
  }

  @Test
  void cometFormSwapIsLeftAloneWithoutAMutation() {
    profile.getData().getSkillLine("kinetics").setAdaptation(upgrade(Comet.class), 1);
    player.setFoodLevel(20);

    PlayerSwapHandItemsEvent swap = shiftForm(-60F);

    assertThat(swap.isCancelled()).isFalse();
    assertThat(player.foodLevel()).isEqualTo(20);
  }

  @Test
  void lancerReachesFartherScalesWithSpeedAndPins() {
    mutate();
    Lancer lancer = learn(Lancer.class, 1);
    player.holding(FakeItem.tool("iron_spear", 500));
    FakeLiving target = platform.world().add(zombie(2.5D));

    adapt.post(new PlayerItemHeldEvent(player, 0, 0));
    adapt.post(new PlayerMoveEvent(player, at(0.5D, 61D, 0.5D), at(1.0D, 61D, 0.5D)));
    double damage = hit(target, 10D).damage();
    platform.scheduler().advance(2);

    double bonus = Math.min(lancer.getBonusCap(1), 0.5D * lancer.getSpeedDamageFactor(1));
    assertThat(player.attributeValue(Attributes.ENTITY_INTERACTION_RANGE)).isCloseTo(1D + lancer.getReach(1), within(1.0E-9D));
    assertThat(damage).isCloseTo(10D * (1D + bonus), within(1.0E-9D));
    assertThat(target.effect(SLOWNESS)).isNotNull();
  }

  @Test
  void lancerDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("kinetics").setAdaptation(upgrade(Lancer.class), 1);
    player.holding(FakeItem.tool("iron_spear", 500));
    FakeLiving target = platform.world().add(zombie(2.5D));

    adapt.post(new PlayerMoveEvent(player, at(0.5D, 61D, 0.5D), at(1.0D, 61D, 0.5D)));
    double damage = hit(target, 10D).damage();
    platform.scheduler().advance(2);

    assertThat(damage).isEqualTo(10D);
    assertThat(target.effect(SLOWNESS)).isNull();
  }

  @Test
  void featherweightRaisesJumpsAndRemovesFallDamageOnAnyBlock() {
    mutate();
    learn(Featherweight.class, 1);
    double jumpBefore = player.attributeValue(Attributes.JUMP_STRENGTH);

    adapt.post(new PlayerJumpEvent(player, at(0.5D, 61D, 0.5D)));
    platform.scheduler().advance(1);
    EntityDamageEvent fall = adapt.post(fall(8D));

    assertThat(player.attributeValue(Attributes.JUMP_STRENGTH)).isGreaterThan(jumpBefore);
    assertThat(fall.isCancelled()).isTrue();
  }

  @Test
  void featherweightGivesSpringierSoles() {
    mutate();
    Featherweight featherweight = learn(Featherweight.class, 1);
    double before = player.attributeValue(Attributes.BOUNCINESS);

    featherweight.onPlayerPreferencesChanged(profile);

    assertThat(player.attributeValue(Attributes.BOUNCINESS)).isCloseTo(before + featherweight.getBounciness(1), within(1.0E-9D));
  }

  @Test
  void featherweightLeavesFallDamageAloneWithoutAMutation() {
    profile.getData().getSkillLine("kinetics").setAdaptation(upgrade(Featherweight.class), 1);

    EntityDamageEvent fall = adapt.post(fall(8D));

    assertThat(fall.isCancelled()).isFalse();
    assertThat(fall.damage()).isEqualTo(8D);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("kinetics", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("kinetics"), registry.getSkill("stealth"), "Meteor"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("kinetics").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("kinetics").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private Loc at(double x, double y, double z) {
    return new Loc(platform.world(), x, y, z);
  }

  private FakeLiving zombie(double x) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), at(x, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return zombie;
  }

  private FakeLiving armored(FakeLiving living) {
    living.setAttributeBase(Attributes.ARMOR, 10D);
    living.setAttributeBase(Attributes.ARMOR_TOUGHNESS, 4D);
    return platform.world().add(living);
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }

  private PlayerSwapHandItemsEvent shiftForm(float pitch) {
    player.moveTo(new Loc(platform.world(), 0.5D, 61D, 0.5D, 0F, pitch));
    player.setSneaking(true);
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private EntityDamageEvent fall(double damage) {
    return new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), damage, FinalDamage.IDENTITY);
  }
}
