package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RangedFetchShotTest extends EngineTestBase {
  private static final DataKey<Byte> LAUNCH_SNEAK = DataKey.adapt("fetch-launch-sneak", DataCodecs.BYTE);
  private static final Id REBOUND_LEVEL = Id.adapt("rift_pearl_rebound_level");
  private static final Id REBOUNDED = Id.adapt("rift_pearl_rebounded");

  @Test
  void launchByPlayerWithoutFetchShotLeavesThePearlUnclaimed() {
    FakeProjectile pearl = launch(fetchShot(0, true), true);

    assertThat(pearl.data().keys()).isEmpty();
    assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data(), REBOUND_LEVEL, REBOUNDED)).isTrue();
  }

  @Test
  void launchWithoutSneakingStoresIntentWithoutClaimingThePearl() {
    FakeProjectile pearl = launch(fetchShot(2, true), false);

    assertThat(pearl.data().keys()).containsOnly(LAUNCH_SNEAK.id());
    assertThat(pearl.data().get(LAUNCH_SNEAK)).isEqualTo((byte) 0);
    assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data(), REBOUND_LEVEL, REBOUNDED)).isTrue();
  }

  @Test
  void launchWithSneakRuleOffStoresIntentWithoutClaimingThePearl() {
    FakeProjectile pearl = launch(fetchShot(2, false), true);

    assertThat(pearl.data().keys()).containsOnly(LAUNCH_SNEAK.id());
    assertThat(pearl.data().get(LAUNCH_SNEAK)).isEqualTo((byte) 1);
    assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data(), REBOUND_LEVEL, REBOUNDED)).isTrue();
  }

  @Test
  void sneakingLaunchWithFetchShotActiveMarksTheProjectile() {
    FakeProjectile pearl = launch(fetchShot(2, true), true);

    assertThat(pearl.data().keys()).containsOnly(LAUNCH_SNEAK.id());
    assertThat(pearl.data().get(LAUNCH_SNEAK)).isEqualTo((byte) 1);
  }

  private static RangedFetchShot fetchShot(int level, boolean sneakRule) {
    return TestContent.adaptation(Id.adapt("ranged"), () -> new TestFetchShot(level, sneakRule));
  }

  private FakeProjectile launch(RangedFetchShot adaptation, boolean sneaking) {
    PlayerRef player = mock(PlayerRef.class);
    FakeProjectile pearl = new FakeProjectile(Id.minecraft("ender_pearl"), new Loc(new FakeWorld("world"), 0D, 64D, 0D));
    when(player.isSneaking()).thenReturn(sneaking);
    pearl.setShooter(player);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(true);
      adaptation.on(new ProjectileLaunchEvent(pearl));
    }
    return pearl;
  }

  private static final class TestFetchShot extends RangedFetchShot {
    private final int level;
    private final boolean sneakRule;

    private TestFetchShot(int level, boolean sneakRule) {
      this.level = level;
      this.sneakRule = sneakRule;
    }

    @Override
    public boolean hasActiveAdaptation(PlayerRef player) {
      return level > 0;
    }

    @Override
    public boolean preferenceEnabled(PlayerRef player, PlayerPreference<CommonPreferences.Toggle> preference) {
      return preference != RangedPreferences.SNEAK || sneakRule;
    }
  }
}
