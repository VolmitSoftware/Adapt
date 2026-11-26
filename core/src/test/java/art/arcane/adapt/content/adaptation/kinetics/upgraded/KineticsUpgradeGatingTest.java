package art.arcane.adapt.content.adaptation.kinetics.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsUpgradeGatingTest {
  private static final Id SLOWNESS = Id.minecraft("slowness");

  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
    PlatformSupport.reset();
  }

  @Test
  void lancerStaysWhereSpearsDoNotExist() {
    start(FakeGameContent.v1_21_1());

    assertThat(adapt.core().getAdaptServer().getSkillRegistry().getSkill("kinetics").getUpgrades())
        .extracting(Adaptation::getName)
        .containsExactly("upgraded-kinetics-comet", "upgraded-kinetics-lancer", "upgraded-kinetics-featherweight");
  }

  @Test
  void lancerPinsWithATridentWhereSpearsDoNotExist() {
    TestPlatform platform = start(FakeGameContent.v1_21_1());
    FakePlayer player = mutatedLancer(platform);
    player.holding(FakeItem.tool("trident", 250));
    FakeLiving target = zombie(platform);

    hit(player, target);
    platform.scheduler().advance(2);

    assertThat(target.effect(SLOWNESS)).isNotNull();
  }

  @Test
  void lancerIgnoresTridentsWhereSpearsExist() {
    TestPlatform platform = start(FakeGameContent.v26_3());
    FakePlayer player = mutatedLancer(platform);
    player.holding(FakeItem.tool("trident", 250));
    FakeLiving target = zombie(platform);

    hit(player, target);
    platform.scheduler().advance(2);

    assertThat(target.effect(SLOWNESS)).isNull();
  }

  private TestPlatform start(FakeGameContent game) {
    TestPlatform platform = TestPlatform.builder(data).build();
    platform.host().runGame(game);
    adapt = TestAdapt.start(platform, List.of(SkillKinetics.class, SkillStealth.class));
    return platform;
  }

  private FakePlayer mutatedLancer(TestPlatform platform) {
    FakePlayer player = platform.player("Lancer", 0.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("kinetics", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("kinetics"), registry.getSkill("stealth"), "Lancer"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    Lancer lancer = registry.getSkill("kinetics").getUpgrades().stream().filter(Lancer.class::isInstance)
        .map(Lancer.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("kinetics").setAdaptation(lancer, 1);
    return player;
  }

  private FakeLiving zombie(TestPlatform platform) {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return platform.world().add(zombie);
  }

  private void hit(FakePlayer player, FakeLiving target) {
    adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 10D, false, FinalDamage.IDENTITY));
  }
}
