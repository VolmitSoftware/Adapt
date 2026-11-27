package art.arcane.adapt.content.adaptation.hunter.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.adaptation.hunter.HunterRamp;
import art.arcane.adapt.content.adaptation.hunter.HunterSnares;
import art.arcane.adapt.content.adaptation.hunter.HunterStrengthBuff;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HunterUpgradesRuntimeTest {
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id HUNGER = Id.minecraft("hunger");

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
    player = platform.player("Predator", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void upgradesDoNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("hunter").setAdaptation(upgrade(ApexPredator.class), 1);
    profile.getData().getSkillLine("hunter").setAdaptation(upgrade(BloodFrenzy.class), 1);
    FakeLiving target = platform.world().add(mob("zombie", 2.5D));
    double attack = player.attributeValue(Attributes.ATTACK_DAMAGE);

    hit(target, 10D);
    assertThat(hit(target, 10D).damage()).isEqualTo(10D);
    adapt.post(hurt());

    assertThat(player.hasEffect(REGENERATION)).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_DAMAGE)).isEqualTo(attack);
  }

  @Test
  void bloodFrenzyGrantsStrengthAndRegenerationScaledByLowHealthForOneHungerCost() {
    mutate();
    BloodFrenzy frenzy = learn(BloodFrenzy.class, 1);
    player.setHealth(player.maxHealth() * 0.2D);
    double attack = player.attributeValue(Attributes.ATTACK_DAMAGE);
    double scale = frenzy.getScale(1, 0.2D);

    adapt.post(hurt());
    platform.scheduler().advance(2);

    assertThat(scale).isGreaterThan(1D);
    assertThat(player.attributeValue(Attributes.ATTACK_DAMAGE) - attack)
        .isCloseTo(HunterStrengthBuff.strengthDamageBonus(1) * scale, within(1.0E-9D));
    EffectInstance regeneration = player.effect(REGENERATION);
    assertThat(regeneration).isNotNull();
    assertThat(regeneration.durationTicks()).isEqualTo((int) Math.round(frenzy.getConfig().regenTicksPerLevel * scale));
    EffectInstance hunger = player.effect(HUNGER);
    assertThat(hunger).isNotNull();
    assertThat(hunger.durationTicks()).isEqualTo(frenzy.getConfig().hungerDurationTicks);
  }

  @Test
  void apexPredatorRampsTwiceAsFastOnBigGame() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 1);
    FakeLiving zombie = platform.world().add(mob("zombie", 2.5D));
    FakeLiving ravager = platform.world().add(mob("ravager", -1.5D));

    assertThat(hit(zombie, 10D).damage()).isEqualTo(10D);
    double zombieSecond = hit(zombie, 10D).damage();
    hit(ravager, 10D);
    double ravagerSecond = hit(ravager, 10D).damage();

    double perStack = apex.getPerStackBonus(1);
    assertThat(zombieSecond).isCloseTo(10D * HunterRamp.damageMultiplier(2, perStack), within(1.0E-9D));
    assertThat(ravagerSecond).isCloseTo(10D * HunterRamp.damageMultiplier(3, perStack), within(1.0E-9D));
  }

  @Test
  void apexPredatorKeepsItsRampThroughSwordSweepHits() {
    mutate();
    ApexPredator apex = learn(ApexPredator.class, 1);
    FakeLiving zombie = platform.world().add(mob("zombie", 2.5D));
    FakeLiving bystander = platform.world().add(mob("zombie", 3.5D));

    hit(zombie, 10D);
    hit(zombie, 10D);
    double swept = adapt.post(new EntityDamageByEntityEvent(bystander, player, player, DamageCause.ENTITY_SWEEP_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY)).damage();
    double third = hit(zombie, 10D).damage();

    assertThat(swept).isEqualTo(1D);
    assertThat(third).isCloseTo(10D * HunterRamp.damageMultiplier(3, apex.getPerStackBonus(1)), within(1.0E-9D));
  }

  @Test
  void stalkersTrailMarksWoundedPreyAndRootsMobsOnItsSnares() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    FakeLiving prey = platform.world().add(mob("zombie", 2.5D));
    prey.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    prey.setHealth(12D);

    hit(prey, 4D);
    assertThat(trail.hasTickDemand()).isTrue();

    ItemRef snareItem = HunterSnares.buildSnareItem(2);
    player.holding(snareItem);
    PlayerInteractEvent place = adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        snareItem, platform.world().blockAt(2, 60, 0), BlockFace.UP, null, null, null));
    trail.onTick();
    platform.scheduler().advance(2);

    assertThat(place.isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(1);
    assertThat(prey.modifiers(Attributes.MOVEMENT_SPEED)).isNotEmpty();
  }

  @Test
  void stalkersTrailDropsLargerTrophiesOnPreciseKills() {
    mutate();
    StalkersTrail trail = learn(StalkersTrail.class, 1);
    trail.getConfig().trophyChanceBase = 1D;
    player.setSneaking(true);
    FakeLiving prey = platform.world().add(mob("zombie", 2.5D));

    EntityDeathEvent death = adapt.post(new EntityDeathEvent(prey, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(), 0));

    assertThat(death.drops()).isEmpty();
    assertThat(player.inventory().contains(Id.minecraft("rotten_flesh"), trail.getTrophyAmount(1))).isTrue();
  }

  @Test
  void stalkersTrailDropsNoTrophiesFromSpawnerMobs() {
    mutate();
    learn(StalkersTrail.class, 1).getConfig().trophyChanceBase = 1D;
    player.setSneaking(true);
    FakeLiving prey = platform.world().add(mob("zombie", 2.5D));
    MobOrigin.record(prey, CreatureSpawnEvent.Reason.SPAWNER);

    EntityDeathEvent death = adapt.post(new EntityDeathEvent(prey, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(), 0));

    assertThat(death.drops()).isEmpty();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("hunter", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("hunter"), registry.getSkill("stealth"), "Predator"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("hunter").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("hunter")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeLiving mob(String type, double x) {
    FakeLiving mob = new FakeLiving(Id.minecraft(type), new Loc(platform.world(), x, 61D, 0.5D));
    mob.trait(EntityTrait.MONSTER);
    mob.trait(EntityTrait.ENEMY);
    return mob;
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }

  private EntityDamageEvent hurt() {
    return new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 4D, base -> base);
  }
}
