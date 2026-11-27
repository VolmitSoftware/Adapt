package art.arcane.adapt.content.adaptation.axe.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.AbilityHitLiving;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
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

class AxeUpgradesRuntimeTest {
  private static final Id OAK_LOG = Id.minecraft("oak_log");
  private static final Id OAK_LEAVES = Id.minecraft("oak_leaves");
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private static final Id IRON_AXE = Id.minecraft("iron_axe");

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
    player = platform.player("Reaver", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_axe", 250));
    adapt = TestAdapt.start(platform, List.of(SkillAxes.class, SkillStealth.class));
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
    learn(Timberfall.class, 3);
    learn(SunderingCleave.class, 3);
    plantTree();
    player.setSneaking(true);
    FakeLiving primary = zombie(0.5D, 2.5D);
    FakeLiving bystander = zombie(1.0D, 3.0D);

    fellFrom(world.blockAt(3, 61, 3));
    hit(primary, 10D);
    platform.scheduler().advance(4);

    assertThat(world.blockAt(3, 64, 3).type()).isEqualTo(OAK_LOG);
    assertThat(world.blockAt(3, 67, 3).type()).isEqualTo(OAK_LEAVES);
    assertThat(primary.attributeValue(Attributes.ARMOR)).isEqualTo(4D);
    assertThat(bystander.damageTaken()).isZero();
  }

  @Test
  void timberfallFellsTheWholeTreeWithItsLeaves() {
    mutate();
    learn(Timberfall.class, 1);
    plantTree();
    player.setSneaking(true);

    fellFrom(world.blockAt(3, 61, 3));
    platform.scheduler().advance(4);

    for (int y = 62; y <= 66; y++) {
      assertThat(world.blockAt(3, y, 3).isAir()).as("log at y " + y).isTrue();
    }
    for (int x = 2; x <= 4; x++) {
      for (int z = 2; z <= 4; z++) {
        assertThat(world.blockAt(x, 67, z).isAir()).as("leaves at " + x + ", " + z).isTrue();
      }
    }
    assertThat(world.blockAt(2, 66, 3).isAir()).isTrue();
  }

  @Test
  void timberfallSendsLogAndLeafDropsToTheInventory() {
    mutate();
    learn(Timberfall.class, 1);
    BlockRef log = world.set(3, 61, 3, "air").blockAt(3, 61, 3);
    ItemEntityRef drop = world.createItemEntity(log.center().position(), FakeItem.of("oak_log"));

    BlockDropItemEvent event = adapt.post(new BlockDropItemEvent(player, log, FakeBlockState.of("oak_log"), List.of(drop)));

    assertThat(event.items()).isEmpty();
    assertThat(player.inventory().contains(OAK_LOG, 1)).isTrue();
  }

  @Test
  void sunderingCleaveHitsTheArcAndShredsArmorOnEveryTarget() {
    mutate();
    SunderingCleave cleave = learn(SunderingCleave.class, 1);
    FakeLiving primary = zombie(0.5D, 2.5D);
    FakeLiving bystander = zombie(1.0D, 3.0D);

    hit(primary, 10D);
    platform.scheduler().advance(4);

    double shredded = 4D - cleave.getShredPerStack(1);
    assertThat(bystander.damageTaken()).isCloseTo(10D * cleave.getDamageShare(1), within(1.0E-9D));
    assertThat(primary.attributeValue(Attributes.ARMOR)).isCloseTo(shredded, within(1.0E-9D));
    assertThat(bystander.attributeValue(Attributes.ARMOR)).isCloseTo(shredded, within(1.0E-9D));
  }

  @Test
  void worldsplitterSmashesWhereTheAxeLandsThenReturnsIt() {
    mutate();
    learn(Worldsplitter.class, 1);
    FakeLiving bystander = zombie(0.5D, 2.5D);
    FakeItem held = FakeItem.tool("iron_axe", 250);
    held.setUnbreakable(true);
    player.holding(held);

    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    ProjectileRef axe = thrownAxe();
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();

    adapt.post(new ProjectileHitEvent(axe, null, world.blockAt(0, 60, 1), BlockFace.UP));
    platform.scheduler().advance(6);

    assertThat(bystander.damageTaken()).isGreaterThan(0D);
    assertThat(player.inventory().mainHand().type()).isEqualTo(IRON_AXE);
  }

  @Test
  void theReturningAxeStrikesEachCreatureOnItsPathOnceAtHalfDamage() {
    mutate();
    Worldsplitter splitter = learn(Worldsplitter.class, 1);
    FakeLiving near = zombie(0.5D, 3.5D);
    FakeLiving far = zombie(0.5D, 6.5D);
    FakeLiving aside = zombie(6.5D, 4.5D);

    throwAndLand(FakeItem.tool("iron_axe", 250), 12.5D);

    double half = 9D * splitter.getDamageMultiplier(1) * 0.5D;
    assertThat(near.damageTaken()).isCloseTo(half, within(1.0E-9D));
    assertThat(far.damageTaken()).isCloseTo(half, within(1.0E-9D));
    assertThat(aside.damageTaken()).isZero();
    assertThat(player.inventory().mainHand().type()).isEqualTo(IRON_AXE);
  }

  @Test
  void neitherTheThrowNorTheReturnIsTakenForAMeleeHit() {
    mutate();
    Worldsplitter splitter = learn(Worldsplitter.class, 1);
    AbilityHitLiving struck = abilityTarget(splitter, 0.5D, 12.5D);
    AbilityHitLiving near = abilityTarget(splitter, 0.5D, 3.5D);
    player.holding(FakeItem.tool("iron_axe", 250));
    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    ProjectileRef thrown = thrownAxe();
    thrown.teleport(new Loc(world, 0.5D, 61D, 12.5D), TeleportCause.PLUGIN);

    adapt.post(new ProjectileHitEvent(thrown, struck, null, null));
    platform.scheduler().advance(6);

    assertThat(struck.damageTaken()).isPositive();
    assertThat(near.damageTaken()).isPositive();
    assertThat(struck.meleeHits()).isZero();
    assertThat(near.meleeHits()).isZero();
  }

  @Test
  void theReturnStopsStrikingBeforeTheAxeWouldBreak() {
    mutate();
    learn(Worldsplitter.class, 1);
    FakeLiving near = zombie(0.5D, 3.5D);
    FakeLiving far = zombie(0.5D, 6.5D);

    throwAndLand(FakeItem.tool("iron_axe", 250).withDamage(245), 12.5D);

    assertThat(far.damageTaken()).isGreaterThan(0D);
    assertThat(near.damageTaken()).isZero();
    assertThat(player.inventory().mainHand().type()).isEqualTo(IRON_AXE);
  }

  @Test
  void theReturnLeavesArmorAloneWithoutSunderingCleave() {
    mutate();
    learn(Worldsplitter.class, 1);
    FakeLiving near = zombie(0.5D, 3.5D);

    throwAndLand(FakeItem.tool("iron_axe", 250), 12.5D);

    assertThat(near.damageTaken()).isGreaterThan(0D);
    assertThat(near.attributeValue(Attributes.ARMOR)).isEqualTo(4D);
  }

  @Test
  void theReturnShredsArmorWithSunderingCleaveStacks() {
    mutate();
    learn(Worldsplitter.class, 1);
    SunderingCleave cleave = learn(SunderingCleave.class, 1);
    FakeLiving near = zombie(0.5D, 3.5D);

    throwAndLand(FakeItem.tool("iron_axe", 250), 12.5D);

    assertThat(near.attributeValue(Attributes.ARMOR)).isCloseTo(4D - cleave.getShredPerStack(1), within(1.0E-9D));
  }

  @Test
  void theReturnPassesYourOwnPetsAndYou() {
    mutate();
    learn(Worldsplitter.class, 1);
    FakeLiving pet = zombie(0.5D, 3.5D);
    pet.setTameOwner(player.uuid());
    FakeLiving enemy = zombie(0.5D, 6.5D);

    throwAndLand(FakeItem.tool("iron_axe", 250), 12.5D);

    assertThat(pet.damageTaken()).isZero();
    assertThat(enemy.damageTaken()).isGreaterThan(0D);
    assertThat(player.health()).isEqualTo(player.maxHealth());
  }

  private void throwAndLand(ItemRef axe, double landingZ) {
    player.holding(axe);
    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    ProjectileRef thrown = thrownAxe();
    thrown.teleport(new Loc(world, 0.5D, 61D, landingZ), TeleportCause.PLUGIN);
    adapt.post(new ProjectileHitEvent(thrown, null, world.blockAt(0, 60, (int) landingZ), BlockFace.UP));
    platform.scheduler().advance(6);
  }

  private ProjectileRef thrownAxe() {
    for (EntityRef entity : world.entities()) {
      if (entity instanceof ProjectileRef projectile && SNOWBALL.equals(projectile.type())) {
        return projectile;
      }
    }
    throw new AssertionError("No axe was thrown");
  }

  private void plantTree() {
    for (int y = 61; y <= 66; y++) {
      world.set(3, y, 3, "oak_log");
    }
    for (int x = 2; x <= 4; x++) {
      for (int z = 2; z <= 4; z++) {
        world.set(x, 67, z, "oak_leaves");
        if (x != 3 || z != 3) {
          world.set(x, 66, z, "oak_leaves");
        }
      }
    }
  }

  private void fellFrom(BlockRef base) {
    adapt.post(new BlockBreakEvent(player, base, true, 0));
    world.set(base.x(), base.y(), base.z(), "air");
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("axes", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("axes"), registry.getSkill("stealth"), "Reaver"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = SkillHost.of(registry.getSkill("axes")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
    profile.getData().getSkillLine("axes").setAdaptation(upgrade, level);
    return upgrade;
  }

  private FakeLiving zombie(double x, double z) {
    FakeLiving zombie = world.add(new FakeLiving(Id.minecraft("zombie"), new Loc(world, x, 61D, z)));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    zombie.setAttributeBase(Attributes.ARMOR, 4D);
    return zombie;
  }

  private AbilityHitLiving abilityTarget(Adaptation<?> probe, double x, double z) {
    AbilityHitLiving target = world.add(new AbilityHitLiving(adapt, probe, Id.minecraft("zombie"), new Loc(world, x, 61D, z)));
    target.trait(EntityTrait.MONSTER);
    target.trait(EntityTrait.ENEMY);
    return target;
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }
}
