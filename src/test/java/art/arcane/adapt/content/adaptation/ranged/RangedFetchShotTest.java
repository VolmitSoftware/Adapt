package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.projectile.ProjectileClaims;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RangedFetchShotTest extends AdaptTestBase {
  private static final NamespacedKey LAUNCH_SNEAK = NamespacedKey.fromString("adapt:fetch-launch-sneak");
  private static final NamespacedKey REBOUND_LEVEL = new NamespacedKey("adapt", "rift_pearl_rebound_level");
  private static final NamespacedKey REBOUNDED = new NamespacedKey("adapt", "rift_pearl_rebounded");

  @Test
  void launchByPlayerWithoutFetchShotLeavesThePearlUnclaimed() {
    Map<NamespacedKey, Object> stored = new HashMap<>();
    EnderPearl pearl = launch(new TestFetchShot(0, true), true, stored);

    assertThat(stored).isEmpty();
    assertThat(ProjectileClaims.isUnclaimed(pearl, REBOUND_LEVEL, REBOUNDED)).isTrue();
  }

  @Test
  void launchWithoutSneakingByActivePlayerClaimsThePearlWithZero() {
    Map<NamespacedKey, Object> stored = new HashMap<>();
    EnderPearl pearl = launch(new TestFetchShot(2, true), false, stored);

    assertThat(stored).containsOnlyKeys(LAUNCH_SNEAK).containsEntry(LAUNCH_SNEAK, (byte) 0);
    assertThat(ProjectileClaims.isUnclaimed(pearl, REBOUND_LEVEL, REBOUNDED)).isFalse();
  }

  @Test
  void launchWithSneakRuleOffByActivePlayerStillClaimsThePearl() {
    Map<NamespacedKey, Object> stored = new HashMap<>();
    EnderPearl pearl = launch(new TestFetchShot(2, false), true, stored);

    assertThat(stored).containsOnlyKeys(LAUNCH_SNEAK).containsEntry(LAUNCH_SNEAK, (byte) 1);
    assertThat(ProjectileClaims.isUnclaimed(pearl, REBOUND_LEVEL, REBOUNDED)).isFalse();
  }

  @Test
  void sneakingLaunchWithFetchShotActiveMarksTheProjectile() {
    Map<NamespacedKey, Object> stored = new HashMap<>();
    launch(new TestFetchShot(2, true), true, stored);

    assertThat(stored).containsOnlyKeys(LAUNCH_SNEAK).containsEntry(LAUNCH_SNEAK, (byte) 1);
  }

  private EnderPearl launch(RangedFetchShot adaptation, boolean sneaking, Map<NamespacedKey, Object> stored) {
    Player player = mock(Player.class);
    EnderPearl pearl = mock(EnderPearl.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(player.isSneaking()).thenReturn(sneaking);
    when(pearl.getShooter()).thenReturn(player);
    when(pearl.getPersistentDataContainer()).thenReturn(data);
    when(data.getKeys()).thenAnswer(call -> new HashSet<>(stored.keySet()));
    doAnswer(call -> {
      stored.put(call.getArgument(0), call.getArgument(2));
      return null;
    }).when(data).set(any(NamespacedKey.class), any(), any());
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
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
    public int getActiveLevel(Player player) {
      return level;
    }

    @Override
    public boolean preferenceEnabled(Player player, PlayerPreference<CommonPreferences.Toggle> preference) {
      return preference != RangedPreferences.SNEAK || sneakRule;
    }
  }
}
