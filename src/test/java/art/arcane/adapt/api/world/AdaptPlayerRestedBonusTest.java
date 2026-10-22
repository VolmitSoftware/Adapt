package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AdaptPlayerRestedBonusTest extends AdaptTestBase {
  @Test
  void onlyOfflineTimeEarnsOneRestedBonusAndOtherSourcesRemain() {
    long now = TimeUnit.DAYS.toMillis(10);
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sql = mock(AdaptConfig.SqlSettings.class);
    when(config.getSql()).thenReturn(sql);
    when(config.isLoginBonus()).thenReturn(true);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(dataFolder);
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class);
         MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<M> clock = mockStatic(M.class, CALLS_REAL_METHODS)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      clock.when(M::ms).thenReturn(now);

      PlayerData onlineProfile = new PlayerData();
      onlineProfile.setLastLogin(now - TimeUnit.DAYS.toMillis(2));
      onlineProfile.setLastLogout(now - TimeUnit.SECONDS.toMillis(30));
      AdaptPlayer onlineRuntime = runtime(onlineProfile);
      onlineRuntime.loggedIn();

      assertThat(onlineProfile.getMultipliers()).isEmpty();
      assertThat(onlineProfile.getLastLogin()).isEqualTo(now);
      assertThat(onlineProfile.getLastLogout()).isZero();

      PlayerData restedProfile = new PlayerData();
      restedProfile.setLastLogin(now - TimeUnit.DAYS.toMillis(2));
      restedProfile.setLastLogout(now - TimeUnit.HOURS.toMillis(12));
      XPMultiplier quest = XPMultiplier.owned("quest", 0.2D, TimeUnit.HOURS.toMillis(2));
      restedProfile.globalXPMultiplier(quest);
      restedProfile.globalXPMultiplier(XPMultiplier.owned("rested-login", 0.1D, TimeUnit.MINUTES.toMillis(5)));
      AdaptPlayer restedRuntime = runtime(restedProfile);
      restedRuntime.loggedIn();

      assertThat(restedProfile.getMultipliers()).hasSize(2).contains(quest);
      XPMultiplier rested = restedProfile.getMultipliers().stream()
          .filter(multiplier -> "rested-login".equals(multiplier.getSource()))
          .findFirst().orElseThrow();
      assertThat(rested.getMultiplier()).isEqualTo(0.25D);
      assertThat(rested.getGoodFor()).isEqualTo(now + TimeUnit.HOURS.toMillis(1));
      assertThat(restedProfile.getLastLogout()).isZero();

      clock.when(M::ms).thenReturn(now + TimeUnit.MINUTES.toMillis(1));
      restedRuntime.loggedIn();

      assertThat(restedProfile.getMultipliers()).containsExactly(quest, rested);
      assertThat(rested.getGoodFor()).isEqualTo(now + TimeUnit.HOURS.toMillis(1));
    }
  }

  private static AdaptPlayer runtime(PlayerData profile) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    return new AdaptPlayer(player, profile);
  }
}
