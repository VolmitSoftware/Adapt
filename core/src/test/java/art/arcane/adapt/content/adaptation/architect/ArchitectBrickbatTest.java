package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.AbilityHitLiving;
import art.arcane.adapt.testkit.FakeItem;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ArchitectBrickbatTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id SNOWBALL = Id.minecraft("snowball");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private ArchitectBrickbat brickbat;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.items().hardness("stone", 1.5F, 6F).hardness("obsidian", 50F, 1200F)
        .hardness("glass", 0.3F, 0.3F).hardness("bedrock", -1F, 3600000F);
    world = platform.world();
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    hold("stone", 5);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class));
    profile = adapt.join(player);
    brickbat = adaptation();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void heftFollowsHardnessAndBlastResistance() {
    double obsidian = ArchitectBrickbat.heft(50D, 1200D);
    double ancientDebris = ArchitectBrickbat.heft(30D, 1200D);
    double anvil = ArchitectBrickbat.heft(5D, 1200D);
    double iron = ArchitectBrickbat.heft(5D, 6D);
    double stone = ArchitectBrickbat.heft(1.5D, 6D);
    double wool = ArchitectBrickbat.heft(0.8D, 0.8D);
    double glass = ArchitectBrickbat.heft(0.3D, 0.3D);
    double leaves = ArchitectBrickbat.heft(0.2D, 0.2D);

    assertThat(obsidian).isGreaterThan(ancientDebris);
    assertThat(ancientDebris).isGreaterThan(anvil);
    assertThat(anvil).isGreaterThan(iron);
    assertThat(iron).isGreaterThan(stone);
    assertThat(stone).isGreaterThan(wool);
    assertThat(wool).isGreaterThan(glass);
    assertThat(glass).isGreaterThan(leaves);
    assertThat(obsidian).isGreaterThan(10D);
    assertThat(anvil).isGreaterThan(5D);
    assertThat(wool).isLessThan(1.2D);
    assertThat(ArchitectBrickbat.heft(500D, 1.0E9D)).isEqualTo(ArchitectBrickbat.heft(50D, 1.0E9D));
  }

  @Test
  void damageScalesWithHeftAndLevelUnderACap() {
    ArchitectBrickbat.Config config = new ArchitectBrickbat.Config();
    double stone = ArchitectBrickbat.heft(1.5D, 6D);

    assertThat(ArchitectBrickbat.damage(stone, 0.2D, config)).isCloseTo(stone * 1.02D, within(1.0E-9D));
    assertThat(ArchitectBrickbat.damage(stone, 1D, config)).isCloseTo(stone * 1.5D, within(1.0E-9D));
    assertThat(ArchitectBrickbat.damage(ArchitectBrickbat.heft(50D, 1200D), 1D, config)).isEqualTo(config.maxDamage);
    assertThat(ArchitectBrickbat.damage(0D, 1D, config)).isEqualTo(config.minDamage);
  }

  @Test
  void heavyBlocksWindUpLongerAndLevelsShortenEveryWindUp() {
    ArchitectBrickbat.Config config = new ArchitectBrickbat.Config();
    double stone = ArchitectBrickbat.heft(1.5D, 6D);
    double obsidian = ArchitectBrickbat.heft(50D, 1200D);

    assertThat(ArchitectBrickbat.cooldownMillis(obsidian, 0.2D, config))
        .isGreaterThan(ArchitectBrickbat.cooldownMillis(stone, 0.2D, config));
    assertThat(ArchitectBrickbat.cooldownMillis(stone, 1D, config))
        .isLessThan(ArchitectBrickbat.cooldownMillis(stone, 0.2D, config));
    assertThat(ArchitectBrickbat.knockback(stone, config)).isZero();
    assertThat(ArchitectBrickbat.knockback(obsidian, config)).isEqualTo(config.maxKnockback);
    assertThat(brickbat.range(brickbat.getLevelPercent(1))).isLessThan(brickbat.range(brickbat.getLevelPercent(5)));
  }

  @Test
  void blocksWithContentsStateOrNoBreakingAreNeverThrown() {
    assertThat(ArchitectBrickbat.throwable(FakeItem.of("stone"))).isTrue();
    assertThat(ArchitectBrickbat.throwable(FakeItem.of("obsidian"))).isTrue();
    for (String held : List.of("chest", "barrel", "spawner", "oak_door", "red_bed", "white_banner", "oak_sign", "tnt",
        "bedrock", "air")) {
      assertThat(ArchitectBrickbat.throwable(FakeItem.of(held))).as(held).isFalse();
    }
    assertThat(ArchitectBrickbat.throwable(FakeItem.tool("iron_sword", 250))).isFalse();
    assertThat(ArchitectBrickbat.shatters(Id.minecraft("glass"))).isTrue();
    assertThat(ArchitectBrickbat.shatters(Id.minecraft("red_stained_glass_pane"))).isTrue();
    assertThat(ArchitectBrickbat.shatters(Id.minecraft("ice"))).isTrue();
    assertThat(ArchitectBrickbat.shatters(Id.minecraft("tinted_glass"))).isFalse();
    assertThat(ArchitectBrickbat.shatters(STONE)).isFalse();
  }

  @Test
  void aPunchThrowsOneBlockInsteadAndItDropsWhereItLands() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);

    EntityDamageByEntityEvent punch = punch(zombie);

    assertThat(punch.isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(4);
    ProjectileRef ball = onlyBall();
    adapt.post(new ProjectileHitEvent(ball, zombie, null, null));
    platform.scheduler().advance(20);

    double expected = ArchitectBrickbat.damage(ArchitectBrickbat.heft(1.5D, 6D), 1D, brickbat.getConfig());
    assertThat(zombie.damageTaken()).isCloseTo(expected, within(1.0E-6D));
    assertThat(drops(STONE)).isEqualTo(1);
    assertThat(XpPaid.by(profile, brickbat)).isPositive();
  }

  @Test
  void swingingAtAnAimedCreatureInRangeThrows() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 9.5D);

    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(onlyBall().velocity().z()).isPositive();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(4);
    assertThat(zombie.isValid()).isTrue();
  }

  @Test
  void swingingAtNothingKeepsTheBlock() {
    learn(5);

    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(balls()).isEmpty();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(5);
  }

  @Test
  void glassShattersAndDropsNothing() {
    learn(5);
    hold("glass", 1);
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);

    punch(zombie);
    adapt.post(new ProjectileHitEvent(onlyBall(), zombie, null, null));
    platform.scheduler().advance(20);

    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
    assertThat(world.items()).isEmpty();
    assertThat(zombie.damageTaken()).isPositive();
  }

  @Test
  void theCooldownLetsTheNextPunchLandAsAPunch() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);

    assertThat(punch(zombie).isCancelled()).isTrue();
    assertThat(punch(zombie).isCancelled()).isFalse();

    assertThat(balls()).hasSize(1);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(4);
  }

  @Test
  void theServerCapsBlocksInTheAir() {
    learn(5);
    brickbat.getConfig().cooldownMillisBase = 0;
    brickbat.getConfig().cooldownMillisPerHeft = 0;
    brickbat.getConfig().maxInFlight = 1;
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);

    punch(zombie);
    punch(zombie);

    assertThat(balls()).hasSize(1);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(4);
  }

  @Test
  void petsAndProtectedTargetsAreNeverThrownAt() {
    learn(5);
    FakeLiving pet = zombie(0.5D, 61D, 3.5D);
    pet.setTameOwner(UUID.randomUUID());
    FakeLiving other = zombie(1.5D, 61D, 3.5D);

    assertThat(punch(pet).isCancelled()).isFalse();
    AdaptPorts.install(ProtectorPort.class, new NoFighting());
    try {
      assertThat(punch(other).isCancelled()).isFalse();
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }

    assertThat(balls()).isEmpty();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(5);
  }

  @Test
  void playersAreOnlyHitWherePvpIsAllowed() {
    learn(5);
    FakePlayer rival = platform.player("Rival", 0.5D, 61D, 3.5D);
    AdaptPorts.install(ProtectorPort.class, new NoFighting());
    try {
      adapt.post(new EntityDamageByEntityEvent(rival, player, player, DamageCause.ENTITY_ATTACK,
          Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }

    assertThat(balls()).isEmpty();
    assertThat(rival.damageTaken()).isZero();
  }

  @Test
  void creativePlayersPunchNormally() {
    learn(5);
    player.setGameMode(GameMode.CREATIVE);
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);

    assertThat(punch(zombie).isCancelled()).isFalse();
    assertThat(balls()).isEmpty();
  }

  @Test
  void itDoesNothingWithoutTheAdaptation() {
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);

    assertThat(punch(zombie).isCancelled()).isFalse();
    assertThat(balls()).isEmpty();
    assertThat(player.inventory().mainHand().amount()).isEqualTo(5);
  }

  @Test
  void farmedMobsPayNoXp() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    punch(zombie);
    adapt.post(new ProjectileHitEvent(onlyBall(), zombie, null, null));
    platform.scheduler().advance(20);

    assertThat(zombie.damageTaken()).isPositive();
    assertThat(XpPaid.by(profile, brickbat)).isZero();
  }

  @Test
  void exploitThrowingIntoProtectedLandPlacesAndBreaksNothing() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);
    BlockRef wall = world.set(0, 61, 3, "stone").blockAt(0, 61, 3);

    punch(zombie);
    AdaptPorts.install(ProtectorPort.class, new NoFighting());
    try {
      adapt.post(new ProjectileHitEvent(onlyBall(), zombie, wall, BlockFace.NORTH));
      platform.scheduler().advance(20);
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }

    assertThat(wall.type()).isEqualTo(STONE);
    assertThat(world.blockAt(0, 61, 2).isAir()).isTrue();
    assertThat(zombie.damageTaken()).isZero();
    assertThat(drops(STONE)).isEqualTo(1);
  }

  @Test
  void exploitEveryEndOfAThrowGivesBackAtMostTheOneBlockSpent() {
    learn(5);
    brickbat.getConfig().cooldownMillisBase = 0;
    brickbat.getConfig().cooldownMillisPerHeft = 0;
    FakeLiving zombie = zombie(0.5D, 61D, 3.5D);
    for (int throwIndex = 0; throwIndex < 5; throwIndex++) {
      punch(zombie);
    }
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
    assertThat(punch(zombie).isCancelled()).isFalse();
    List<ProjectileRef> thrown = balls();
    assertThat(thrown).hasSize(5);

    adapt.post(new ProjectileHitEvent(thrown.get(0), zombie, null, null));
    adapt.post(new ProjectileHitEvent(thrown.get(0), zombie, null, null));
    adapt.post(new ProjectileHitEvent(thrown.get(1), null, null, null));
    adapt.post(new EntityRemoveEvent(thrown.get(2), EntityRemoveEvent.Cause.UNLOAD));
    adapt.post(new PlayerQuitEvent(player));
    platform.scheduler().advance(200);
    brickbat.unregister();
    platform.scheduler().advance(20);

    assertThat(drops(STONE) + held(STONE)).isEqualTo(5);
  }

  /** A claim where nobody may hurt anything or build. */
  static final class NoFighting implements ProtectorPort {
    @Override
    public List<String> defaultProtectors() {
      return List.of("claims");
    }

    @Override
    public List<String> allProtectors() {
      return List.of("claims");
    }

    @Override
    public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim,
                          Adaptation<?> adaptation) {
      return check != ProtectionCheck.PVE && check != ProtectionCheck.PVP && check != ProtectionCheck.BLOCK_PLACE
          && check != ProtectionCheck.BLOCK_BREAK;
    }
  }

  private EntityDamageByEntityEvent punch(FakeLiving target) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 1D, false, FinalDamage.IDENTITY));
  }

  private void hold(String path, int amount) {
    FakeItem item = FakeItem.of(path);
    item.setAmount(amount);
    player.holding(item);
  }

  private void learn(int level) {
    profile.getData().getSkillLine("architect").setAdaptation(brickbat, level);
  }

  private ArchitectBrickbat adaptation() {
    Skill<?> skill = SkillRegistry.skills.get("architect");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation instanceof ArchitectBrickbat found) {
        return found;
      }
    }
    throw new AssertionError("Brickbat is not registered");
  }

  private FakeLiving zombie(double x, double y, double z) {
    AbilityHitLiving zombie = new AbilityHitLiving(adapt, brickbat, Id.minecraft("zombie"), new Loc(world, x, y, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return world.add(zombie);
  }

  private List<ProjectileRef> balls() {
    List<ProjectileRef> found = new ArrayList<>();
    for (EntityRef entity : world.entities()) {
      if (entity instanceof ProjectileRef ball && SNOWBALL.equals(ball.type()) && ball.isValid()) {
        found.add(ball);
      }
    }
    return found;
  }

  private ProjectileRef onlyBall() {
    List<ProjectileRef> found = balls();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private int drops(Id type) {
    int total = 0;
    for (ItemEntityRef item : world.items()) {
      if (type.equals(item.item().type())) {
        total += item.item().amount();
      }
    }
    return total;
  }

  private int held(Id type) {
    int total = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && type.equals(item.type())) {
        total += item.amount();
      }
    }
    return total;
  }
}
