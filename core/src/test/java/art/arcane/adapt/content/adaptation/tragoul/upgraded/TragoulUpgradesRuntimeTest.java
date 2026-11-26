package art.arcane.adapt.content.adaptation.tragoul.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSpirit;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class TragoulUpgradesRuntimeTest {
  private static final Id PLAYER_ATTACK = Id.minecraft("player_attack");
  private static final Id THORNS = Id.minecraft("thorns");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id BONE = Id.minecraft("bone");

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
    player = platform.world().add(platform.player("Bloodbinder", 0.5D, 61D, 0.5D));
    player.holding(FakeItem.tool("iron_sword", 250));
    player.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    adapt = TestAdapt.start(platform, List.of(SkillTragOul.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void crimsonTideHealsFromDamageDealt() {
    mutate();
    CrimsonTide tide = learn(CrimsonTide.class, 1);
    player.setHealth(10D);
    FakeLiving target = platform.world().add(zombie(2.5D));

    hit(target, player, 4D);
    platform.scheduler().advance(2);

    assertThat(player.health()).isCloseTo(10D + (4D * tide.getHealPercent(1)), within(1.0E-9D));
  }

  @Test
  void crimsonTideDrainsAndReflectsOntoAttackers() {
    mutate();
    CrimsonTide tide = learn(CrimsonTide.class, 1);
    player.setHealth(10D);
    FakeLiving attacker = platform.world().add(zombie(2.5D));

    hit(player, attacker, 3D);
    platform.scheduler().advance(4);

    assertThat(attacker.damageTaken()).isCloseTo(tide.getDrainDamage(1) + tide.getReflectDamage(1), within(1.0E-9D));
    assertThat(attacker.lastDamageType()).isEqualTo(THORNS);
    assertThat(player.health()).isCloseTo(10D + tide.getDrainDamage(1), within(1.0E-9D));
  }

  @Test
  void plagueNovaSpreadsAPoisonedKillsAffliction() {
    mutate();
    learn(PlagueNova.class, 1);
    Mob carrier = platform.world().add(new Mob(2.5D, player));
    carrier.addEffect(EffectInstance.of(POISON, 200, 0));
    Mob neighbour = platform.world().add(new Mob(5.5D, null));

    hit(carrier, player, 1D);
    platform.scheduler().advance(4);
    adapt.post(new EntityDeathEvent(carrier, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
    platform.scheduler().advance(15);

    assertThat(neighbour.effect(POISON)).isNotNull();
    assertThat(neighbour.effect(POISON).amplifier()).isEqualTo(1);
  }

  @Test
  void crimsonTideDoesNothingWithoutAMutation() {
    profile.getData().getSkillLine("tragoul").setAdaptation(upgrade(CrimsonTide.class), 1);
    player.setHealth(10D);
    FakeLiving attacker = platform.world().add(zombie(2.5D));

    hit(player, attacker, 3D);
    hit(attacker, player, 4D);
    platform.scheduler().advance(4);

    assertThat(attacker.damageTaken()).isZero();
    assertThat(player.health()).isEqualTo(10D);
  }

  @Test
  void plagueNovaBurstsAroundAKill() {
    mutate();
    learn(PlagueNova.class, 1);
    FakeLiving victim = killedBy(zombie(2.5D), player);
    FakeLiving bystander = platform.world().add(zombie(4.5D));

    adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
    platform.scheduler().advance(15);

    assertThat(bystander.damageTaken()).isPositive();
  }

  @Test
  void plagueNovaLaunchesALanceAtTheNextEnemy() {
    mutate();
    PlagueNova nova = learn(PlagueNova.class, 1);
    nova.getConfig().novaRadiusBase = 1D;
    nova.getConfig().novaRadiusPerLevel = 0D;
    FakeLiving victim = killedBy(zombie(2.5D), player);
    victim.setLastDamage(new LastDamage(DamageCause.ENTITY_ATTACK, player, 4D));
    FakeLiving bystander = platform.world().add(zombie(8.5D));

    adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
    platform.scheduler().advance(40);

    assertThat(bystander.damageTaken()).isCloseTo(4D * nova.getConfig().lanceUnarmoredDamageMultiplier, within(1.0E-9D));
  }

  @Test
  void deathlessBonesAbsorbPartOfAHeavyHit() {
    mutate();
    Deathless deathless = learn(Deathless.class, 1);
    player.inventory().addItem(new FakeItem(Id.minecraft("bone"), 4));

    EntityDamageEvent hit = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 10D,
        FinalDamage.IDENTITY));

    assertThat(hit.damage()).isCloseTo(10D * (1D - deathless.getAbsorbPercent(1)), within(1.0E-9D));
    assertThat(player.inventory().contains(Id.minecraft("bone"), 4)).isFalse();
  }

  @Test
  void lichsBargainLeavesAKillingBlowAt1HpAndNeverKillsYouLater() {
    mutate();
    learn(Deathless.class, 1);
    player.setHealth(4D);

    EntityDamageEvent lethal = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 50D,
        FinalDamage.IDENTITY));
    platform.scheduler().advance(20 * 120);

    assertThat(lethal.isCancelled()).isTrue();
    assertThat(player.health()).isEqualTo(1D);
    assertThat(player.isDead()).isFalse();
  }

  @Test
  void lichsBargainRaisesItsServantsFreeOnTheSaveAndOnlyOncePerCooldown() {
    mutate();
    Deathless lich = learn(Deathless.class, 2);
    FakePlayer lichPlayer = raisingPlayer();
    lichPlayer.inventory().addItem(new FakeItem(BONE, 8));
    lichPlayer.setHealth(4D);

    adapt.post(new EntityDamageEvent(lichPlayer, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 50D, FinalDamage.IDENTITY));
    lichPlayer.setHealth(4D);
    EntityDamageEvent second = adapt.post(new EntityDamageEvent(lichPlayer, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 50D,
        FinalDamage.IDENTITY));

    assertThat(pack().servants(lichPlayer.uuid())).hasSize(lich.getServantCap(2)).hasSize(2);
    assertThat(second.isCancelled()).isFalse();
    assertThat(bones(lichPlayer)).as("one bone absorbed the first hit, none paid for servants").isEqualTo(7);
  }

  @Test
  void lichsBargainCountsServantsWorkingAsGolemsTowardItsCap() {
    mutate();
    Deathless lich = learn(Deathless.class, 2);
    FakePlayer lichPlayer = raisingPlayer();
    lichPlayer.setHealth(4D);
    UUID legionnaire = UUID.randomUUID();
    ServantFrame.enlist(legionnaire, lichPlayer.uuid());
    try {
      adapt.post(new EntityDamageEvent(lichPlayer, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 50D, FinalDamage.IDENTITY));

      assertThat(pack().servants(lichPlayer.uuid())).hasSize(lich.getServantCap(2) - 1);
    } finally {
      ServantFrame.discharge(legionnaire);
    }
  }

  @Test
  void aSkeletalServantSummonFindsNoRoomWhenServantsWorkingAsGolemsFillTheCap() {
    FakePlayer summoner = raisingPlayer();
    adapt.core().getAdaptServer().getPlayer(summoner).getData().getSkillLine("tragoul").setAdaptation(pack(), 1);
    summoner.holding(new FakeItem(BONE, 32));
    List<UUID> legion = new ArrayList<>();
    for (int count = 0; count < 16; count++) {
      UUID legionnaire = UUID.randomUUID();
      ServantFrame.enlist(legionnaire, summoner.uuid());
      legion.add(legionnaire);
    }
    try {
      raiseByHand(summoner);
      assertThat(pack().servants(summoner.uuid())).isEmpty();

      legion.forEach(ServantFrame::discharge);
      platform.scheduler().advance(20 * 60);
      raiseByHand(summoner);
      assertThat(pack().servants(summoner.uuid())).hasSize(1);
    } finally {
      legion.forEach(ServantFrame::discharge);
    }
  }

  @Test
  void lichsBargainRaisesByHandForFourBonesAndHealthUpToTheSharedCap() {
    mutate();
    learn(Deathless.class, 1);
    FakePlayer lichPlayer = raisingPlayer();
    lichPlayer.inventory().addItem(new FakeItem(BONE, 12));
    lichPlayer.setHealth(20D);

    raiseByHand(lichPlayer);
    platform.scheduler().advance(40);
    raiseByHand(lichPlayer);
    lichPlayer.setHealth(4D);
    adapt.post(new EntityDamageEvent(lichPlayer, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 50D, FinalDamage.IDENTITY));

    assertThat(pack().servants(lichPlayer.uuid())).hasSize(1);
    assertThat(bones(lichPlayer)).as("four for the one servant, one absorbed the lethal hit").isEqualTo(7);
  }

  @Test
  void lichsBargainNeverRaisesByHandWithoutTheBonesOrTheHealth() {
    mutate();
    learn(Deathless.class, 3);
    FakePlayer lichPlayer = raisingPlayer();
    lichPlayer.inventory().addItem(new FakeItem(BONE, 3));
    lichPlayer.setHealth(20D);

    raiseByHand(lichPlayer);
    lichPlayer.inventory().addItem(new FakeItem(BONE, 1));
    lichPlayer.setHealth(2D);
    raiseByHand(lichPlayer);

    assertThat(pack().servants(lichPlayer.uuid())).isEmpty();
    assertThat(bones(lichPlayer)).isEqualTo(4);
    assertThat(lichPlayer.health()).isEqualTo(2D);
  }

  @Test
  void lichsBargainServantsDropNoLootOrXp() {
    mutate();
    learn(Deathless.class, 1);
    FakePlayer lichPlayer = raisingPlayer();
    lichPlayer.inventory().addItem(new FakeItem(BONE, 4));
    raiseByHand(lichPlayer);
    LivingRef servant = (LivingRef) pack().servants(lichPlayer.uuid()).getFirst();
    List<ItemRef> drops = new ArrayList<>(List.of(new FakeItem(BONE, 2), new FakeItem(Id.minecraft("arrow"), 3)));

    EntityDeathEvent death = adapt.post(new EntityDeathEvent(servant, player, player, player, DamageCause.ENTITY_ATTACK,
        drops, 5));

    assertThat(death.drops()).isEmpty();
    assertThat(death.droppedExp()).isZero();
  }

  @Test
  void crimsonTideStoresHealingPastFullHealthAsBloodThatRaisingByHandSpendsFirst() {
    mutate();
    CrimsonTide tide = learn(CrimsonTide.class, 1);
    learn(Deathless.class, 1);
    FakePlayer lichPlayer = raisingPlayer();
    lichPlayer.inventory().addItem(new FakeItem(BONE, 4));
    lichPlayer.setHealth(20D);
    FakeLiving target = platform.world().add(zombie(lichPlayer, 2.5D));

    tide.getConfig().healPercentBase = 1D;
    hit(target, lichPlayer, 4D);
    platform.scheduler().advance(2);
    double stored = tide.blood(lichPlayer);
    raiseByHand(lichPlayer);

    assertThat(stored).isGreaterThan(2D);
    assertThat(lichPlayer.health()).isEqualTo(20D);
    assertThat(tide.blood(lichPlayer)).isCloseTo(stored - 2D, within(1.0E-9D));
  }

  @Test
  void crimsonTideStoresNoBloodFromAFarmedMobAndCapsTheReservoir() {
    mutate();
    CrimsonTide tide = learn(CrimsonTide.class, 1);
    player.setHealth(20D);
    FakeLiving farmed = platform.world().add(zombie(2.5D));
    MobOrigin.record(farmed, CreatureSpawnEvent.Reason.SPAWNER);
    FakeLiving natural = platform.world().add(zombie(3.5D));

    hit(farmed, player, 10D);
    platform.scheduler().advance(2);
    double fromFarmed = tide.blood(player);
    tide.getConfig().healCapPerSecondBase = 1000D;
    tide.getConfig().healPercentBase = 10D;
    hit(natural, player, 10D);
    platform.scheduler().advance(2);

    assertThat(fromFarmed).isZero();
    assertThat(tide.blood(player)).isEqualTo(tide.getReservoirHealth(1)).isEqualTo(8D);
  }

  @Test
  void crimsonTideBloodDrainsOutOfCombat() {
    mutate();
    CrimsonTide tide = learn(CrimsonTide.class, 1);
    tide.getConfig().combatWindowMillis = 0L;
    tide.getConfig().bloodDrainIntervalMillis = 1L;
    player.setHealth(20D);
    FakeLiving natural = platform.world().add(zombie(2.5D));

    hit(natural, player, 10D);
    platform.scheduler().advance(2);
    long deadline = System.currentTimeMillis() + 2000L;
    while (tide.blood(player) > 0D && System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }

    assertThat(tide.blood(player)).isZero();
  }

  @Test
  void deathlessDoesNotSaveAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("tragoul").setAdaptation(upgrade(Deathless.class), 1);
    player.setHealth(4D);

    EntityDamageEvent lethal = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, 50D,
        FinalDamage.IDENTITY));

    assertThat(lethal.isCancelled()).isFalse();
    assertThat(player.effect(TragoulSpirit.INVISIBILITY)).isNull();
  }

  @Test
  void lichsBargainLeavesTheHeritageSummonToAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("tragoul").setAdaptation(upgrade(Deathless.class), 3);

    assertThat(upgrade(Deathless.class).raisesByHand(player)).isFalse();
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("tragoul", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("tragoul"), registry.getSkill("stealth"), "Bloodbinder"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("tragoul").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("tragoul").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakePlayer raisingPlayer() {
    FakeWorld world = spy(platform.world());
    doAnswer(invocation -> {
      Loc at = invocation.getArgument(1);
      Mob servant = new Mob(Id.minecraft("skeleton"), at, null);
      invocation.<Consumer<EntityRef>>getArgument(2).accept(servant);
      return platform.world().add(servant);
    }).when(world).spawn(eq(Id.minecraft("skeleton")), any(Loc.class), any());
    FakePlayer lichPlayer = platform.world().add(platform.host().join(new FakePlayer("Lich", new Loc(world, 0.5D, 61D, 0.5D))));
    lichPlayer.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    AdaptPlayer lich = adapt.join(lichPlayer);
    lich.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("tragoul", "stealth")) {
      PlayerSkillLine line = lich.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(lich.getData(), registry.getSkill("tragoul"), registry.getSkill("stealth"), "Lich"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    for (Adaptation<?> upgrade : registry.getSkill("tragoul").getUpgrades()) {
      int level = profile.getData().getSkillLine("tragoul").getAdaptationLevel(upgrade.getName());
      if (level > 0) {
        lich.getData().getSkillLine("tragoul").setAdaptation(upgrade, level);
      }
    }
    return lichPlayer;
  }

  private void raiseByHand(FakePlayer who) {
    who.setSneaking(true);
    adapt.post(new PlayerInteractEvent(who, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, who.inventory().mainHand(),
        null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private TragoulSkeletalServant pack() {
    return registry.getSkill("tragoul").getAdaptations().stream().filter(TragoulSkeletalServant.class::isInstance)
        .map(TragoulSkeletalServant.class::cast).findFirst().orElseThrow();
  }

  private static int bones(FakePlayer who) {
    int count = 0;
    for (ItemRef item : who.inventory().contents()) {
      if (item != null && BONE.equals(item.type())) {
        count += item.amount();
      }
    }
    return count;
  }

  private FakeLiving zombie(FakePlayer near, double x) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(near.location().world(), x, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return zombie;
  }

  private FakeLiving zombie(double x) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return zombie;
  }

  private FakeLiving killed(FakeLiving victim, PlayerRef killer) {
    return new FakeLiving(victim.type(), victim.location()) {
      @Override
      public PlayerRef killer() {
        return killer;
      }
    };
  }

  private FakeLiving killedBy(FakeLiving victim, PlayerRef killer) {
    FakeLiving corpse = new FakeLiving(victim.type(), victim.location()) {
      @Override
      public PlayerRef killer() {
        return killer;
      }
    };
    corpse.trait(EntityTrait.MONSTER);
    corpse.trait(EntityTrait.ENEMY);
    return corpse;
  }

  private void hit(FakeLiving target, FakeLiving damager, double damage) {
    adapt.post(new EntityDamageByEntityEvent(target, damager, damager, DamageCause.ENTITY_ATTACK, PLAYER_ATTACK, damage,
        false, FinalDamage.IDENTITY));
  }

  private final class Mob extends FakeLiving implements MobRef {
    private final PlayerRef killer;
    private LivingRef target;

    private Mob(double x, PlayerRef killer) {
      this(Id.minecraft("zombie"), x, killer);
      trait(EntityTrait.MONSTER);
      trait(EntityTrait.ENEMY);
    }

    private Mob(Id type, double x, PlayerRef killer) {
      this(type, new Loc(platform.world(), x, 61D, 0.5D), killer);
    }

    private Mob(Id type, Loc at, PlayerRef killer) {
      super(type, at);
      this.killer = killer;
    }

    @Override
    public PlayerRef killer() {
      return killer;
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
      return true;
    }

    @Override
    public void setAware(boolean aware) {
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
