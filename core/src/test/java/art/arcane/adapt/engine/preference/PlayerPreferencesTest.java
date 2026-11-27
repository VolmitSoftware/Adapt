package art.arcane.adapt.engine.preference;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerPreferencesTest {
  private static final PlayerPreference<Mode> MODE = PlayerPreference.of("activation", Mode.class, Mode.MANUAL,
      List.of(new PlayerPreference.Choice<>(Mode.MANUAL, Text.literal("Manual"), Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Mode.REACTIVE, Text.literal("Reactive"), Id.minecraft("shield"), 2)))
      .labelled(Text.literal("Activation"));

  private Adaptation<?> adaptation;
  private AdaptationConfig config;

  @BeforeEach
  void prepareAdaptation() {
    adaptation = mock(Adaptation.class);
    config = new AdaptationConfig();
    lenient().when(adaptation.getId()).thenReturn(Id.adapt("rift/blink"));
    when(adaptation.getMaxLevel()).thenReturn(5);
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(MODE));
    doReturn(config).when(adaptation).getConfig();
    PlayerPreferences.validate(adaptation, config);
  }

  @Test
  void literalLabelsShowAsWritten() {
    assertThat(PlayerPreferences.label("example.adaptation.geology.core_sample", MODE)).isEqualTo(Text.literal("Activation"));
    assertThat(PlayerPreferences.label("example.adaptation.geology.core_sample", MODE, Mode.REACTIVE))
        .isEqualTo(Text.literal("Reactive"));
    assertThat(PlayerPreferences.shown(PlayerPreferences.label("example.adaptation.geology.core_sample", MODE)))
        .isEqualTo("Activation");
    assertThat(PlayerPreferences.shown("<white>", Text.literal("Reactive"))).isEqualTo("<white>Reactive");
  }

  @Test
  void unlabelledPreferencesUseTheConventionBelowTheirOwner() {
    PlayerPreference<Mode> plain = PlayerPreference.of("delivery", Mode.class, Mode.MANUAL);

    assertThat(PlayerPreferences.label("example.adaptation.geology.core_sample", plain))
        .isEqualTo(Text.key("example.adaptation.geology.core_sample.preference.delivery.name"));
    assertThat(PlayerPreferences.label("example.adaptation.geology.core_sample", plain, Mode.REACTIVE))
        .isEqualTo(Text.key("example.adaptation.geology.core_sample.preference.delivery.choice.reactive"));
  }

  @Test
  void policyOrderAndTomlRemainStableAcrossInputOrdersAndRoundTrips() throws IOException {
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(CommonPreferences.ENABLED, MODE));
    PreferencePolicy mode = policy(Mode.REACTIVE, Mode.MANUAL, Mode.REACTIVE);
    mode.playerEditable = false;
    Map<String, PreferencePolicy> configured = new LinkedHashMap<>();
    configured.put("retained-policy", policy(Mode.MANUAL, Mode.MANUAL));
    configured.put("enabled", PreferencePolicy.defaults(CommonPreferences.ENABLED));
    configured.put("activation", mode);
    config.playerPreferences = configured;

    PlayerPreferences.validate(adaptation, config);
    String canonical = TomlCodec.toToml(config, "adaptation:rift-blink");

    assertThat(config.playerPreferences.keySet()).containsExactly("activation", "enabled", "retained-policy");
    assertThat(config.playerPreferences.get("activation").defaultValue).isEqualTo("REACTIVE");
    assertThat(config.playerPreferences.get("activation").playerEditable).isFalse();
    assertThat(config.playerPreferences.get("activation").allowedValues).containsExactly("MANUAL", "REACTIVE");
    assertThatThrownBy(() -> config.playerPreferences.clear()).isInstanceOf(UnsupportedOperationException.class);
    configured.clear();
    assertThat(config.playerPreferences).hasSize(3);

    config.playerPreferences = new LinkedHashMap<>(config.playerPreferences);
    PreferencePolicy activation = config.playerPreferences.remove("activation");
    config.playerPreferences.put("activation", activation);
    PlayerPreferences.validate(adaptation, config);
    assertThat(TomlCodec.toToml(config, "adaptation:rift-blink")).isEqualTo(canonical);

    AdaptationConfig restored = TomlCodec.fromToml(canonical, AdaptationConfig.class);
    PlayerPreferences.validate(adaptation, restored);
    assertThat(TomlCodec.toToml(restored, "adaptation:rift-blink")).isEqualTo(canonical);
  }

  @Test
  void playersResolveIndependentlyWithoutChangingSharedConfiguration() {
    PlayerData first = new PlayerData();
    PlayerData second = new PlayerData();
    first.getPreferences().set("adapt:rift/blink", "activation", "REACTIVE");

    assertThat(resolve(first, 2)).isEqualTo(Mode.REACTIVE);
    assertThat(resolve(second, 2)).isEqualTo(Mode.MANUAL);
    assertThat(config.playerPreferences.get("activation").defaultValue).isEqualTo("MANUAL");
  }

  @Test
  void serverLockRetainsButOverridesPlayerChoice() {
    PlayerData data = reactiveData();
    config.playerPreferences.get("activation").playerEditable = false;

    assertThat(resolve(data, 2)).isEqualTo(Mode.MANUAL);
    assertThat(data.getPreferences().get("adapt:rift/blink", "activation")).isEqualTo("REACTIVE");
    config.playerPreferences.get("activation").playerEditable = true;
    assertThat(resolve(data, 2)).isEqualTo(Mode.REACTIVE);
  }

  @Test
  void restrictedChoiceRemainsDormantUntilAllowedAgain() {
    PlayerData data = reactiveData();
    config.playerPreferences.get("activation").allowedValues = List.of("MANUAL");
    assertThat(resolve(data, 2)).isEqualTo(Mode.MANUAL);
    config.playerPreferences.get("activation").allowedValues = List.of("MANUAL", "REACTIVE");
    assertThat(resolve(data, 2)).isEqualTo(Mode.REACTIVE);
  }

  @Test
  void levelGatesApplyWithoutDeletingSavedMode() {
    PlayerData data = reactiveData();
    assertThat(resolve(data, 1)).isEqualTo(Mode.MANUAL);
    assertThat(PlayerPreferences.allowedChoices(adaptation, MODE, 1))
        .extracting(PlayerPreference.Choice::value).containsExactly(Mode.MANUAL);
    assertThat(resolve(data, 2)).isEqualTo(Mode.REACTIVE);
  }

  @Test
  void forcedLockedModeDoesNotResolveToAnUnpermittedMode() {
    PreferencePolicy policy = policy(Mode.REACTIVE, Mode.REACTIVE);
    policy.playerEditable = false;
    config.playerPreferences = Map.of("activation", policy);
    PlayerPreferences.validate(adaptation, config);

    assertThat(resolve(new PlayerData(), 1)).isEqualTo(Mode.REACTIVE);
    assertThat(PlayerPreferences.allowedChoices(adaptation, MODE, 1)).isEmpty();
  }

  @Test
  void corruptSavedValueUsesServerDefault() {
    PlayerData data = new PlayerData();
    data.getPreferences().set("adapt:rift/blink", "activation", "INVALID");
    assertThat(resolve(data, 2)).isEqualTo(Mode.MANUAL);
  }

  @Test
  void defaultsArePopulatedAndInvalidPolicyRejected() {
    assertThat(config.playerPreferences.get("activation").allowedValues).containsExactly("MANUAL", "REACTIVE");
    config.playerPreferences.get("activation").defaultValue = "UNKNOWN";
    assertThatThrownBy(() -> PlayerPreferences.validate(adaptation, config))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("adapt:rift/blink.playerPreferences.activation.defaultValue");
  }

  @Test
  void defaultMustBeAllowedAndEveryAllowedChoiceMustExist() {
    config.playerPreferences.get("activation").allowedValues = List.of("REACTIVE");
    assertThatThrownBy(() -> PlayerPreferences.validate(adaptation, config))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultValue");
    config.playerPreferences.get("activation").allowedValues = List.of("MANUAL", "UNKNOWN");
    assertThatThrownBy(() -> PlayerPreferences.validate(adaptation, config))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("allowedValues");
  }

  @Test
  void preferencesSurviveJsonAndLearnedRecordReplacement() {
    PlayerData data = reactiveData();
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:rift");
    data.getSkillLines().put("adapt:rift", line);
    line.setAdaptation(adaptation, 1);
    line.setAdaptation(adaptation, 3);
    line.setAdaptation(adaptation, 0);

    PlayerData restored = PlayerData.fromJson(data.toJson(false));
    assertThat(restored).isNotNull();
    assertThat(restored.getPreferences().get("adapt:rift/blink", "activation")).isEqualTo("REACTIVE");
    assertThat(resolve(restored, 2)).isEqualTo(Mode.REACTIVE);
  }

  @Test
  void missingAndNullPreferenceSectionsUseDefaults() {
    assertThat(resolve(PlayerData.fromJson("{}"), 2)).isEqualTo(Mode.MANUAL);
    assertThat(resolve(PlayerData.fromJson("{\"preferences\":null}"), 2)).isEqualTo(Mode.MANUAL);
  }

  @Test
  void resetOnlyRemovesThatAdaptationsOverrides() {
    PlayerData data = reactiveData();
    data.getPreferences().set("adapt:other", "setting", "VALUE");
    assertThat(data.getPreferences().reset("adapt:rift/blink")).isTrue();
    assertThat(resolve(data, 2)).isEqualTo(Mode.MANUAL);
    assertThat(data.getPreferences().get("adapt:other", "setting")).isEqualTo("VALUE");
    assertThat(data.getPreferences().reset("adapt:rift/blink")).isFalse();
  }

  @Test
  void storedPreferenceDataResolvesLikePlayerDataAndAMissingSetUsesTheDefault() {
    PlayerPreferenceData preferences = reactiveData().getPreferences();

    assertThat(PlayerPreferences.resolve(adaptation, preferences, 2, MODE)).isEqualTo(Mode.REACTIVE);
    assertThat(PlayerPreferences.resolve(adaptation, preferences, 1, MODE)).isEqualTo(Mode.MANUAL);
    assertThat(PlayerPreferences.resolve(adaptation, (PlayerPreferenceData) null, 2, MODE)).isEqualTo(Mode.MANUAL);
  }

  @Test
  void aLockedPolicyWinsOverStoredPreferenceData() {
    config.playerPreferences.get("activation").playerEditable = false;

    assertThat(PlayerPreferences.resolve(adaptation, reactiveData().getPreferences(), 2, MODE)).isEqualTo(Mode.MANUAL);
  }

  private Mode resolve(PlayerData data, int level) {
    return PlayerPreferences.resolve(adaptation, data, level, MODE);
  }

  private PlayerData reactiveData() {
    PlayerData data = new PlayerData();
    data.getPreferences().set("adapt:rift/blink", "activation", "REACTIVE");
    return data;
  }

  private enum Mode {
    MANUAL,
    REACTIVE
  }

  private static PreferencePolicy policy(Mode defaultValue, Mode... allowed) {
    PreferencePolicy policy = new PreferencePolicy();
    policy.defaultValue = defaultValue.name();
    List<String> values = new ArrayList<>();
    for (Mode value : allowed) {
      values.add(value.name());
    }
    policy.allowedValues = List.copyOf(values);
    return policy;
  }
}
