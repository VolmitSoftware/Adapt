package art.arcane.adapt.content.adaptation.blocking.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BlockingUpgradesRuntimeTest {
  private static final Id SHIELD = Id.minecraft("shield");

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
    player = platform.player("Guard", 0.5D, 61D, 0.5D);
    player.inventory().setOffHand(FakeItem.tool("shield", 336));
    adapt = TestAdapt.start(platform, List.of(SkillBlocking.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void mirrorGuardDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("blocking").setAdaptation(upgrade(MirrorGuard.class), 1);
    FakeLiving attacker = zombieAt(0.5D, 2.5D);

    raiseShield();
    EntityDamageByEntityEvent hit = melee(attacker, 6D);

    assertThat(hit.isCancelled()).isFalse();
    assertThat(hit.damage()).isEqualTo(6D);
  }

  @Test
  void mirrorGuardNegatesATimedMeleeHitAndDealsItBack() {
    mutate();
    MirrorGuard guard = learn(MirrorGuard.class, 1);
    FakeLiving attacker = zombieAt(0.5D, 2.5D);

    raiseShield();
    EntityDamageByEntityEvent hit = melee(attacker, 6D);
    platform.scheduler().advance(2);

    assertThat(hit.isCancelled()).isTrue();
    assertThat(attacker.damageTaken()).isCloseTo(6D * guard.getReflectShare(1), within(1.0E-9D));
  }

  @Test
  void mirrorGuardIgnoresAHitOutsideTheTimingWindow() {
    mutate();
    learn(MirrorGuard.class, 1);
    FakeLiving attacker = zombieAt(0.5D, 2.5D);

    EntityDamageByEntityEvent hit = melee(attacker, 6D);

    assertThat(hit.isCancelled()).isFalse();
    assertThat(attacker.damageTaken()).isZero();
  }

  @Test
  void mirrorGuardSendsATimedProjectileBackAtItsShooter() {
    mutate();
    learn(MirrorGuard.class, 1);
    FakeLiving shooter = zombieAt(0.5D, 8.5D);
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 0.5D, 62.5D, 1.5D)));
    arrow.setShooter(shooter);
    arrow.setVelocity(new Vec3(0, 0, -2D));

    raiseShield();
    EntityDamageByEntityEvent hit = adapt.post(new EntityDamageByEntityEvent(player, arrow, shooter,
        DamageCause.PROJECTILE, Id.minecraft("arrow"), 5D, false, FinalDamage.IDENTITY));
    platform.scheduler().advance(2);

    assertThat(hit.isCancelled()).isTrue();
    assertThat(arrow.shooter()).isSameAs(player);
    assertThat(arrow.velocity().z()).isPositive();
  }

  @Test
  void livingWallBracesFullyAgainstKnockbackWhileTheShieldIsRaised() {
    mutate();
    learn(LivingWall.class, 1);
    double base = player.attributeValue(Attributes.KNOCKBACK_RESISTANCE);
    player.setBlocking(true);

    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(0.1D, 0, 0)));
    platform.scheduler().advance(2);
    assertThat(player.attributeValue(Attributes.KNOCKBACK_RESISTANCE)).isEqualTo(base + 1D);

    player.setBlocking(false);
    platform.scheduler().advance(3);
    assertThat(player.attributeValue(Attributes.KNOCKBACK_RESISTANCE)).isEqualTo(base);
  }

  @Test
  void temperedAegisRepairsShieldThenArmorUpToTheWearOfANaturalBlockedHit() {
    mutate();
    TemperedAegis aegis = learn(TemperedAegis.class, 1);
    ItemRef shield = player.inventory().offHand();
    shield.setDamage(100);
    player.inventory().setOffHand(shield);
    ItemRef chestplate = FakeItem.tool("iron_chestplate", 240);
    chestplate.setDamage(50);
    player.inventory().setArmor(EquipmentSlot.CHEST, chestplate);
    player.setBlocking(true);
    int wear = aegis.getShieldRepair(1) + 1;

    melee(zombieAt(0.5D, 2.5D), 6D);
    adapt.post(new PlayerItemDamageEvent(player, player.inventory().offHand(), wear, wear));
    platform.scheduler().advance(2);

    assertThat(player.inventory().offHand().damage()).isEqualTo(100 - aegis.getShieldRepair(1));
    assertThat(player.inventory().armor(EquipmentSlot.CHEST).damage()).isEqualTo(49);
  }

  @Test
  void temperedAegisNeverRepairsMoreThanTheHitWoreAndPaysNoXpForIt() {
    mutate();
    TemperedAegis aegis = learn(TemperedAegis.class, 3);
    ItemRef shield = player.inventory().offHand();
    shield.setDamage(100);
    player.inventory().setOffHand(shield);
    ItemRef chestplate = FakeItem.tool("iron_chestplate", 240);
    chestplate.setDamage(50);
    player.inventory().setArmor(EquipmentSlot.CHEST, chestplate);
    player.setBlocking(true);

    melee(zombieAt(0.5D, 2.5D), 6D);
    adapt.post(new PlayerItemDamageEvent(player, player.inventory().offHand(), 2, 2));
    platform.scheduler().advance(2);

    assertThat(aegis.getShieldRepair(3)).isGreaterThan(2);
    assertThat(player.inventory().offHand().damage()).isEqualTo(98);
    assertThat(player.inventory().armor(EquipmentSlot.CHEST).damage()).isEqualTo(50);
    assertThat(XpPaid.by(profile, aegis)).isZero();
  }

  @Test
  void temperedAegisRepairsNothingWithoutABlockedHitFromAFoe() {
    mutate();
    learn(TemperedAegis.class, 1);
    ItemRef shield = player.inventory().offHand();
    shield.setDamage(100);
    player.inventory().setOffHand(shield);

    adapt.post(new PlayerItemDamageEvent(player, player.inventory().offHand(), 3, 3));
    platform.scheduler().advance(2);

    assertThat(player.inventory().offHand().damage()).isEqualTo(100);
  }

  @Test
  void temperedAegisRepairsOnlyOnceForOneBlockedHit() {
    mutate();
    learn(TemperedAegis.class, 1);
    ItemRef shield = player.inventory().offHand();
    shield.setDamage(100);
    player.inventory().setOffHand(shield);
    player.setBlocking(true);

    melee(zombieAt(0.5D, 2.5D), 6D);
    adapt.post(new PlayerItemDamageEvent(player, player.inventory().offHand(), 1, 1));
    adapt.post(new PlayerItemDamageEvent(player, player.inventory().offHand(), 1, 1));
    platform.scheduler().advance(2);

    assertThat(player.inventory().offHand().damage()).isEqualTo(99);
  }

  @Test
  void temperedAegisRepairsNothingWhenASpawnerMobHitsTheShield() {
    mutate();
    learn(TemperedAegis.class, 1);
    ItemRef shield = player.inventory().offHand();
    shield.setDamage(100);
    player.inventory().setOffHand(shield);
    FakeLiving attacker = zombieAt(0.5D, 2.5D);
    MobOrigin.record(attacker, CreatureSpawnEvent.Reason.SPAWNER);
    player.setBlocking(true);

    melee(attacker, 6D);
    adapt.post(new PlayerItemDamageEvent(player, player.inventory().offHand(), 3, 3));
    platform.scheduler().advance(2);

    assertThat(player.inventory().offHand().damage()).isEqualTo(100);
  }

  @Test
  void temperedAegisClearsTheShieldDisableAfterAnAxeBlow() {
    mutate();
    learn(TemperedAegis.class, 1);
    FakeLiving attacker = zombieAt(0.5D, 2.5D);
    attacker.setEquipment(EquipmentSlot.MAIN_HAND, FakeItem.tool("iron_axe", 250));
    player.setBlocking(true);

    melee(attacker, 6D);
    player.setCooldown(SHIELD, 100);
    platform.scheduler().advance(3);

    assertThat(player.cooldownTicks(SHIELD)).isZero();
  }

  @Test
  void temperedAegisLeavesTheDisableForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("blocking").setAdaptation(upgrade(TemperedAegis.class), 1);
    FakeLiving attacker = zombieAt(0.5D, 2.5D);
    attacker.setEquipment(EquipmentSlot.MAIN_HAND, FakeItem.tool("iron_axe", 250));
    player.setBlocking(true);

    melee(attacker, 6D);
    player.setCooldown(SHIELD, 100);
    platform.scheduler().advance(3);

    assertThat(player.cooldownTicks(SHIELD)).isEqualTo(100);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("blocking", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("blocking"), registry.getSkill("stealth"), "Bulwark"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("blocking").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("blocking").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private void raiseShield() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, null, player.inventory().offHand(), null,
        null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private FakeLiving zombieAt(double x, double z) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return platform.world().add(zombie);
  }

  private EntityDamageByEntityEvent melee(FakeLiving attacker, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), damage, false, FinalDamage.IDENTITY));
  }
}
