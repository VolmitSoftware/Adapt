package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DiscoveryUpgradesRuntimeTest {
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
    player = platform.player("Seeker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class, SkillSwords.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void omniscienceReadsTheCreatureInSightOntoTheReadout() {
    mutate();
    Omniscience omniscience = learn(Omniscience.class, 1);
    platform.world().add(zombie(0.5D, 3.5D));

    assertThat(omniscience.readout(player, 1)).contains("Zombie");
  }

  @Test
  void omniscienceCountsTheChestsAndSpawnersInSight() {
    mutate();
    Omniscience omniscience = learn(Omniscience.class, 1);
    platform.world().set(0, 61, 4, "chest").set(3, 61, -2, "trapped_chest").set(-3, 61, 2, "spawner")
        .set(-2, 61, -3, "barrel");

    String readout = omniscience.readout(player, 1);

    assertThat(readout).contains(AdaptLanguage.text(DiscoveryMessages.OMNISCIENCE_CHESTS, trusted("count", C.WHITE + "2")));
    assertThat(readout).contains(AdaptLanguage.text(DiscoveryMessages.OMNISCIENCE_SPAWNERS, trusted("count", C.WHITE + "1")));
  }

  @Test
  void unityPrimeFeedsTheLowestSkillFromVanillaXp() {
    mutate();
    UnityPrime unity = learn(UnityPrime.class, 3);
    PlayerSkillLine stealth = profile.getData().getSkillLine("stealth");
    double before = stealth.getXp();

    adapt.post(new PlayerExpChangeEvent(player, 3));
    platform.scheduler().advance(2);
    stealth.flushXpPool(null);

    assertThat(UnityPrime.lowestSkills(profile.getData(), 1)).containsExactly(stealth);
    assertThat(stealth.getXp() - before).isCloseTo(unity.getOrbXp(3, 3), within(1.0E-6D));
  }

  @Test
  void unityPrimeRaisesAllGainsForEveryLevelledSkill() {
    mutate();
    UnityPrime unity = learn(UnityPrime.class, 1);
    PlayerData playerData = profile.getData();
    double before = playerData.computeXpMultiplier(player);

    unity.boostOwned(player);

    assertThat(playerData.computeXpMultiplier(player)).isGreaterThan(before);
  }

  @Test
  void explorersLuckBanksAPayoutBonusAndSpeciesDamageOnAFirstKill() {
    mutate();
    ExplorersLuck luck = learn(ExplorersLuck.class, 1);
    FakeLiving zombie = platform.world().add(killedBy(player));

    adapt.post(death(zombie));
    platform.scheduler().advance(2);

    assertThat(profile.getData().getStat(ExplorersLuck.BANK)).isGreaterThan(0D);
    assertThat(hit(zombie, 5D).damage()).isCloseTo(5D + 0.25D, within(1.0E-9D));
    assertThat(luck.getPayout(1)).isGreaterThan(1D);
  }

  @Test
  void explorersLuckDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("discovery").setAdaptation(upgrade(ExplorersLuck.class), 1);
    FakeLiving zombie = platform.world().add(killedBy(player));

    adapt.post(death(zombie));
    platform.scheduler().advance(2);

    assertThat(profile.getData().getStat(ExplorersLuck.BANK)).isZero();
    assertThat(hit(zombie, 5D).damage()).isEqualTo(5D);
  }

  @Test
  void unityPrimeDoesNothingForAPlayerWhoHasNotMutated() {
    UnityPrime unity = upgrade(UnityPrime.class);
    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);
    PlayerData playerData = profile.getData();
    double before = playerData.computeXpMultiplier(player);

    unity.boostOwned(player);

    assertThat(unity.getLevel(player)).isZero();
    assertThat(playerData.computeXpMultiplier(player)).isEqualTo(before);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("discovery", "swords")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    profile.getData().getSkillLine("stealth");
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("discovery"), registry.getSkill("swords"), "Seeker"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("discovery").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("discovery").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeLiving zombie(double x, double z) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), x, 61D, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return zombie;
  }

  private FakeLiving killedBy(PlayerRef killer) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D)) {
      @Override
      public PlayerRef killer() {
        return killer;
      }
    };
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return zombie;
  }

  private EntityDeathEvent death(FakeLiving victim) {
    return new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0);
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }
}
