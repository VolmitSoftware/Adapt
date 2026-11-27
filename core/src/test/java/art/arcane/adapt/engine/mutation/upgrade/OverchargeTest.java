package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.content.adaptation.sword.upgraded.TempestFlow;
import art.arcane.adapt.content.adaptation.sword.upgraded.Witherblade;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** GEN-2 on the reference upgrade: Witherblade spends the partner's Fuel for half again its wither. */
class OverchargeTest {
  private static final Id WITHER = Id.minecraft("wither");
  private static final Id SEEDS = Id.minecraft("wheat_seeds");
  private static final int BASE_TICKS = 60;

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;
  private Witherblade witherblade;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Reaper", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    kit.mutate("swords", "herbalism");
    witherblade = (Witherblade) kit.registry().getAdaptation(Id.adapt("swords/upgrade/witherblade"));
    profile.getData().getSkillLine("swords").setAdaptation(witherblade, 1);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void payingThePartnersFuelMakesTheEffectStronger() {
    player.inventory().setItem(9, new FakeItem(SEEDS, 10));

    FakeLiving target = strike();

    assertThat(target.effect(WITHER).durationTicks()).isEqualTo(BASE_TICKS * 3 / 2);
    assertThat(seeds()).isEqualTo(7);
  }

  @Test
  void withoutTheFuelTheEffectRunsAtItsNormalStrength() {
    FakeLiving target = strike();

    assertThat(target.effect(WITHER).durationTicks()).isEqualTo(BASE_TICKS);
  }

  @Test
  void thePlayerCanTurnItOffAndPaysNothing() {
    player.inventory().setItem(9, new FakeItem(SEEDS, 10));
    assertThat(PlayerPreferences.set(witherblade, profile, SkillUpgrade.OVERCHARGE, CommonPreferences.Toggle.OFF)).isTrue();

    FakeLiving target = strike();

    assertThat(target.effect(WITHER).durationTicks()).isEqualTo(BASE_TICKS);
    assertThat(seeds()).isEqualTo(10);
  }

  @Test
  void onlyAnUpgradeWithAnOverchargeOffersTheSwitchAndTheStatLine() {
    TempestFlow flow = (TempestFlow) kit.registry().getAdaptation(Id.adapt("swords/upgrade/tempest_flow"));
    StatLines with = new StatLines();
    StatLines without = new StatLines();
    witherblade.addLevelStats(1, with);
    witherblade.addStats(1, without);

    assertThat(witherblade.getPlayerPreferences()).contains(SkillUpgrade.OVERCHARGE);
    assertThat(flow.getPlayerPreferences()).doesNotContain(SkillUpgrade.OVERCHARGE);
    assertThat(with.lines()).hasSize(without.lines().size() + 1);
    assertThat(C.stripColor(with.lines().getLast())).contains("50%").contains("Overcharged Wither Duration");
  }

  private FakeLiving strike() {
    FakeLiving target = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    kit.hit(player, target, 4D, false);
    platform.scheduler().advance(2);
    return target;
  }

  private int seeds() {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(SEEDS)) {
        count += item.amount();
      }
    }
    return count;
  }
}
