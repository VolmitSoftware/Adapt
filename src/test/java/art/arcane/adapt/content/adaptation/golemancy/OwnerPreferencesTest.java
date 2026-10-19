package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerPreferencesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

  private final AdaptationConfig config = new AdaptationConfig();
  private Adaptation<?> adaptation;
  private AdaptServer server;
  private OwnerPreferences owners;

  @BeforeEach
  void setUp() {
    adaptation = mock(Adaptation.class);
    server = mock(AdaptServer.class);
    when(adaptation.getName()).thenReturn("golem-gatherer-core");
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(CommonPreferences.ENABLED, GolemancyPreferences.MAGNET));
    when(adaptation.getServer()).thenReturn(server);
    doReturn(config).when(adaptation).getConfig();
    PlayerPreferences.validate(adaptation, config);
    owners = new OwnerPreferences(adaptation);
  }

  @Test
  void anOnlineOwnersSavedChoiceIsReadAndKeptForWhenTheyGoOffline() {
    online(magnetOff(), 4);

    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();

    when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(null);
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
  }

  @Test
  void aChoiceRememberedAtQuitHoldsWhileTheOwnerIsOfflineWithoutAnEarlierRead() {
    AdaptPlayer player = online(magnetOff(), 4);

    owners.remember(OWNER, player);
    when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(null);

    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
  }

  @Test
  void anOfflineOwnerWithNoKnownChoiceUsesTheServerDefault() {
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isTrue();

    config.playerPreferences.get("magnet").defaultValue = "OFF";
    config.playerPreferences.get("magnet").allowedValues = List.of("ON", "OFF");
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
  }

  @Test
  void anOwnerWhoChangesTheirChoiceWhileOnlineIsReadFresh() {
    PlayerData data = new PlayerData();
    online(data, 4);
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isTrue();

    data.getPreferences().set("golem-gatherer-core", "magnet", "OFF");

    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
  }

  @Test
  void theServerLockOverridesAnOfflineOwnersLastKnownChoice() throws Exception {
    online(magnetOff(), 4);
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
    when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(null);
    AdaptConfig locked = TomlCodec.fromToml("playerSettingsEnabled = false\n", AdaptConfig.class);

    try (MockedStatic<AdaptConfig> configuration = mockStatic(AdaptConfig.class)) {
      configuration.when(AdaptConfig::get).thenReturn(locked);
      assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isTrue();
    }
  }

  @Test
  void readingAnOfflineOwnerNeverLoadsTheirPlayerData() {
    owners.enabled(OWNER, GolemancyPreferences.MAGNET);

    verify(server, never()).peekData(any(UUID.class));
  }

  private AdaptPlayer online(PlayerData data, int level) {
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(player.getData()).thenReturn(data);
    when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(player);
    when(adaptation.getLevel(player)).thenReturn(level);
    return player;
  }

  private static PlayerData magnetOff() {
    PlayerData data = new PlayerData();
    data.getPreferences().set("golem-gatherer-core", "magnet", "OFF");
    return data;
  }
}
