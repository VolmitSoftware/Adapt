package art.arcane.adapt.content.adaptation.stealth.upgraded;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StealthUpgradesRuntimeTest {
  private static final Id INVISIBILITY = Id.minecraft("invisibility");

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
    player = platform.player("Shade", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillStealth.class, SkillSwords.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void deathMarkKillsAnUnawareMobOutright() {
    mutate();
    learn(DeathMark.class, 1);
    FakeLiving target = mob();
    player.setSneaking(true);

    EntityDamageByEntityEvent strike = strike(target, 1D);

    assertThat(strike.damage()).isCloseTo(target.health(), within(1.0E-9D));
    assertThat(stat("stealth.death-mark.executions")).isEqualTo(1D);
  }

  @Test
  void deathMarkRobsAnUnawareMobItCannotKill() {
    mutate();
    learn(DeathMark.class, 1);
    FakeLiving target = mob();
    target.setAttributeBase(Attributes.MAX_HEALTH, 500D);
    platform.host().animals().setLoot(target, List.of(FakeItem.of("emerald")));
    player.setSneaking(true);

    EntityDamageByEntityEvent strike = strike(target, 1D);
    platform.scheduler().advance(1);

    assertThat(strike.damage()).isLessThan(500D);
    assertThat(player.inventory().item(1).type()).isEqualTo(Id.minecraft("emerald"));
    assertThat(stat("stealth.death-mark.pockets-picked")).isEqualTo(1D);
  }

  @Test
  void deathMarkDoesNotRobABredMob() {
    mutate();
    learn(DeathMark.class, 1);
    FakeLiving target = mob();
    target.setAttributeBase(Attributes.MAX_HEALTH, 500D);
    platform.host().animals().setLoot(target, List.of(FakeItem.of("emerald")));
    MobOrigin.record(target, CreatureSpawnEvent.Reason.BREEDING);
    player.setSneaking(true);

    strike(target, 1D);
    platform.scheduler().advance(1);

    assertThat(player.inventory().contains(Id.minecraft("emerald"), 1)).isFalse();
  }

  @Test
  void deathMarkDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("stealth").setAdaptation(upgrade(DeathMark.class), 1);
    FakeLiving target = mob();
    player.setSneaking(true);

    assertThat(strike(target, 1D).damage()).isEqualTo(1D);
  }

  @Test
  void everyStealthMutationKeepsConcealmentAtItsTopLevelForFree() {
    mutate();
    StealthCore core = registry.getSkill("stealth").getAdaptations().stream().filter(StealthCore.class::isInstance)
        .map(StealthCore.class::cast).findFirst().orElseThrow();
    FakeLiving target = mob();
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(core.getActiveLevel(player)).isEqualTo(core.getMaxLevel());
    assertThat(core.isUndetected(player, target)).isTrue();
    assertThat(profile.getData().getUsedPower()).isZero();
  }

  @Test
  void umbralFormHidesTheUnseenSneakerAtWalkSpeedAndBuildsALayerTheNextHitBreaks() {
    mutate();
    UmbralForm umbral = learn(UmbralForm.class, 1);
    double sneakSpeed = player.attributeValue(Attributes.SNEAKING_SPEED);
    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, true));

    umbral.onTick();
    platform.scheduler().advance(1);

    assertThat(player.effect(INVISIBILITY)).isNotNull();
    assertThat(player.attributeValue(Attributes.SNEAKING_SPEED)).isGreaterThan(sneakSpeed);
    double armored = player.attributeValue(Attributes.ARMOR);
    assertThat(armored).isPositive();

    adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 4D,
        FinalDamage.IDENTITY));

    assertThat(player.attributeValue(Attributes.ARMOR)).isLessThan(armored);
    assertThat(player.effect(INVISIBILITY)).isNull();
  }

  @Test
  void phantomDecoySpendsAPelletAndSwapsPlacesOnTheSwapKeyForNoXp() {
    mutate();
    PhantomDecoy phantom = learn(PhantomDecoy.class, 1);
    StealthShadowDecoy decoys = StealthShadowDecoy.of(registry.getSkill("stealth"));
    player.inventory().addItem(pellets(phantom, 2));

    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.scheduler().advance(1);

    assertThat(decoys.hasActiveDecoy(player.uuid())).isTrue();
    assertThat(pelletCount(phantom)).isEqualTo(1);

    player.moveTo(new Loc(world, 6.5D, 61D, 0.5D));
    PlayerSwapHandItemsEvent swapKey = adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(),
        player.inventory().offHand()));
    adapt.awaitTicks(() -> player.location().x() < 1D, 2_000L);

    assertThat(swapKey.isCancelled()).isTrue();
    assertThat(decoys.activeDecoyAnchor(player.uuid()).location().x()).isCloseTo(6.5D, within(1.0E-9D));
    assertThat(XpPaid.by(profile, phantom)).isZero();
  }

  @Test
  void phantomDecoyNeedsAPellet() {
    mutate();
    learn(PhantomDecoy.class, 1);
    StealthShadowDecoy decoys = StealthShadowDecoy.of(registry.getSkill("stealth"));
    ItemRef plainStars = FakeItem.of("firework_star");
    plainStars.setAmount(3);
    player.inventory().addItem(plainStars);

    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, true));
    platform.scheduler().advance(1);

    assertThat(decoys.hasActiveDecoy(player.uuid())).isFalse();
  }

  @Test
  void smokePelletCraftsFourAtTheUpgradesFirstLevel() {
    PhantomDecoy phantom = upgrade(PhantomDecoy.class);
    MutationTool<?> pellet = phantom.getTools().getFirst();

    assertThat(pellet.id()).isEqualTo("tool-stealth-smoke-pellet");
    assertThat(phantom.getRecipes()).anySatisfy((RecipeSpec recipe) -> {
      assertThat(recipe.key()).isEqualTo(Id.adapt("tool-stealth-smoke-pellet"));
      assertThat(pellet.is(recipe.result())).isTrue();
      assertThat(recipe.result().amount()).isEqualTo(4);
    });
  }

  @Test
  void shadowStepLandsBehindAnUnawareCreatureForOnePelletAndOpensDeathMark() {
    mutate();
    PhantomDecoy phantom = learn(PhantomDecoy.class, 1);
    learn(DeathMark.class, 1);
    floor();
    FakeLiving target = mob();
    player.holding(pellets(phantom, 3));
    player.setSneaking(true);

    step(target);

    assertThat(player.location().x()).isCloseTo(2.5D, within(1.0E-9D));
    assertThat(player.location().z()).isLessThan(target.location().z());
    assertThat(player.location().yaw()).isEqualTo(target.location().yaw());
    assertThat(pelletCount(phantom)).isEqualTo(2);
    assertThat(stat("stealth.phantom-decoy.shadow-steps")).isEqualTo(1D);

    player.setSneaking(false);
    EntityDamageByEntityEvent strike = strike(target, 1D);

    assertThat(strike.damage()).isCloseTo(target.health(), within(1.0E-9D));
  }

  @Test
  void aSeenStrikeWithoutAShadowStepIsNoOpening() {
    mutate();
    learn(DeathMark.class, 1);
    FakeLiving target = mob();
    player.setSneaking(false);

    assertThat(strike(target, 1D).damage()).isEqualTo(1D);
  }

  @Test
  void shadowStepCoolsDownAndNeverSpendsAPelletWhenItRefuses() {
    mutate();
    PhantomDecoy phantom = learn(PhantomDecoy.class, 1);
    floor();
    FakeLiving target = mob();
    player.holding(pellets(phantom, 3));
    player.setSneaking(true);

    step(target);
    Loc landed = player.location();
    player.moveTo(new Loc(world, 0.5D, 61D, 0.5D));
    step(target);

    assertThat(landed.z()).isLessThan(target.location().z());
    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
    assertThat(pelletCount(phantom)).isEqualTo(2);
  }

  @Test
  void shadowStepRefusesWhenSeenOutOfRangeIntoAWallOrAClaim() {
    mutate();
    PhantomDecoy phantom = learn(PhantomDecoy.class, 1);
    floor();
    player.holding(pellets(phantom, 3));

    FakeLiving watching = mob();
    player.setSneaking(false);
    step(watching);
    assertThat(player.location().x()).as("seen").isCloseTo(0.5D, within(1.0E-9D));

    player.setSneaking(true);
    FakeLiving far = world.add(mobAt(new Loc(world, 14.5D, 61D, 0.5D)));
    step(far);
    assertThat(player.location().x()).as("out of range").isCloseTo(0.5D, within(1.0E-9D));

    world.set(2, 61, -1, "stone").set(2, 62, -1, "stone").set(2, 62, 0, "stone");
    step(watching);
    assertThat(player.location().x()).as("wall").isCloseTo(0.5D, within(1.0E-9D));
    world.set(2, 61, -1, "air").set(2, 62, -1, "air").set(2, 62, 0, "air");

    AdaptPorts.install(ProtectorPort.class, new Claim());
    try {
      step(watching);
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }
    assertThat(player.location().x()).as("claim").isCloseTo(0.5D, within(1.0E-9D));
    assertThat(pelletCount(phantom)).isEqualTo(3);
  }

  @Test
  void aPelletDoesNothingForAPlayerWhoHasNotMutated() {
    PhantomDecoy phantom = upgrade(PhantomDecoy.class);
    profile.getData().getSkillLine("stealth").setAdaptation(phantom, 3);
    floor();
    FakeLiving target = mob();
    player.holding(pellets(phantom, 3));
    player.setSneaking(true);

    step(target);

    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
    assertThat(pelletCount(phantom)).isEqualTo(3);
  }

  @Test
  void shadowStepBehindABossOpensNoExecution() {
    mutate();
    PhantomDecoy phantom = learn(PhantomDecoy.class, 1);
    learn(DeathMark.class, 1);
    floor();
    FakeLiving boss = mob();
    boss.trait(EntityTrait.BOSS);
    player.holding(pellets(phantom, 1));
    player.setSneaking(true);

    step(boss);
    player.setSneaking(false);

    assertThat(player.location().z()).isLessThan(boss.location().z());
    assertThat(strike(boss, 1D).damage()).isLessThan(boss.health());
  }

  @Test
  void deathMarkRobsAMobOnlyOnce() {
    mutate();
    learn(DeathMark.class, 1);
    FakeLiving target = mob();
    target.setAttributeBase(Attributes.MAX_HEALTH, 500D);
    platform.host().animals().setLoot(target, List.of(FakeItem.of("emerald")));
    player.setSneaking(true);

    strike(target, 1D);
    strike(target, 1D);
    platform.scheduler().advance(1);

    assertThat(stat("stealth.death-mark.pockets-picked")).isEqualTo(1D);
    assertThat(emeralds()).isEqualTo(1);
  }

  @Test
  void deathMarkPaysNothingForASpawnerMob() {
    mutate();
    DeathMark mark = learn(DeathMark.class, 1);
    FakeLiving robbed = mob();
    robbed.setAttributeBase(Attributes.MAX_HEALTH, 500D);
    platform.host().animals().setLoot(robbed, List.of(FakeItem.of("emerald")));
    MobOrigin.record(robbed, CreatureSpawnEvent.Reason.SPAWNER);
    FakeLiving executed = mob();
    MobOrigin.record(executed, CreatureSpawnEvent.Reason.SPAWNER);
    player.setSneaking(true);

    strike(robbed, 1D);
    EntityDamageByEntityEvent kill = strike(executed, 1D);
    platform.scheduler().advance(1);

    assertThat(emeralds()).isZero();
    assertThat(kill.damage()).isCloseTo(executed.health(), within(1.0E-9D));
    assertThat(XpPaid.by(profile, mark)).isZero();
  }

  private void step(FakeLiving target) {
    adapt.post(new PlayerInteractEntityEvent(player, target, Hand.MAIN_HAND, null));
    platform.scheduler().advance(2);
  }

  private void floor() {
    for (int x = -2; x <= 6; x++) {
      for (int z = -4; z <= 4; z++) {
        world.set(x, 60, z, "stone");
      }
    }
  }

  private ItemRef pellets(PhantomDecoy phantom, int amount) {
    ItemRef stack = ((SmokePellet) phantom.getTools().getFirst()).create(new MutationTool.NoData());
    stack.setAmount(amount);
    return stack;
  }

  private int pelletCount(PhantomDecoy phantom) {
    MutationTool<?> pellet = phantom.getTools().getFirst();
    int count = 0;
    for (ItemRef stack : player.inventory().contents()) {
      if (pellet.is(stack)) {
        count += stack.amount();
      }
    }
    return count;
  }

  private int emeralds() {
    int count = 0;
    for (ItemRef stack : player.inventory().contents()) {
      if (stack != null && stack.type().equals(Id.minecraft("emerald"))) {
        count += stack.amount();
      }
    }
    return count;
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("stealth", "swords")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("stealth"), registry.getSkill("swords"), "Shade"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("stealth").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("stealth").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private double stat(String key) {
    return profile.getData().getStat(key);
  }

  private FakeLiving mob() {
    return world.add(mobAt(new Loc(world, 2.5D, 61D, 0.5D)));
  }

  private TestMob mobAt(Loc at) {
    TestMob mob = new TestMob(at);
    mob.trait(EntityTrait.MONSTER);
    mob.trait(EntityTrait.ENEMY);
    mob.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    return mob;
  }

  private EntityDamageByEntityEvent strike(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }

  /** A claim the player cannot enter, covering everything. */
  private static final class Claim implements ProtectorPort {
    @Override
    public List<String> defaultProtectors() {
      return List.of("claims");
    }

    @Override
    public List<String> allProtectors() {
      return List.of("claims");
    }

    @Override
    public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location,
                          EntityRef victim, Adaptation<?> adaptation) {
      return check != ProtectionCheck.REGION;
    }
  }

  private static final class TestMob extends FakeLiving implements MobRef {
    private LivingRef target;
    private boolean aware = true;

    private TestMob(Loc location) {
      super(Id.minecraft("pillager"), location);
    }

    @Override
    public LivingRef target() {
      return target;
    }

    @Override
    public void setTarget(LivingRef target) {
      this.target = target;
    }

    @Override
    public boolean isAware() {
      return aware;
    }

    @Override
    public void setAware(boolean aware) {
      this.aware = aware;
    }

    @Override
    public boolean pathTo(Loc destination, double speed) {
      return false;
    }

    @Override
    public boolean hasPath() {
      return false;
    }

    @Override
    public void stopPathing() {
    }

    @Override
    public void lookAt(Loc target) {
    }

    @Override
    public boolean rangedAttack(LivingRef target) {
      return false;
    }

    @Override
    public boolean isPlayerCreated() {
      return false;
    }

    @Override
    public void setPlayerCreated(boolean created) {
    }

    @Override
    public int weatheringStage() {
      return 0;
    }

    @Override
    public boolean isWaxed() {
      return false;
    }

    @Override
    public void setWeathering(int stage, boolean waxed) {
    }

    @Override
    public CopperGolemState copperGolemState() {
      return null;
    }

    @Override
    public void setCopperGolemState(CopperGolemState state) {
    }

    @Override
    public void setBurnsInDaylight(boolean burns) {
    }

    @Override
    public MobGoalsRef goals() {
      return null;
    }
  }
}
