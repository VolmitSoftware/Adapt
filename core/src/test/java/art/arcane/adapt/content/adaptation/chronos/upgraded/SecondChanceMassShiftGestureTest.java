package art.arcane.adapt.content.adaptation.chronos.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sneak and swap hands is Mass Shift's for a player who has it; Second Chance's rewind then waits for a killing blow. */
class SecondChanceMassShiftGestureTest {
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
    player = platform.player("Shifter", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillStealth.class, SkillKinetics.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("chronos", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("chronos"), registry.getSkill("stealth"), "Shifter"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    SecondChance secondChance = registry.getSkill("chronos").getUpgrades().stream()
        .filter(SecondChance.class::isInstance).map(SecondChance.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("chronos").setAdaptation(secondChance, 1);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void oneGestureShiftsFormAndNeverAlsoRewinds() {
    profile.getData().getSkillLine("kinetics").setAdaptation(registry.getAdaptation("kinetics-mass-shift"), 1);
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    Loc away = new Loc(platform.world(), 12.5D, 61D, 0.5D).withRotation(0F, -45F);
    player.teleport(away, TeleportCause.PLUGIN);
    double health = player.attributeValue(Attributes.MAX_HEALTH);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent gesture = adapt.post(swap());
    platform.scheduler().advance(1);

    assertThat(gesture.isCancelled()).isTrue();
    assertThat(player.location().distance(mark)).isGreaterThan(10D);
    assertThat(player.attributeValue(Attributes.MAX_HEALTH)).isNotEqualTo(health);
  }

  @Test
  void withoutMassShiftTheGestureStillRewinds() {
    Loc mark = player.location();
    adapt.post(new PlayerMoveEvent(player, mark, mark));
    player.teleport(new Loc(platform.world(), 12.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    player.setSneaking(true);

    adapt.post(swap());
    platform.scheduler().advance(1);

    assertThat(player.location().distance(mark)).isLessThan(0.01D);
  }

  private PlayerSwapHandItemsEvent swap() {
    return new PlayerSwapHandItemsEvent(player, player.inventory().offHand(), player.inventory().mainHand());
  }
}
