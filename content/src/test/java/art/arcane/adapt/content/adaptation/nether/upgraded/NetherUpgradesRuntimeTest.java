package art.arcane.adapt.content.adaptation.nether.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.adaptation.nether.NetherSkullToss;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.PiglinBarterEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NetherUpgradesRuntimeTest {
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

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
    world.environment(Environment.NETHER, 1L);
    player = platform.player("Ashen", 0.5D, 61D, 0.5D);
    player.setGameMode(GameMode.SURVIVAL);
    adapt = TestAdapt.start(platform, List.of(SkillNether.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void brimstoneDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("nether").setAdaptation(upgrade(Brimstone.class), 3);

    EntityDamageEvent blast = adapt.post(damage(DamageCause.ENTITY_EXPLOSION, "explosion", 10D));

    assertThat(blast.damage()).isEqualTo(10D);
  }

  @Test
  void hellwalkerStridesOverLavaAndIgnoresFireAndMagma() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 3);
    world.set(0, 61, 0, "lava");
    player.setFoodLevel(20);

    adapt.post(new PlayerMoveEvent(player, player.location(), player.location()));
    EntityDamageEvent fire = adapt.post(damage(DamageCause.FIRE, "in_fire", 4D));
    EntityDamageEvent magma = adapt.post(damage(DamageCause.HOT_FLOOR, "hot_floor", 1D));

    assertThat(player.effect(FIRE_RESISTANCE)).isNotNull();
    assertThat(player.foodLevel()).isEqualTo(20 - hellwalker.getConfig().hungerPerStride);
    assertThat(player.velocity().y()).isGreaterThan(0D);
    assertThat(fire.isCancelled()).isTrue();
    assertThat(magma.isCancelled()).isTrue();
    assertThat(XpPaid.by(profile, hellwalker)).isZero();
  }

  @Test
  void hellwalkerStridesAlongTheFacingWhenLookingStraightUpOrDown() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 3);
    hellwalker.getConfig().strideCooldownMillisBase = 0;
    hellwalker.getConfig().minimumStrideCooldownMillis = 0;
    world.set(0, 61, 0, "lava");
    for (float pitch : new float[] {-90F, 90F}) {
      player.setFoodLevel(20);
      player.moveTo(player.location().withRotation(90F, pitch));

      adapt.post(new PlayerMoveEvent(player, player.location(), player.location()));

      assertThat(player.velocity().x()).isCloseTo(-hellwalker.getStride(3), within(1.0E-9D));
      assertThat(player.velocity().z()).isCloseTo(0D, within(1.0E-9D));
      assertThat(Double.isFinite(player.velocity().y())).isTrue();
    }
  }

  @Test
  void hellwalkerDescribesStridesRatherThanWalkingOnLava() {
    Hellwalker hellwalker = upgrade(Hellwalker.class);

    assertThat(hellwalker.getDescription()).startsWith("Stride over Nether lava").doesNotContain("Walk on lava");
  }

  @Test
  void hellwalkerRemovesTheSoulSandSlowdown() {
    mutate();
    learn(Hellwalker.class, 1);
    world.set(0, 60, 0, "soul_sand");
    Loc from = player.location();
    double before = player.attributeValue(Attributes.MOVEMENT_EFFICIENCY);

    adapt.post(new PlayerMoveEvent(player, from, from.add(0.3D, 0D, 0D)));

    assertThat(player.attributeValue(Attributes.MOVEMENT_EFFICIENCY) - before).isCloseTo(1D, within(1.0E-9D));
  }

  @Test
  void witherKingThrowsSkullsShrugsOffWitherAndHarvestsSkeletons() {
    mutate();
    learn(WitherKing.class, 3);
    FakeItem skulls = FakeItem.of("wither_skeleton_skull");
    skulls.setAmount(2);
    player.holding(skulls);

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, skulls, null, null, null,
        UseResult.DENY, UseResult.DEFAULT));
    EntityDamageEvent wither = adapt.post(damage(DamageCause.WITHER, "wither", 1D));
    FakeLiving skeleton = world.add(new FakeLiving(Id.minecraft("wither_skeleton"), new Loc(world, 3D, 61D, 0D)));
    EntityDeathEvent death = adapt.post(new EntityDeathEvent(skeleton, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(), 5));

    EntityRef skull = world.entities().stream().filter((EntityRef entity) -> entity.type().equals(Id.minecraft("wither_skull")))
        .findFirst().orElseThrow();
    assertThat(NetherSkullToss.isThrownSkull(skull)).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(1);
    assertThat(wither.isCancelled()).isTrue();
    assertThat(death.drops()).extracting((ItemRef drop) -> drop.type().path())
        .contains("bone", "coal");
  }

  @Test
  void witherKingHarvestsNothingFromASpawnedSkeleton() {
    mutate();
    learn(WitherKing.class, 3);
    FakeLiving skeleton = world.add(new FakeLiving(Id.minecraft("wither_skeleton"), new Loc(world, 3D, 61D, 0D)));
    MobOrigin.record(skeleton, CreatureSpawnEvent.Reason.SPAWNER);

    EntityDeathEvent death = adapt.post(new EntityDeathEvent(skeleton, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(), 5));

    assertThat(death.drops()).isEmpty();
    assertThat(XpPaid.by(profile, upgrade(WitherKing.class))).isZero();
  }

  @Test
  void brimstoneIgnitesAttackersFeedsOnFireAndSoftensBlasts() {
    mutate();
    Brimstone brimstone = learn(Brimstone.class, 1);
    FakeLiving zombie = world.add(new FakeLiving(Id.minecraft("zombie"), new Loc(world, 1.5D, 61D, 0.5D)));
    zombie.trait(EntityTrait.MONSTER);
    player.setFoodLevel(10);

    adapt.post(new EntityDamageByEntityEvent(player, zombie, zombie, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 3D,
        false, FinalDamage.IDENTITY));
    platform.scheduler().advance(2);
    adapt.post(damage(DamageCause.FIRE_TICK, "on_fire", 1D));
    EntityDamageEvent blast = adapt.post(damage(DamageCause.BLOCK_EXPLOSION, "explosion", 10D));

    assertThat(zombie.fireTicks()).isEqualTo(brimstone.getIgniteTicks(1));
    assertThat(player.foodLevel()).isEqualTo(10 + (int) Math.round(brimstone.getFoodRestore(1)));
    assertThat(player.effect(REGENERATION)).isNotNull();
    assertThat(blast.damage()).isCloseTo(10D * (1D - brimstone.getBlastReduction(1)), within(1.0E-9D));
  }

  @Test
  void hellwalkerSwimsThroughLavaAtAShareOfWaterSpeedAndSeesClearly() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 1);
    world.environment(Environment.NORMAL, 1L);
    submerge();
    player.setFoodLevel(20);
    Loc from = player.location();

    EntityDamageEvent lava = adapt.post(damage(DamageCause.LAVA, "lava", 4D));
    adapt.post(new PlayerMoveEvent(player, from, from.add(0.05D, 0D, 0D)));

    assertThat(lava.isCancelled()).isTrue();
    assertThat(player.effect(FIRE_RESISTANCE)).isNotNull();
    assertThat(player.effect(NIGHT_VISION)).isNotNull();
    assertThat(player.velocity().x()).isCloseTo(hellwalker.getConfig().waterSwimSpeed * hellwalker.getSwimShare(1),
        within(1.0E-9D));
    assertThat(hellwalker.getSwimShare(1)).isCloseTo(0.6D, within(1.0E-9D));
    assertThat(hellwalker.getSwimShare(3)).isCloseTo(1D, within(1.0E-9D));
  }

  @Test
  void hellwalkerChargesOneHungerPerTenSecondsInLavaAndLeavesYouToBurnWhenStarving() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 1);
    world.environment(Environment.NORMAL, 1L);
    submerge();
    player.setFoodLevel(20);

    for (int second = 0; second < 10; second++) {
      hellwalker.onTick();
      platform.scheduler().advance(2);
    }
    assertThat(player.foodLevel()).isEqualTo(19);

    player.setFoodLevel(0);
    EntityDamageEvent lava = adapt.post(damage(DamageCause.LAVA, "lava", 4D));
    assertThat(lava.isCancelled()).isFalse();
    assertThat(XpPaid.by(profile, hellwalker)).isZero();
  }

  @Test
  void hellwalkerTakesBackItsNightVisionWhenYouLeaveTheLava() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 1);
    submerge();
    player.setFoodLevel(20);
    hellwalker.onTick();
    platform.scheduler().advance(2);
    assertThat(player.effect(NIGHT_VISION)).isNotNull();

    world.set(0, 61, 0, "air");
    world.set(0, 62, 0, "air");
    hellwalker.onTick();
    platform.scheduler().advance(2);

    assertThat(player.effect(NIGHT_VISION)).isNull();
  }

  @Test
  void hellwalkerLeavesYourOwnNightVisionWhenYouLeaveTheLava() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 1);
    submerge();
    player.setFoodLevel(20);
    hellwalker.onTick();
    platform.scheduler().advance(2);
    player.addEffect(EffectInstance.of(NIGHT_VISION, 200, 0));

    world.set(0, 61, 0, "air");
    world.set(0, 62, 0, "air");
    hellwalker.onTick();
    platform.scheduler().advance(2);

    assertThat(player.effect(NIGHT_VISION)).isNotNull();
  }

  @Test
  void hellwalkerLavaSwimDoesNothingWithoutTheMutation() {
    profile.getData().getSkillLine("nether").setAdaptation(upgrade(Hellwalker.class), 3);
    world.environment(Environment.NORMAL, 1L);
    submerge();
    player.setFoodLevel(20);

    EntityDamageEvent lava = adapt.post(damage(DamageCause.LAVA, "lava", 4D));

    assertThat(lava.isCancelled()).isFalse();
    assertThat(player.effect(NIGHT_VISION)).isNull();
  }

  @Test
  void hellwalkerFloatsItemsDroppedNearLavaFromLevelTwoForFiveSeconds() {
    mutate();
    Hellwalker hellwalker = learn(Hellwalker.class, 1);
    world.set(1, 60, 0, "lava");
    FakeItemEntity early = drop();
    assertThat(burn(early).isCancelled()).isFalse();

    learn(Hellwalker.class, 2);
    FakeItemEntity floated = drop();
    assertThat(burn(floated).isCancelled()).isTrue();

    platform.scheduler().advance(hellwalker.getConfig().itemFloatTicks + 2);
    assertThat(burn(floated).isCancelled()).isFalse();
  }

  @Test
  void hellwalkerNeverTouchesAnItemsInvulnerableFlag() {
    mutate();
    learn(Hellwalker.class, 3);
    world.set(1, 60, 0, "lava");
    FakeItemEntity item = new FakeItemEntity(new Loc(world, 1.5D, 61D, 0.5D), FakeItem.of("diamond"));
    item.setInvulnerable(true);
    world.add(item);
    FakeItemEntity plain = drop();

    adapt.post(new PlayerDropItemEvent(player, item));
    platform.scheduler().advance(2);

    assertThat(item.isInvulnerable()).isTrue();
    assertThat(plain.isInvulnerable()).isFalse();
  }

  @Test
  void brimstoneBrokersBartersAtTheTopLevelOnceAndPaysNoXp() {
    mutate();
    Brimstone brimstone = learn(Brimstone.class, 1);
    brimstone.getConfig().barterExtraRollChance = 1D;
    brimstone.getConfig().barterRareBonusChance = 1D;
    FakeLiving piglin = world.add(new FakeLiving(Id.minecraft("piglin"), new Loc(world, 3D, 61D, 0D)));

    for (int barter = 0; barter < 40; barter++) {
      PiglinBarterEvent event = adapt.post(new PiglinBarterEvent(piglin, FakeItem.of("gold_ingot"),
          List.of(FakeItem.of("quartz"))));
      assertThat(event.outcome()).hasSize(3);
    }
    assertThat(XpPaid.by(profile, brimstone)).isZero();
  }

  @Test
  void brimstoneAddsNothingToAFarmedPiglinsBarter() {
    mutate();
    Brimstone brimstone = learn(Brimstone.class, 1);
    brimstone.getConfig().barterExtraRollChance = 1D;
    brimstone.getConfig().barterRareBonusChance = 1D;
    FakeLiving piglin = world.add(new FakeLiving(Id.minecraft("piglin"), new Loc(world, 3D, 61D, 0D)));
    MobOrigin.record(piglin, CreatureSpawnEvent.Reason.SPAWNER_EGG);

    for (int barter = 0; barter < 40; barter++) {
      PiglinBarterEvent event = adapt.post(new PiglinBarterEvent(piglin, FakeItem.of("gold_ingot"),
          List.of(FakeItem.of("quartz"))));
      assertThat(event.outcome()).hasSize(1);
    }
  }

  @Test
  void brimstoneKeepsPiglinsNeutralUntilYouProvokeThem() {
    mutate();
    learn(Brimstone.class, 1);
    FakeLiving piglin = world.add(new FakeLiving(Id.minecraft("piglin"), new Loc(world, 3D, 61D, 0D)));
    FakeLiving brute = world.add(new FakeLiving(Id.minecraft("piglin_brute"), new Loc(world, 4D, 61D, 0D)));

    assertThat(target(piglin).isCancelled()).isTrue();
    assertThat(target(brute).isCancelled()).isFalse();

    world.set(2, 61, 2, "chest");
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, null,
        world.blockAt(2, 61, 2), null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(target(piglin).isCancelled()).isFalse();
  }

  @Test
  void brimstoneLetsAPiglinYouHitFightBack() {
    mutate();
    learn(Brimstone.class, 1);
    FakeLiving piglin = world.add(new FakeLiving(Id.minecraft("piglin"), new Loc(world, 3D, 61D, 0D)));

    adapt.post(new EntityDamageByEntityEvent(piglin, player, player, DamageCause.ENTITY_ATTACK, Id.minecraft("player_attack"),
        2D, false, FinalDamage.IDENTITY));

    assertThat(target(piglin).isCancelled()).isFalse();
  }

  @Test
  void piglinsStayHostileToAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("nether").setAdaptation(upgrade(Brimstone.class), 3);
    FakeLiving piglin = world.add(new FakeLiving(Id.minecraft("piglin"), new Loc(world, 3D, 61D, 0D)));

    assertThat(target(piglin).isCancelled()).isFalse();
  }

  private EntityTargetEvent target(FakeLiving mob) {
    return adapt.post(new EntityTargetEvent(mob, player, EntityTargetEvent.Reason.CLOSEST_PLAYER));
  }

  private void submerge() {
    world.set(0, 61, 0, "lava");
    world.set(0, 62, 0, "lava");
  }

  private FakeItemEntity drop() {
    FakeItemEntity item = new FakeItemEntity(new Loc(world, 1.5D, 61D, 0.5D), FakeItem.of("diamond"));
    world.add(item);
    adapt.post(new PlayerDropItemEvent(player, item));
    return item;
  }

  private EntityDamageEvent burn(FakeItemEntity item) {
    return adapt.post(new EntityDamageEvent(item, DamageCause.LAVA, Id.minecraft("lava"), 4D, FinalDamage.IDENTITY));
  }

  private EntityDamageEvent damage(DamageCause cause, String type, double amount) {
    return new EntityDamageEvent(player, cause, Id.minecraft(type), amount, FinalDamage.IDENTITY);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("nether", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("nether"), registry.getSkill("stealth"), "Ashen"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("nether").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("nether")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
