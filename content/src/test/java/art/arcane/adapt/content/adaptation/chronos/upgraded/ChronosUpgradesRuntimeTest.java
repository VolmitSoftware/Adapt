package art.arcane.adapt.content.adaptation.chronos.upgraded;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.api.mutation.SkillPair;

import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
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

class ChronosUpgradesRuntimeTest {
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id AMETHYST_SHARD = Id.minecraft("amethyst_shard");
  private static final DataKey<String> SECOND_CHANCE_DEBT = DataKey.adapt("chronos_second_chance_debt", DataCodecs.STRING);
  private static final DataKey<String> BORROWED_TIME_DEBT = DataKey.adapt("chronos_borrowed_time_debt", DataCodecs.STRING);

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
    player = platform.player("Timekeeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillStealth.class));
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
    MutatedSkill mutated = registry.getMutatedSkill(PairKeys.parse("chronos+stealth"));

    assertThat(mutated.getAdaptations()).extracting((Adaptation<?> adaptation) -> adaptation.getId().toString()).startsWith(
        "adapt:chronos/upgrade/second_chance", "adapt:chronos/upgrade/time_lord", "adapt:chronos/upgrade/frozen_moment",
        "adapt:stealth/upgrade/umbral_form", "adapt:stealth/upgrade/death_mark", "adapt:stealth/upgrade/phantom_decoy");
  }

  @Test
  void secondChanceDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("chronos").setAdaptation(upgrade(SecondChance.class), 3);
    player.setSneaking(true);

    assertThat(adapt.post(swap()).isCancelled()).isFalse();
  }

  @Test
  void secondChanceRewindsYouWhenYouTriggerIt() {
    mutate();
    learn(SecondChance.class, 1);
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    player.teleport(new Loc(platform.world(), 12.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    player.setSneaking(true);

    adapt.post(swap());
    platform.scheduler().advance(1);

    assertThat(player.location().distance(mark)).isLessThan(0.01D);
  }

  @Test
  void secondChanceKeepsItsChargeWhenThereIsNothingToRewindTo() {
    mutate();
    learn(SecondChance.class, 1);
    player.setSneaking(true);
    adapt.post(swap());
    platform.scheduler().advance(1);

    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    player.teleport(new Loc(platform.world(), 12.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    adapt.post(swap());
    platform.scheduler().advance(1);

    assertThat(player.location().distance(mark)).isLessThan(0.01D);
  }

  @Test
  void secondChanceSpendsTheRewindOnAKillingBlowAndKeepsTheHealthFromBeforeIt() {
    mutate();
    learn(SecondChance.class, 1);
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    player.teleport(new Loc(platform.world(), 12.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    player.setHealth(3D);

    EntityDamageEvent blow = adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 10D,
        FinalDamage.IDENTITY));
    platform.scheduler().advance(1);

    assertThat(blow.isCancelled()).isTrue();
    assertThat(player.location().distance(mark)).isLessThan(0.01D);
    assertThat(player.health()).isEqualTo(3D);
  }

  @Test
  void aSavedKillingBlowLeavesAtMostHalfYourHealth() {
    mutate();
    learn(SecondChance.class, 1);
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    player.setHealth(18D);

    EntityDamageEvent blow = adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 100D,
        FinalDamage.IDENTITY));
    platform.scheduler().advance(1);

    assertThat(blow.isCancelled()).isTrue();
    assertThat(player.health()).isEqualTo(10D);
  }

  @Test
  void secondChanceOwesPartOfEachHitLater() {
    mutate();
    learn(SecondChance.class, 3);

    EntityDamageEvent hit = adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 10D,
        FinalDamage.IDENTITY));

    assertThat(hit.finalDamage()).isCloseTo(6.5D, within(1.0E-9D));
    assertThat(player.data().get(SECOND_CHANCE_DEBT)).isNotNull();
  }

  @Test
  void aRewindClearsTheDebtStillOwed() {
    mutate();
    learn(SecondChance.class, 3);
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 10D, FinalDamage.IDENTITY));
    player.setSneaking(true);

    adapt.post(swap());
    platform.scheduler().advance(1);

    assertThat(player.data().get(SECOND_CHANCE_DEBT)).isNull();
    assertThat(player.data().get(BORROWED_TIME_DEBT)).isNull();
  }

  @Test
  void loggingOutNeverClearsTheDebt() {
    mutate();
    SecondChance chance = learn(SecondChance.class, 3);
    adapt.post(new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), 10D, FinalDamage.IDENTITY));
    String owed = player.data().get(SECOND_CHANCE_DEBT);

    adapt.post(new PlayerQuitEvent(player));
    assertThat(player.data().get(SECOND_CHANCE_DEBT)).isEqualTo(owed);
    adapt.post(new PlayerJoinEvent(player));
    player.setHealth(20D);
    chance.onTick();
    platform.scheduler().advance(1);

    assertThat(player.health()).isLessThan(20D);
  }

  @Test
  void secondChanceRestoresTheRecordedHealthOnAChosenRewind() {
    mutate();
    learn(SecondChance.class, 1);
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    player.teleport(new Loc(platform.world(), 12.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    player.setHealth(6D);
    player.setSneaking(true);

    adapt.post(swap());
    platform.scheduler().advance(1);

    assertThat(player.health()).isEqualTo(20D);
  }

  @Test
  void timeLordMakesBeneficialPotionsLastLonger() {
    mutate();
    TimeLord lord = learn(TimeLord.class, 1);
    EffectInstance speed = EffectInstance.of(SPEED, 200, 0);
    player.addEffect(speed);

    adapt.post(new EntityPotionEffectEvent(player, SPEED, null, speed, EntityPotionEffectEvent.Action.ADDED,
        EntityPotionEffectEvent.Cause.POTION_DRINK));
    platform.scheduler().advance(1);

    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(200 + (int) Math.round(200 * lord.getPotionExtension(1)));
  }

  @Test
  void timeLordBottlesHoldMoreThanTimeInABottleAllows() {
    mutate();
    TimeLord lord = learn(TimeLord.class, 1);
    double originalCap = lord.getBottleCapacity(1) / lord.getConfig().bottleCapacityMultiplier;
    ItemRef bottle = ChronoTimeBottle.withStoredSeconds(originalCap);
    player.inventory().setItem(0, bottle);

    lord.onTick();
    platform.scheduler().advance(1);

    assertThat(ChronoTimeBottle.getStoredSeconds(player.inventory().item(0))).isGreaterThan(originalCap);
  }

  @Test
  void frozenMomentThrowsAFieldThatRootsWhatYouHitInside() {
    mutate();
    learn(FrozenMoment.class, 1);
    player.setSneaking(true);
    player.holding(new FakeItem(AMETHYST_SHARD, 4));
    FakeLiving zombie = platform.world().add(zombie(6.5D));

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    FakeProjectile shard = player.launched().getLast();
    shard.teleport(new Loc(platform.world(), 6.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    platform.world().set(6, 60, 0, "stone");
    adapt.post(new ProjectileHitEvent(shard, null,
        platform.world().blockAt(6, 60, 0), BlockFace.UP));
    adapt.post(new EntityDamageByEntityEvent(zombie, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 2D, false, FinalDamage.IDENTITY));

    assertThat(player.inventory().mainHand().amount()).isEqualTo(3);
    assertThat(zombie.effect(SLOWNESS)).isNotNull();
    assertThat(zombie.effect(SLOWNESS).amplifier()).isEqualTo(upgrade(FrozenMoment.class).getConfig().rootAmplifier);
  }

  @Test
  void frozenMomentLeavesHitsOutsideAFieldAlone() {
    mutate();
    learn(FrozenMoment.class, 1);
    FakeLiving zombie = platform.world().add(zombie(2.5D));

    adapt.post(new EntityDamageByEntityEvent(zombie, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 2D, false, FinalDamage.IDENTITY));

    assertThat(zombie.effect(SLOWNESS)).isNull();
  }

  private PlayerSwapHandItemsEvent swap() {
    return new PlayerSwapHandItemsEvent(player, player.inventory().offHand(), player.inventory().mainHand());
  }

  private FakeLiving zombie(double x) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return zombie;
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("chronos", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("chronos"), registry.getSkill("stealth"), "Timekeeper"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("chronos").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("chronos")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
