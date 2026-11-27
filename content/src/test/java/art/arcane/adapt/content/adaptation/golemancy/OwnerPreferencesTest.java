package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.player.PlayerDirectory;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.player.PreferenceStore;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerPreferencesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final Id ADAPTATION = Id.adapt("golem/gatherer_core");

  private final AdaptationConfig config = new AdaptationConfig();
  private final PlayerDirectory players = mock(PlayerDirectory.class);
  private Adaptation<?> adaptation;
  private OwnerPreferences owners;
  private MockedStatic<AdaptApi> api;

  @BeforeEach
  void setUp() {
    adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getId()).thenReturn(ADAPTATION);
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(CommonPreferences.ENABLED, GolemancyPreferences.MAGNET));
    doReturn(config).when(adaptation).getConfig();
    doAnswer(invocation -> PlayerPreferences.resolveSaved(adaptation, invocation.getArgument(1), invocation.getArgument(0),
        invocation.<PlayerPreference<CommonPreferences.Toggle>>getArgument(2)))
        .when(adaptation).preferenceFor(anyInt(), any(), any());
    PlayerPreferences.validate(adaptation, config);
    api = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    api.when(AdaptApi::players).thenReturn(players);
    lenient().when(players.find(any(UUID.class))).thenReturn(Optional.empty());
    owners = new OwnerPreferences(adaptation);
  }

  @AfterEach
  void closeApi() {
    api.close();
  }

  @Test
  void anOnlineOwnersSavedChoiceIsReadAndKeptForWhenTheyGoOffline() {
    online(store("OFF"), 4);

    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();

    when(players.find(OWNER)).thenReturn(Optional.empty());
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
  }

  @Test
  void aChoiceRememberedAtQuitHoldsWhileTheOwnerIsOfflineWithoutAnEarlierRead() {
    PlayerProfile player = online(store("OFF"), 4);

    owners.remember(player);
    when(players.find(OWNER)).thenReturn(Optional.empty());

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
    PreferenceStore store = store(null);
    online(store, 4);
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isTrue();

    when(store.saved(ADAPTATION, "magnet")).thenReturn(Optional.of("OFF"));

    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
  }

  @Test
  void theServerLockOverridesAnOfflineOwnersLastKnownChoice() throws Exception {
    online(store("OFF"), 4);
    assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isFalse();
    when(players.find(OWNER)).thenReturn(Optional.empty());
    AdaptConfig locked = TomlCodec.fromToml("playerSettingsEnabled = false\n", AdaptConfig.class);

    try (MockedStatic<AdaptConfig> configuration = mockStatic(AdaptConfig.class)) {
      configuration.when(AdaptConfig::get).thenReturn(locked);
      assertThat(owners.enabled(OWNER, GolemancyPreferences.MAGNET)).isTrue();
    }
  }

  @Test
  void readingAnOfflineOwnerNeverLoadsTheirPlayerData() {
    owners.enabled(OWNER, GolemancyPreferences.MAGNET);

    verify(players, never()).peek(any(UUID.class), any(PlayerSection.class));
  }

  private PlayerProfile online(PreferenceStore store, int level) {
    PlayerProfile player = mock(PlayerProfile.class);
    lenient().when(player.uuid()).thenReturn(OWNER);
    when(player.preferences()).thenReturn(store);
    when(adaptation.getLevel(player)).thenReturn(level);
    when(players.find(OWNER)).thenReturn(Optional.of(player));
    return player;
  }

  private static PreferenceStore store(String magnet) {
    PreferenceStore store = mock(PreferenceStore.class);
    lenient().when(store.saved(any(Id.class), anyString())).thenReturn(Optional.empty());
    lenient().when(store.saved(ADAPTATION, "magnet")).thenReturn(Optional.ofNullable(magnet));
    return store;
  }
}
