package art.arcane.adapt.content.adaptation.blocking.upgraded;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.AbilityHitLiving;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
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

class BlockingDepthRuntimeTest {
  private static final Id SHIELD = Id.minecraft("shield");
  private static final Id TINTED_GLASS = Id.minecraft("tinted_glass");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final DataKey<byte[]> PLANTED_SHIELD = DataKey.adapt("planted-bulwark-shield", DataCodecs.BYTE_ARRAY);

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
  void plantingTurnsTheShieldIntoAWallInFrontUntilItExpires() {
    mutate();
    LivingWall wall = learn(LivingWall.class, 1);

    plant();

    assertThat(wallBlocks()).hasSize(3 * 2);
    assertThat(shields()).isZero();
    assertThat(player.data().get(PLANTED_SHIELD)).isNotNull();

    platform.scheduler().advance(wall.getDurationTicks(1) + 20);

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
    assertThat(player.data().get(PLANTED_SHIELD)).isNull();
  }

  @Test
  void theTopLevelWallIsFiveWide() {
    mutate();
    learn(LivingWall.class, 3);

    plant();

    assertThat(wallBlocks()).hasSize(5 * 2);
  }

  @Test
  void plantingDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("blocking").setAdaptation(upgrade(LivingWall.class), 1);

    plant();

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void movingWhileSneakingPlantsNothing() {
    mutate();
    learn(LivingWall.class, 1);
    player.setBlocking(true);
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    Loc from = player.location();
    Loc to = from.add(1D, 0, 0);
    player.moveTo(to);
    adapt.post(new PlayerMoveEvent(player, from, to));
    platform.scheduler().advance(21);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void letGoTooEarlyPlantsNothing() {
    mutate();
    learn(LivingWall.class, 1);
    player.setBlocking(true);
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.scheduler().advance(10);
    player.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(player, false));
    platform.scheduler().advance(15);

    assertThat(wallBlocks()).isEmpty();
  }

  @Test
  void protectedLandRefusesTheWallAndKeepsTheShield() {
    mutate();
    learn(LivingWall.class, 1);
    platform.protection().deny(true);

    plant();

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
    assertThat(player.inventory().offHand().damage()).isZero();
    assertThat(player.data().get(PLANTED_SHIELD)).isNull();
  }

  @Test
  void aWornOutShieldCannotBePlanted() {
    mutate();
    learn(LivingWall.class, 1);
    ItemRef shield = player.inventory().offHand();
    shield.setDamage(320);
    player.inventory().setOffHand(shield);

    plant();

    assertThat(wallBlocks()).isEmpty();
    assertThat(player.inventory().offHand().damage()).isEqualTo(320);
  }

  @Test
  void wallBlocksCannotBeBrokenForDrops() {
    mutate();
    learn(LivingWall.class, 1);
    plant();

    BlockBreakEvent breaking = adapt.post(new BlockBreakEvent(player, wallBlocks().getFirst(), true, 0));

    assertThat(breaking.isCancelled()).isTrue();
  }

  @Test
  void pickingTheWallUpReturnsOneShieldAndASecondPickUpReturnsNothing() {
    mutate();
    learn(LivingWall.class, 1);
    plant();
    BlockRef block = wallBlocks().getFirst();

    rightClick(block);
    rightClick(block);
    adapt.post(new PlayerQuitEvent(player));

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void loggingOutReturnsTheShieldOnceAndLoggingBackInAddsNoSecond() {
    mutate();
    learn(LivingWall.class, 1);
    plant();

    adapt.post(new PlayerQuitEvent(player));
    adapt.post(new PlayerJoinEvent(player));
    platform.scheduler().advance(2);

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void dyingTakesTheWallDownAndRespawningReturnsTheShieldOnce() {
    mutate();
    learn(LivingWall.class, 1);
    plant();

    player.setDead(true);
    adapt.post(new PlayerDeathEvent(player, null, null, null, DamageCause.ENTITY_ATTACK, new ArrayList<>(), 0, false,
        false, 0, 0, 0));
    platform.scheduler().advance(2);
    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isZero();

    player.setDead(false);
    adapt.post(new PlayerRespawnEvent(player, player.location(), false, false));
    adapt.post(new PlayerRespawnEvent(player, player.location(), false, false));
    platform.scheduler().advance(2);

    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void aShieldLeftInPlayerDataAfterACrashComesBackOnJoin() {
    mutate();
    learn(LivingWall.class, 1);
    plant();
    byte[] stored = player.data().get(PLANTED_SHIELD);
    FakePlayer other = platform.player("Returning", 4.5D, 61D, 4.5D);
    other.data().set(PLANTED_SHIELD, stored);

    adapt.join(other);
    platform.scheduler().advance(2);

    assertThat(SHIELD.equals(other.inventory().offHand().type())).isTrue();
    assertThat(other.data().get(PLANTED_SHIELD)).isNull();
  }

  @Test
  void walkingFarAwayReturnsTheShield() {
    mutate();
    learn(LivingWall.class, 1);
    plant();

    player.moveTo(player.location().add(0, 0, -30D));
    platform.scheduler().advance(11);

    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void theWallCannotBePlantedAgainUntilTheCooldownEnds() {
    mutate();
    LivingWall wall = learn(LivingWall.class, 1);
    plant();
    rightClick(wallBlocks().getFirst());

    plant();

    assertThat(wall.getCooldownMillis(1)).isPositive();
    assertThat(wallBlocks()).isEmpty();
    assertThat(shields()).isEqualTo(1);
  }

  @Test
  void anEarlierWallsTimerLeavesANewerWallOnTheSameSpot() {
    mutate(profile);
    LivingWall wall = learn(profile, LivingWall.class, 1);
    plant(player);
    rightClick(wallBlocks().getFirst());
    platform.scheduler().advance(45);
    FakePlayer second = platform.player("Second", 0.5D, 61D, 0.5D);
    second.inventory().setOffHand(FakeItem.tool("shield", 336));
    AdaptPlayer secondProfile = adapt.join(second);
    mutate(secondProfile);
    learn(secondProfile, LivingWall.class, 1);

    plant(second);
    assertThat(wallBlocks()).hasSize(3 * 2);
    platform.scheduler().advance(wall.getDurationTicks(1) - 10);

    assertThat(wallBlocks()).hasSize(3 * 2);
  }

  @Test
  void theWallSlowsMobsAndComesDownOnTheWallsOwnRegion() {
    mutate();
    learn(LivingWall.class, 1);
    plant();
    List<BlockRef> blocks = wallBlocks();
    int planted = platform.scheduler().regionTaskLocations().size();

    platform.scheduler().advance(11);
    int ticked = platform.scheduler().regionTaskLocations().size();
    rightClick(blocks.getFirst());
    List<Loc> requested = platform.scheduler().regionTaskLocations();

    assertThat(ticked).isGreaterThan(planted);
    assertThat(requested.subList(ticked, requested.size()))
        .containsAll(blocks.stream().map(BlockRef::location).toList());
  }

  @Test
  void anAllyBehindTheWallTakesLessFromABlow() {
    mutate();
    LivingWall wall = learn(LivingWall.class, 1);
    FakePlayer ally = platform.player("Ally", 0.5D, 61D, -0.5D);
    adapt.join(ally);
    player.addTeammate(ally);
    plant();
    FakeLiving zombie = zombieAt(0.5D, 5.5D);

    EntityDamageByEntityEvent blow = adapt.post(new EntityDamageByEntityEvent(ally, zombie, zombie,
        DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 10D, false, FinalDamage.IDENTITY));

    assertThat(blow.damage()).isCloseTo(10D * (1D - wall.getDamageReduction(1)), within(1.0E-9D));
  }

  @Test
  void anAllyInFrontOfTheWallIsNotSheltered() {
    mutate();
    learn(LivingWall.class, 1);
    FakePlayer ally = platform.player("Ally", 0.5D, 61D, 4.5D);
    adapt.join(ally);
    player.addTeammate(ally);
    plant();
    FakeLiving zombie = zombieAt(0.5D, 6.5D);

    EntityDamageByEntityEvent blow = adapt.post(new EntityDamageByEntityEvent(ally, zombie, zombie,
        DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 10D, false, FinalDamage.IDENTITY));

    assertThat(blow.damage()).isEqualTo(10D);
  }

  @Test
  void aStrangerOffYourTeamIsNotShelteredWherePvpIsOn() {
    mutate();
    learn(LivingWall.class, 1);
    FakePlayer stranger = platform.player("Stranger", 0.5D, 61D, -0.5D);
    adapt.join(stranger);
    plant();
    FakeLiving zombie = zombieAt(0.5D, 5.5D);

    EntityDamageByEntityEvent blow = adapt.post(new EntityDamageByEntityEvent(stranger, zombie, zombie,
        DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 10D, false, FinalDamage.IDENTITY));

    assertThat(blow.damage()).isEqualTo(10D);
  }

  @Test
  void hostileMobsAgainstTheWallAreSlowed() {
    mutate();
    learn(LivingWall.class, 1);
    plant();
    FakeLiving zombie = zombieAt(0.5D, 3.5D);

    platform.scheduler().advance(11);

    assertThat(zombie.hasEffect(SLOWNESS)).isTrue();
  }

  @Test
  void blockingCutsYourOwnBlastDamage() {
    mutate();
    LivingWall wall = learn(LivingWall.class, 1);
    player.setBlocking(true);
    FakeLiving creeper = zombieAt(0.5D, 3.5D);

    EntityDamageByEntityEvent blast = adapt.post(new EntityDamageByEntityEvent(player, creeper, creeper,
        DamageCause.ENTITY_EXPLOSION, Id.minecraft("explosion"), 10D, false, FinalDamage.IDENTITY));
    platform.scheduler().advance(1);

    assertThat(blast.damage()).isCloseTo(10D * (1D - wall.getDamageReduction(1)), within(1.0E-9D));
    assertThat(player.inventory().offHand().damage()).isPositive();
  }

  @Test
  void aSprintJumpWithTheShieldUpBashesTheFirstFoeForDurability() {
    mutate();
    TemperedAegis aegis = learn(TemperedAegis.class, 1);
    FakeLiving zombie = zombieAt(0.5D, 1.8D);

    bashMove();

    assertThat(zombie.damageTaken()).isCloseTo(aegis.getBashDamage(1), within(1.0E-9D));
    assertThat(zombie.hasEffect(SLOWNESS)).isTrue();
    assertThat(player.inventory().offHand().damage()).isEqualTo(5);
  }

  @Test
  void theBashIsNotTakenForAMeleeHit() {
    mutate();
    TemperedAegis aegis = learn(TemperedAegis.class, 1);
    AbilityHitLiving zombie = platform.world().add(new AbilityHitLiving(adapt, aegis, Id.minecraft("zombie"),
        new Loc(platform.world(), 0.5D, 61D, 1.8D)));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);

    bashMove();

    assertThat(zombie.damageTaken()).isPositive();
    assertThat(zombie.meleeHits()).isZero();
  }

  @Test
  void theBashWaitsForItsCooldown() {
    mutate();
    TemperedAegis aegis = learn(TemperedAegis.class, 1);
    FakeLiving zombie = zombieAt(0.5D, 1.8D);

    bashMove();
    bashMove();

    assertThat(zombie.damageTaken()).isCloseTo(aegis.getBashDamage(1), within(1.0E-9D));
    assertThat(player.inventory().offHand().damage()).isEqualTo(5);
  }

  @Test
  void noBashWithoutARecentSprint() {
    mutate();
    learn(TemperedAegis.class, 1);
    FakeLiving zombie = zombieAt(0.5D, 1.8D);
    player.setBlocking(true);
    player.setOnGround(false);

    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(0, 0.2D, 0)));

    assertThat(zombie.damageTaken()).isZero();
    assertThat(player.inventory().offHand().damage()).isZero();
  }

  @Test
  void aSpawnerMobBashPaysNoXp() {
    mutate();
    TemperedAegis aegis = learn(TemperedAegis.class, 1);
    FakeLiving zombie = zombieAt(0.5D, 1.8D);
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    bashMove();

    assertThat(zombie.damageTaken()).isPositive();
    assertThat(XpPaid.by(profile, aegis)).isZero();
  }

  private void plant() {
    plant(player);
  }

  private void plant(FakePlayer planter) {
    planter.setBlocking(true);
    planter.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(planter, true));
    platform.scheduler().advance(21);
    planter.setSneaking(false);
    adapt.post(new PlayerToggleSneakEvent(planter, false));
    planter.setBlocking(false);
  }

  private void bashMove() {
    adapt.post(new PlayerToggleSprintEvent(player, false));
    player.setBlocking(true);
    player.setOnGround(false);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(0, 0.2D, 0)));
  }

  private void rightClick(BlockRef block) {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), block, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private List<BlockRef> wallBlocks() {
    List<BlockRef> found = new ArrayList<>();
    for (int x = -4; x <= 4; x++) {
      for (int y = 60; y <= 64; y++) {
        for (int z = -4; z <= 5; z++) {
          BlockRef block = platform.world().blockAt(x, y, z);
          if (TINTED_GLASS.equals(block.type())) {
            found.add(block);
          }
        }
      }
    }
    return found;
  }

  private int shields() {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && SHIELD.equals(item.type())) {
        count += item.amount();
      }
    }
    return count;
  }

  private void mutate() {
    mutate(profile);
  }

  private void mutate(AdaptPlayer mutating) {
    mutating.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("blocking", "stealth")) {
      PlayerSkillLine line = mutating.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(mutating.getData(), registry.getSkill("blocking"), registry.getSkill("stealth"), "Bulwark"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    return learn(profile, type, level);
  }

  private <A extends Adaptation<?>> A learn(AdaptPlayer learner, Class<A> type, int level) {
    A upgrade = upgrade(type);
    learner.getData().getSkillLine("blocking").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("blocking")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeLiving zombieAt(double x, double z) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return platform.world().add(zombie);
  }
}
