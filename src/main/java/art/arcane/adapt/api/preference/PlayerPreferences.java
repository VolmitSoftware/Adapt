package art.arcane.adapt.api.preference;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerPreferences {
  private PlayerPreferences() {
  }

  public static PreferencePolicy policy(Adaptation<?> adaptation, PlayerPreference<?> preference) {
    if (adaptation.getConfig() instanceof AdaptationConfig config && config.playerPreferences != null) {
      PreferencePolicy policy = config.playerPreferences.get(preference.id());
      if (policy != null) {
        return policy;
      }
    }
    return preference.defaultPolicy();
  }

  public static <E extends Enum<E>> E resolve(Adaptation<?> adaptation, PlayerData data, int level,
                                             PlayerPreference<E> preference) {
    return resolve(preference, policy(adaptation, preference), level, data == null ? null
        : data.getPreferences().get(adaptation.getName(), preference.id()));
  }

  public static <E extends Enum<E>> E resolve(Skill<?> skill, PlayerData data, PlayerPreference<E> preference) {
    return resolve(preference, policy(skill, preference), 1, data == null ? null
        : data.getPreferences().getSkill(skill.getName(), preference.id()));
  }

  private static <E extends Enum<E>> E resolve(PlayerPreference<E> preference, PreferencePolicy policy,
                                               int level, String stored) {
    E serverDefault = preference.parse(policy.defaultValue);
    if (!policy.playerEditable) {
      return serverDefault == null ? preference.defaultValue() : serverDefault;
    }
    if (stored != null) {
      E saved = preference.parse(stored);
      if (available(preference, policy, saved, level)) {
        return saved;
      }
    }
    if (available(preference, policy, serverDefault, level)) {
      return serverDefault;
    }
    if (available(preference, policy, preference.defaultValue(), level)) {
      return preference.defaultValue();
    }
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      if (available(preference, policy, choice.value(), level)) {
        return choice.value();
      }
    }
    return serverDefault == null ? preference.defaultValue() : serverDefault;
  }

  public static <E extends Enum<E>> List<PlayerPreference.Choice<E>> allowedChoices(
      Adaptation<?> adaptation, PlayerPreference<E> preference, int level) {
    PreferencePolicy policy = policy(adaptation, preference);
    List<PlayerPreference.Choice<E>> choices = new ArrayList<>(preference.choices().size());
    for (PlayerPreference.Choice<E> choice : preference.choices()) {
      if (available(preference, policy, choice.value(), level)) {
        choices.add(choice);
      }
    }
    return List.copyOf(choices);
  }

  public static <E extends Enum<E>> boolean set(Adaptation<?> adaptation, AdaptPlayer player,
                                               PlayerPreference<E> preference, E value) {
    if (!canEdit(adaptation, player) || !adaptation.getPlayerPreferences().contains(preference)) {
      return false;
    }
    PreferencePolicy policy = policy(adaptation, preference);
    if (!policy.playerEditable || !available(preference, policy, value, adaptation.getLevel(player))) {
      return false;
    }
    player.getData().getPreferences().set(adaptation.getName(), preference.id(), value.name());
    player.requestSave();
    PreferenceConfirmation.clear(player.getPlayer());
    reconcile(adaptation, player);
    return true;
  }

  public static boolean reset(Adaptation<?> adaptation, AdaptPlayer player) {
    if (!canEdit(adaptation, player)) {
      return false;
    }
    if (player.getData().getPreferences().reset(adaptation.getName())) {
      player.requestSave();
    }
    PreferenceConfirmation.clear(player.getPlayer());
    reconcile(adaptation, player);
    return true;
  }

  public static void validate(Adaptation<?> adaptation, AdaptationConfig config) {
    config.playerPreferences = validate(adaptation.getName(), adaptation.getPlayerPreferences(), config.playerPreferences);
  }

  public static void validate(Skill<?> skill, SkillConfig config) {
    config.playerPreferences = validate(skill.getName(), List.of(CommonPreferences.SKILL_ENABLED), config.playerPreferences);
  }

  private static Map<String, PreferencePolicy> validate(String owner, List<PlayerPreference<?>> preferences,
                                                        Map<String, PreferencePolicy> configured) {
    Map<String, PreferencePolicy> policies = configured == null
        ? new LinkedHashMap<>() : new LinkedHashMap<>(configured);
    Set<String> registered = new HashSet<>();
    for (PlayerPreference<?> preference : preferences) {
      if (!registered.add(preference.id())) {
        throw new IllegalArgumentException("Duplicate player preference " + owner + "." + preference.id());
      }
      PreferencePolicy policy = policies.get(preference.id());
      if (policy == null) {
        policy = PreferencePolicy.defaults(preference);
      }
      validatePolicy(owner, preference, policy);
      PreferencePolicy snapshot = new PreferencePolicy();
      snapshot.playerEditable = policy.playerEditable;
      snapshot.defaultValue = policy.defaultValue;
      snapshot.allowedValues = List.copyOf(policy.allowedValues);
      policies.put(preference.id(), snapshot);
    }
    return Map.copyOf(policies);
  }

  public static PreferencePolicy policy(Skill<?> skill, PlayerPreference<?> preference) {
    if (skill.getConfig() instanceof SkillConfig config && config.playerPreferences != null) {
      PreferencePolicy policy = config.playerPreferences.get(preference.id());
      if (policy != null) {
        return policy;
      }
    }
    return preference.defaultPolicy();
  }

  public static boolean isEnabled(Adaptation<?> adaptation, PlayerData data, int learnedLevel) {
    if (resolve(adaptation, data, learnedLevel, CommonPreferences.ENABLED) != CommonPreferences.Toggle.ON) {
      return false;
    }
    Skill<?> skill = adaptation.getSkill();
    return skill == null || resolve(skill, data, CommonPreferences.SKILL_ENABLED) == CommonPreferences.Toggle.ON;
  }

  public static boolean setSkillEnabled(Skill<?> skill, AdaptPlayer player, CommonPreferences.Toggle value) {
    if (!canEdit(skill, player)) {
      return false;
    }
    PreferencePolicy policy = policy(skill, CommonPreferences.SKILL_ENABLED);
    if (!policy.playerEditable || !available(CommonPreferences.SKILL_ENABLED, policy, value, 1)) {
      return false;
    }
    player.getData().getPreferences().setSkill(skill.getName(), CommonPreferences.SKILL_ENABLED.id(), value.name());
    player.requestSave();
    reconcileSkill(skill, player);
    return true;
  }

  public static boolean resetSkill(Skill<?> skill, AdaptPlayer player) {
    if (!canEdit(skill, player)) {
      return false;
    }
    if (player.getData().getPreferences().resetSkill(skill.getName())) {
      player.requestSave();
    }
    reconcileSkill(skill, player);
    return true;
  }

  public static void reconcile(Adaptation<?> adaptation, AdaptPlayer player) {
    try {
      adaptation.onPlayerPreferencesChanged(player);
    } catch (RuntimeException error) {
      Adapt.warn("Could not reconcile preferences for " + adaptation.getName() + " and player " + player.getPlayer().getUniqueId());
      Adapt.error(error);
    }
    if (Adapt.instance != null && Adapt.instance.getAdaptServer() != null) {
      Adapt.instance.getAdaptServer().synchronizeRecipeBook(player.getPlayer().getUniqueId(), player);
    }
  }

  public static void reconcileOnline(Adaptation<?> adaptation) {
    if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return;
    }
    for (AdaptPlayer player : Adapt.instance.getAdaptServer().getLearnedAdaptPlayerSnapshot(adaptation.getName())) {
      J.runEntity(player.getPlayer(), () -> {
        if (player.isRuntimeReady()) {
          reconcile(adaptation, player);
        }
      });
    }
  }

  public static void reconcileOnline(Skill<?> skill) {
    if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return;
    }
    for (AdaptPlayer player : Adapt.instance.getAdaptServer().getOnlineAdaptPlayerSnapshot()) {
      J.runEntity(player.getPlayer(), () -> {
        if (player.isRuntimeReady()) {
          reconcileSkill(skill, player);
        }
      });
    }
  }

  public static void reconcileSkill(Skill<?> skill, AdaptPlayer player) {
    PreferenceConfirmation.clear(player.getPlayer());
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      reconcile(adaptation, player);
    }
  }

  private static boolean isCurrentSkill(Skill<?> skill) {
    return Adapt.instance != null && Adapt.instance.getAdaptServer() != null
        && Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skill.getName()) == skill;
  }

  private static boolean canEdit(Skill<?> skill, AdaptPlayer player) {
    if (skill == null || player == null || !player.isRuntimeReady() || !skill.isEnabled() || !isCurrentSkill(skill)) {
      return false;
    }
    Player owner = player.getPlayer();
    return owner != null && owner.isOnline() && J.isOwnedByCurrentRegion(owner)
        && skill.hasUsePermission(owner, skill);
  }

  private static boolean canEdit(Adaptation<?> adaptation, AdaptPlayer player) {
    if (adaptation == null || player == null || !player.isRuntimeReady() || !adaptation.isEnabled()
        || adaptation.getSkill() == null || !adaptation.getSkill().isEnabled()
        || !isCurrentSkill(adaptation.getSkill())
        || !adaptation.getSkill().getAdaptations().contains(adaptation)) {
      return false;
    }
    Player owner = player.getPlayer();
    return owner != null && owner.isOnline() && J.isOwnedByCurrentRegion(owner)
        && adaptation.hasUsePermission(owner, adaptation)
        && adaptation.getSkill().hasUsePermission(owner, adaptation.getSkill())
        && adaptation.getLevel(player) > 0;
  }

  private static <E extends Enum<E>> boolean available(PlayerPreference<E> preference, PreferencePolicy policy,
                                                       E value, int level) {
    return value != null && policy.allowedValues != null && policy.allowedValues.contains(value.name())
        && preference.choice(value).minimumLevel() <= level;
  }

  private static void validatePolicy(String adaptationId, PlayerPreference<?> preference, PreferencePolicy policy) {
    String path = adaptationId + ".playerPreferences." + preference.id();
    if (policy.allowedValues == null || policy.allowedValues.isEmpty()) {
      throw new IllegalArgumentException(path + ".allowedValues must contain at least one choice");
    }
    for (String value : policy.allowedValues) {
      if (preference.parse(value) == null) {
        throw new IllegalArgumentException(path + ".allowedValues contains an invalid choice: " + value);
      }
    }
    if (preference.parse(policy.defaultValue) == null || !policy.allowedValues.contains(policy.defaultValue)) {
      throw new IllegalArgumentException(path + ".defaultValue must be an allowed choice");
    }
  }
}
