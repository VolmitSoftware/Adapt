package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RangedUpgradesRuntimeTest {
  private static final Id COBWEB = Id.minecraft("cobweb");
  private static final Id LEVITATION = Id.minecraft("levitation");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Marksman", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("bow", 384));
    adapt = TestAdapt.start(platform, List.of(SkillRanged.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void stormshotSpeedsArrowsUpPiercesAndHitsHarder() {
    mutate();
    Stormshot stormshot = learn(Stormshot.class, 1);
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1D));

    adapt.post(new ProjectileLaunchEvent(arrow));
    double damage = hit(zombie(0.5D, 5.5D), arrow, 10D).damage();

    assertThat(arrow.velocity().length()).isCloseTo(1D + stormshot.getSpeedBonus(1), within(1.0E-9D));
    assertThat(arrow.pierceLevel()).isEqualTo(stormshot.getPierce(1));
    assertThat(damage).isCloseTo(10D * (1D + stormshot.getDamageBonus(1)), within(1.0E-9D));
  }

  @Test
  void stormshotDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("ranged").setAdaptation(upgrade(Stormshot.class), 1);
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1D));

    adapt.post(new ProjectileLaunchEvent(arrow));
    double damage = hit(zombie(0.5D, 5.5D), arrow, 10D).damage();

    assertThat(arrow.velocity().length()).isCloseTo(1D, within(1.0E-9D));
    assertThat(arrow.pierceLevel()).isZero();
    assertThat(damage).isEqualTo(10D);
  }

  @Test
  void huntersWebCagesThePinnedTargetInWebsAndLiftsIt() {
    mutate();
    learn(HuntersWeb.class, 1);
    FakeLiving target = zombie(0.5D, 5.5D);
    double speed = target.attributeValue(Attributes.MOVEMENT_SPEED);
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1D));

    shoot(arrow, tool(SnareArrow.ID), true);
    hit(target, arrow, 4D);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(0, 62, 5).type()).isEqualTo(COBWEB);
    assertThat(world.blockAt(0, 63, 5).type()).isEqualTo(COBWEB);
    assertThat(target.effect(LEVITATION)).isNotNull();
    assertThat(target.attributeValue(Attributes.MOVEMENT_SPEED)).isLessThan(speed);
  }

  @Test
  void huntersWebIgnoresSnowballs() {
    mutate();
    learn(HuntersWeb.class, 1);
    FakeLiving target = zombie(0.5D, 5.5D);
    FakeProjectile snowball = world.add(new FakeProjectile(Id.minecraft("snowball"), new Loc(world, 0.5D, 62.5D, 1.5D)));
    snowball.setShooter(player);

    adapt.post(new ProjectileLaunchEvent(snowball));
    hit(target, snowball, 0.5D);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(0, 62, 5).type()).isNotEqualTo(COBWEB);
    assertThat(target.effect(LEVITATION)).isNull();
  }

  @Test
  void huntersWebPaysForCagingANaturalMobButNotASpawnerOne() {
    mutate();
    HuntersWeb web = learn(HuntersWeb.class, 1);
    FakeLiving spawned = zombie(0.5D, 5.5D);
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);
    FakeProjectile first = arrow(new Vec3(0D, 0D, 1D));

    shoot(first, tool(SnareArrow.ID), true);
    hit(spawned, first, 4D);
    platform.scheduler().advance(1);
    assertThat(spawned.effect(LEVITATION)).isNotNull();
    assertThat(XpPaid.by(profile, web)).isZero();

    FakeLiving natural = zombie(3.5D, 5.5D);
    FakeProjectile second = arrow(new Vec3(0D, 0D, 1D));
    shoot(second, tool(SnareArrow.ID), true);
    hit(natural, second, 4D);
    platform.scheduler().advance(1);
    assertThat(XpPaid.by(profile, web)).isPositive();
  }

  @Test
  void trueflightCurvesALockedArrowOntoItsTarget() {
    mutate();
    Trueflight trueflight = learn(Trueflight.class, 1);
    zombie(0.5D, 8.5D);
    lockOn();
    FakeProjectile arrow = arrow(new Vec3(1.5D, 0D, 0D));
    arrow.moveTo(new Loc(world, 0.5D, 62.2D, 6.8D));

    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), FakeItem.of("arrow"), arrow,
        Hand.MAIN_HAND, 1F, true));

    for (int tick = 0; tick < 4; tick++) {
      trueflight.onTick();
      platform.scheduler().advance(1);
    }

    assertThat(arrow.velocity().z()).isGreaterThan(0.5D);
  }

  @Test
  void trueflightBouncesOnToASecondTargetWithAnArrowNobodyCanPickUp() {
    mutate();
    learn(Trueflight.class, 1);
    FakeLiving first = zombie(0.5D, 8.5D);
    FakeLiving second = zombie(4.5D, 8.5D);
    lockOn();
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1.5D));
    shoot(arrow, tool(RicochetArrow.ID), true);

    hit(first, arrow, 4D);

    FakeProjectile bounced = bouncedArrow(arrow);
    assertThat(bounced).isNotNull();
    assertThat(bounced.velocity().x()).isPositive();
    assertThat(second.location().x()).isGreaterThan(first.location().x());
    assertThat(bounced.canPickup()).isFalse();
  }

  @Test
  void trueflightsBouncedArrowKeepsStormshotsDamageAndPierce() {
    mutate();
    Stormshot stormshot = learn(Stormshot.class, 1);
    learn(Trueflight.class, 1);
    FakeLiving first = zombie(0.5D, 8.5D);
    FakeLiving second = zombie(4.5D, 8.5D);
    lockOn();
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1.5D));
    adapt.post(new ProjectileLaunchEvent(arrow));
    shoot(arrow, tool(RicochetArrow.ID), true);
    hit(first, arrow, 4D);
    FakeProjectile bounced = bouncedArrow(arrow);

    double damage = hit(second, bounced, 10D).damage();

    assertThat(bounced.pierceLevel()).isEqualTo(stormshot.getPierce(1));
    assertThat(damage).isGreaterThanOrEqualTo(10D * (1D + stormshot.getDamageBonus(1)));
  }

  @Test
  void trueflightDoesNotBounceWithoutAMutation() {
    profile.getData().getSkillLine("ranged").setAdaptation(upgrade(Trueflight.class), 1);
    FakeLiving first = zombie(0.5D, 8.5D);
    zombie(4.5D, 8.5D);
    lockOn();
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1.5D));
    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), FakeItem.of("arrow"), arrow,
        Hand.MAIN_HAND, 1F, true));

    hit(first, arrow, 4D);

    assertThat(bouncedArrow(arrow)).isNull();
  }

  @Test
  void huntersWebCagesNothingFromAPlainArrowOrAnUnspentSnareArrow() {
    mutate();
    learn(HuntersWeb.class, 1);
    FakeLiving target = zombie(0.5D, 5.5D);
    FakeProjectile plain = arrow(new Vec3(0D, 0D, 1D));
    shoot(plain, FakeItem.of("arrow"), true);
    hit(target, plain, 4D);
    FakeProjectile infinite = arrow(new Vec3(0D, 0D, 1D));
    shoot(infinite, tool(SnareArrow.ID), false);
    hit(target, infinite, 4D);
    platform.scheduler().advance(1);

    assertThat(world.blockAt(0, 62, 5).type()).isNotEqualTo(COBWEB);
    assertThat(target.effect(LEVITATION)).isNull();
  }

  @Test
  void trueflightDoesNotBounceAPlainArrow() {
    mutate();
    learn(Trueflight.class, 1);
    FakeLiving first = zombie(0.5D, 8.5D);
    zombie(4.5D, 8.5D);
    lockOn();
    FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1.5D));
    shoot(arrow, FakeItem.of("arrow"), true);

    hit(first, arrow, 4D);

    assertThat(bouncedArrow(arrow)).isNull();
  }

  @Test
  void theQuiverStoresLooseArrowsUpToItsCapacityAndHandsBackAStack() {
    mutate();
    Stormshot stormshot = learn(Stormshot.class, 1);
    ItemRef quiver = tool(Quiver.ID);
    player.holding(quiver);
    player.inventory().setItem(1, new FakeItem(Id.minecraft("arrow"), 64));
    player.inventory().setItem(2, new FakeItem(Id.minecraft("arrow"), 64));
    player.inventory().setItem(3, new FakeItem(Id.minecraft("arrow"), 22));
    player.inventory().setItem(4, tool(SnareArrow.ID).copyWithAmount(10));

    useHeld();
    int capacity = ((Quiver) MutationTools.find(Quiver.ID)).capacity(1);
    Quiver.Data stored = quiverData();

    assertThat(capacity).isEqualTo(stormshot.getQuiverStacks(1) * 64).isEqualTo(128);
    assertThat(stored.count(Quiver.Kind.ARROW)).isEqualTo(128);
    assertThat(loose(Id.minecraft("arrow"))).isEqualTo(22);
    assertThat(countSnares()).isEqualTo(10);

    player.setSneaking(true);
    useHeld();

    assertThat(quiverData().count(Quiver.Kind.ARROW)).isEqualTo(64);
    assertThat(loose(Id.minecraft("arrow"))).isEqualTo(86);
  }

  @Test
  void theQuiverIsInertWithoutStormshot() {
    profile.getData().getSkillLine("ranged").setAdaptation(upgrade(Stormshot.class), 1);
    player.holding(tool(Quiver.ID));
    player.inventory().setItem(1, new FakeItem(Id.minecraft("arrow"), 20));

    PlayerInteractEvent use = useHeld();

    assertThat(use.useItem()).isEqualTo(UseResult.DENY);
    assertThat(quiverData().stored()).isZero();
    assertThat(loose(Id.minecraft("arrow"))).isEqualTo(20);
  }

  @Test
  void theQuiverTakesBackAShareOfArrowsThatHit() {
    mutate();
    learn(Stormshot.class, 3);
    player.inventory().setItem(5, tool(Quiver.ID));
    player.inventory().setItem(1, new FakeItem(Id.minecraft("arrow"), 10));
    FakeLiving target = zombie(0.5D, 5.5D);
    int disallowed = 0;

    for (int shot = 0; shot < 4; shot++) {
      FakeProjectile arrow = arrow(new Vec3(0D, 0D, 1D));
      shoot(arrow, FakeItem.of("arrow"), true);
      hit(target, arrow, 4D);
      disallowed += arrow.pickupRule() == ArrowPickup.DISALLOWED ? 1 : 0;
    }

    assertThat(quiverData(5).count(Quiver.Kind.ARROW)).isEqualTo(2);
    assertThat(disallowed).isEqualTo(2);
  }

  @Test
  void theQuiverTakesNothingBackFromInfinityUnspentMissedFarmedOrRepeatHits() {
    mutate();
    learn(Stormshot.class, 3);
    player.inventory().setItem(5, tool(Quiver.ID));
    player.inventory().setItem(1, new FakeItem(Id.minecraft("arrow"), 10));
    FakeLiving natural = zombie(0.5D, 5.5D);
    FakeLiving pierced = zombie(0.5D, 7.5D);
    FakeLiving spawned = zombie(3.5D, 5.5D);
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);

    player.holding(FakeItem.tool("bow", 384).enchant("infinity", 1));
    for (int shot = 0; shot < 4; shot++) {
      FakeProjectile infinite = arrow(new Vec3(0D, 0D, 1D));
      shoot(infinite, FakeItem.of("arrow"), true);
      hit(natural, infinite, 4D);
    }
    player.holding(FakeItem.tool("bow", 384));
    for (int shot = 0; shot < 4; shot++) {
      FakeProjectile unspent = arrow(new Vec3(0D, 0D, 1D));
      shoot(unspent, FakeItem.of("arrow"), false);
      hit(natural, unspent, 4D);
      FakeProjectile missed = arrow(new Vec3(0D, 0D, 1D));
      shoot(missed, FakeItem.of("arrow"), true);
      FakeProjectile farmed = arrow(new Vec3(0D, 0D, 1D));
      shoot(farmed, FakeItem.of("arrow"), true);
      hit(spawned, farmed, 4D);
    }
    FakeProjectile piercing = arrow(new Vec3(0D, 0D, 1D));
    shoot(piercing, FakeItem.of("arrow"), true);
    hit(natural, piercing, 4D);
    hit(pierced, piercing, 4D);

    assertThat(quiverData(5).stored()).isZero();
  }

  @Test
  void aSkeletonsShotNeverLooksAtTheArrowItLoosed() {
    mutate();
    learn(Stormshot.class, 1);
    FakeLiving skeleton = zombie(2.5D, 2.5D);
    ItemRef consumable = mock(ItemRef.class);

    adapt.post(new EntityShootBowEvent(skeleton, FakeItem.of("bow"), consumable, arrow(new Vec3(0D, 0D, 1D)),
        Hand.MAIN_HAND, 1F, true));

    verifyNoInteractions(consumable);
  }

  @Test
  void theQuiverRestocksTheInventoryWhenTheLastArrowLeaves() {
    mutate();
    learn(Stormshot.class, 1);
    ItemRef quiver = tool(Quiver.ID);
    Quiver.Data data = new Quiver.Data();
    data.add(Quiver.Kind.ARROW, 70);
    ((Quiver) MutationTools.find(Quiver.ID)).write(quiver, data);
    player.inventory().setItem(5, quiver);

    shoot(arrow(new Vec3(0D, 0D, 1D)), FakeItem.of("arrow"), true);
    platform.scheduler().advance(1);

    assertThat(loose(Id.minecraft("arrow"))).isEqualTo(64);
    assertThat(quiverData(5).count(Quiver.Kind.ARROW)).isEqualTo(6);
  }

  @Test
  void aVolleySendsOnePaidArrowPerExtraLock() {
    mutate();
    Trueflight trueflight = learn(Trueflight.class, 1);
    trueflight.getConfig().fullDrawMillis = 0L;
    player.inventory().setItem(1, new FakeItem(Id.minecraft("arrow"), 5));
    zombie(0.5D, 8.5D);
    zombie(4.5D, 8.5D);

    sweepVolley(trueflight);
    shoot(arrow(new Vec3(0D, 0D, 1.5D)), FakeItem.of("arrow"), true);

    assertThat(player.launched()).hasSize(1);
    assertThat(player.launched().getFirst().velocity().x()).isPositive();
    assertThat(loose(Id.minecraft("arrow"))).isEqualTo(4);
  }

  @Test
  void aVolleyLocksNothingThroughWallsAndFiresNothingUnpaidHalfDrawnOrOnCooldown() {
    mutate();
    Trueflight trueflight = learn(Trueflight.class, 1);
    trueflight.getConfig().fullDrawMillis = 0L;
    zombie(0.5D, 8.5D);
    zombie(4.5D, 8.5D);
    player.inventory().setItem(1, tool(SnareArrow.ID).copyWithAmount(5));

    sweepVolley(trueflight);
    shoot(arrow(new Vec3(0D, 0D, 1.5D)), FakeItem.of("arrow"), true);
    assertThat(player.launched()).isEmpty();
    assertThat(countSnares()).isEqualTo(5);

    player.inventory().setItem(2, new FakeItem(Id.minecraft("arrow"), 5));
    sweepVolley(trueflight);
    adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), FakeItem.of("arrow"),
        arrow(new Vec3(0D, 0D, 0.7D)), Hand.MAIN_HAND, 0.5F, true));
    assertThat(player.launched()).isEmpty();

    world.blockAt(2, 62, 4).setType(Id.minecraft("stone"), false);
    sweepVolley(trueflight);
    shoot(arrow(new Vec3(0D, 0D, 1.5D)), FakeItem.of("arrow"), true);
    assertThat(player.launched()).isEmpty();

    world.blockAt(2, 62, 4).setType(Id.minecraft("air"), false);
    sweepVolley(trueflight);
    shoot(arrow(new Vec3(0D, 0D, 1.5D)), FakeItem.of("arrow"), true);
    sweepVolley(trueflight);
    shoot(arrow(new Vec3(0D, 0D, 1.5D)), FakeItem.of("arrow"), true);
    assertThat(player.launched()).hasSize(1);
    assertThat(loose(Id.minecraft("arrow"))).isEqualTo(4);
  }

  private void sweepVolley(Trueflight trueflight) {
    player.moveTo(new Loc(world, 0.5D, 61D, 0.5D, 0F, 0F));
    lockOn();
    player.setHandRaised(true);
    player.setActiveItem(player.inventory().mainHand());
    player.moveTo(new Loc(world, 0.5D, 61D, 0.5D, (float) -Math.toDegrees(Math.atan2(4D, 8D)), 0F));
    trueflight.onTick();
    platform.scheduler().advance(1);
  }

  private PlayerInteractEvent useHeld() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private Quiver.Data quiverData() {
    return ((Quiver) MutationTools.find(Quiver.ID)).read(player.inventory().mainHand());
  }

  private Quiver.Data quiverData(int slot) {
    return ((Quiver) MutationTools.find(Quiver.ID)).read(player.inventory().item(slot));
  }

  private int loose(Id type) {
    int count = 0;
    for (ItemRef stack : player.inventory().contents()) {
      if (stack != null && !stack.isEmpty() && stack.type().equals(type) && MutationTool.idOf(stack) == null) {
        count += stack.amount();
      }
    }
    return count;
  }

  private int countSnares() {
    int count = 0;
    for (ItemRef stack : player.inventory().contents()) {
      if (SnareArrow.ID.equals(MutationTool.idOf(stack))) {
        count += stack.amount();
      }
    }
    return count;
  }

  private EntityShootBowEvent shoot(FakeProjectile arrow, ItemRef consumable, boolean consumed) {
    return adapt.post(new EntityShootBowEvent(player, player.inventory().mainHand(), consumable, arrow,
        Hand.MAIN_HAND, 1F, consumed));
  }

  private ItemRef tool(String id) {
    MutationTool<?> tool = MutationTools.find(id);
    if (tool instanceof MunitionArrow arrow) {
      return arrow.create(new MutationTool.NoData());
    }
    return ((Quiver) tool).create(new Quiver.Data());
  }

  private void lockOn() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private FakeProjectile bouncedArrow(FakeProjectile original) {
    for (EntityRef entity : world.entities()) {
      if (entity instanceof FakeProjectile projectile && projectile != original) {
        return projectile;
      }
    }
    return null;
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("ranged", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("ranged"), registry.getSkill("stealth"), "Marksman"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("ranged").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("ranged").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeProjectile arrow(Vec3 velocity) {
    FakeProjectile arrow = world.add(new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 0.5D, 62.5D, 1.5D)));
    arrow.setShooter(player);
    arrow.setVelocity(velocity);
    arrow.setOnGround(false);
    return arrow;
  }

  private FakeLiving zombie(double x, double z) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(world, x, 61D, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return world.add(zombie);
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, FakeProjectile arrow, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, arrow, player, DamageCause.PROJECTILE,
        Id.minecraft("arrow"), damage, false, FinalDamage.IDENTITY));
  }
}
