package art.arcane.adapt.engine.preference;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import java.util.concurrent.ConcurrentHashMap;
public final class PlayerPreferences {
  private static final Map<PlayerPreference<?>, PreferencePolicy> DEFAULT_POLICIES = new ConcurrentHashMap<>();

  private PlayerPreferences() {
  }

  /** The policy a preference has when no config names it: its own default, every choice allowed. */
  public static PreferencePolicy defaultPolicy(PlayerPreference<?> preference) {
    return DEFAULT_POLICIES.computeIfAbsent(preference, PreferencePolicy::defaults);
  }

  /** The key the conventional preference labels of {@code adaptation} sit under. */
  public static String ownerKey(Adaptation<?> adaptation) {
    return AdaptationHost.of(adaptation).textKey();
  }

  /** The key the conventional preference labels of {@code skill} sit under. */
  public static String ownerKey(Skill<?> skill) {
    return TextKeys.skill(skill.getId());
  }

  /** The label of {@code preference}: its own text when it has one, else the conventional key below {@code ownerKey}. */
  public static Text label(String ownerKey, PlayerPreference<?> preference) {
    return preference.label().orElseGet(() -> Text.key(ownerKey + "." + preference.conventionalKey()));
  }

  /** The label of the choice {@code value}: its own text when it has one, else the conventional key below {@code ownerKey}. */
  public static <E extends Enum<E>> Text label(String ownerKey, PlayerPreference<E> preference, E value) {
    return preference.choice(value).label()
        .orElseGet(() -> Text.key(ownerKey + "." + preference.conventionalKey(value)));
  }

  /** {@code label} in the current render language, a literal as written. */
  public static String shown(Text label) {
    return shown("", label);
  }

  /** {@code label} in the current render language with {@code style} as the default style. */
  public static String shown(String style, Text label) {
    if (label.isLiteral()) {
      return style + label.literal();
    }
    if (!label.arguments().isEmpty()) {
      return style + label.resolve();
    }
    return AdaptLanguage.textStyled(style, AdaptLanguage.key(label.key()));
  }

  /** The icon item a choice shows on the running server. */
  public static Id shownIcon(PlayerPreference.Choice<?> choice) {
    return choice.icon().equals(choice.fallbackIcon()) ? choice.icon()
        : PlatformSupport.itemOr(choice.icon(), choice.fallbackIcon());
  }

  public static PreferencePolicy policy(Adaptation<?> adaptation, PlayerPreference<?> preference) {
    if (adaptation.getConfig() instanceof AdaptationConfig config && config.playerPreferences != null) {
      PreferencePolicy policy = config.playerPreferences.get(preference.id());
      if (policy != null) {
        return policy;
      }
    }
    return defaultPolicy(preference);
  }

  public static <E extends Enum<E>> E resolve(Adaptation<?> adaptation, PlayerData data, int level,
                                             PlayerPreference<E> preference) {
    return resolve(adaptation, data == null ? null : data.getPreferences(), level, preference);
  }

  public static <E extends Enum<E>> E resolve(Adaptation<?> adaptation, PlayerPreferenceData preferences, int level,
                                             PlayerPreference<E> preference) {
    return resolve(preference, policy(adaptation, preference), level, preferences == null ? null
        : preferences.get(adaptation.getId().toString(), preference.id()));
  }

  /** {@code preference} of {@code adaptation} for a player at {@code level} whose saved choice is {@code saved}. */
  public static <E extends Enum<E>> E resolveSaved(Adaptation<?> adaptation, String saved, int level,
                                                   PlayerPreference<E> preference) {
    return resolve(preference, policy(adaptation, preference), level, saved);
  }

  /** {@code preference} of {@code skill} for the player, read from the page that shows the skill to them. */
  public static <E extends Enum<E>> E resolve(Skill<?> skill, PlayerData data, PlayerPreference<E> preference) {
    Skill<?> page = page(skill, data);
    return resolve(preference, policy(page, preference), 1, data == null ? null
        : data.getPreferences().getSkill(SkillHost.lineKey(page), preference.id()));
  }

  /**
   * The skill whose page shows {@code skill}'s adaptations to the player: the mutated skill of the player's mutation
   * that took {@code skill} in, else {@code skill}. That page's switch governs them, as their progress lives on its line.
   */
  public static Skill<?> page(Skill<?> skill, @Nullable PlayerData data) {
    if (data == null || skill instanceof MutatedSkill) {
      return skill;
    }
    for (SkillPair mutation : data.getMutations()) {
      if (mutation.involves(skill.getId())) {
        AdaptServer server = server();
        MutatedSkill mutated = server == null || server.getSkillRegistry() == null ? null
            : server.getSkillRegistry().getMutatedSkill(mutation);
        return mutated == null ? skill : mutated;
      }
    }
    return skill;
  }

  private static <E extends Enum<E>> E resolve(PlayerPreference<E> preference, PreferencePolicy policy,
                                               int level, String stored) {
    E serverDefault = preference.parse(policy.defaultValue).orElse(null);
    if (!AdaptConfig.get().isPlayerSettingsEnabled() || !policy.playerEditable) {
      return serverDefault == null ? preference.defaultValue() : serverDefault;
    }
    if (stored != null) {
      E saved = preference.parse(stored).orElse(null);
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
    if (!policy.playerEditable || adaptation.isPlayerPreferenceBlocked(preference)
        || !available(preference, policy, value, editLevel(adaptation, player))) {
      return false;
    }
    player.getData().getPreferences().set(adaptation.getId().toString(), preference.id(), value.name());
    player.requestSave();
    PreferenceConfirmation.clear(player.getPlayer());
    reconcile(adaptation, player);
    ClientUiService.changed(player);
    return true;
  }

  public static boolean reset(Adaptation<?> adaptation, AdaptPlayer player) {
    if (!canEdit(adaptation, player)) {
      return false;
    }
    if (player.getData().getPreferences().reset(adaptation.getId().toString())) {
      player.requestSave();
    }
    PreferenceConfirmation.clear(player.getPlayer());
    reconcile(adaptation, player);
    ClientUiService.changed(player);
    return true;
  }

  /** Forgets one saved choice so the server default applies again; false when the player may not edit it. */
  public static boolean clear(Adaptation<?> adaptation, AdaptPlayer player, String preference) {
    if (!canEdit(adaptation, player)) {
      return false;
    }
    if (player.getData().getPreferences().remove(adaptation.getId().toString(), preference)) {
      player.requestSave();
    }
    PreferenceConfirmation.clear(player.getPlayer());
    reconcile(adaptation, player);
    ClientUiService.changed(player);
    return true;
  }

  /** As {@link #clear(Adaptation, AdaptPlayer, String)}, for a skill's choice. */
  public static boolean clearSkill(Skill<?> skill, AdaptPlayer player, String preference) {
    if (!canEdit(skill, player)) {
      return false;
    }
    Skill<?> page = page(skill, player.getData());
    if (page != skill && !canEdit(page, player)) {
      return false;
    }
    if (player.getData().getPreferences().removeSkill(SkillHost.lineKey(page), preference)) {
      player.requestSave();
    }
    reconcileSkill(page, player);
    ClientUiService.changed(player);
    return true;
  }

  public static void validate(Adaptation<?> adaptation, AdaptationConfig config) {
    config.playerPreferences = validate(adaptation.getId().toString(), adaptation.getPlayerPreferences(), config.playerPreferences);
  }

  public static void validate(Skill<?> skill, SkillConfig config) {
    config.playerPreferences = validate(SkillHost.lineKey(skill), List.of(CommonPreferences.SKILL_ENABLED), config.playerPreferences);
  }

  private static Map<String, PreferencePolicy> validate(String owner, List<PlayerPreference<?>> preferences,
                                                        Map<String, PreferencePolicy> configured) {
    Map<String, PreferencePolicy> policies = configured == null
        ? new TreeMap<>() : new TreeMap<>(configured);
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
    return Collections.unmodifiableMap(new LinkedHashMap<>(policies));
  }

  public static PreferencePolicy policy(Skill<?> skill, PlayerPreference<?> preference) {
    if (skill.getConfig() instanceof SkillConfig config && config.playerPreferences != null) {
      PreferencePolicy policy = config.playerPreferences.get(preference.id());
      if (policy != null) {
        return policy;
      }
    }
    return defaultPolicy(preference);
  }

  public static boolean isEnabled(Adaptation<?> adaptation, PlayerData data, int learnedLevel) {
    if (resolve(adaptation, data, learnedLevel, CommonPreferences.ENABLED) != CommonPreferences.Toggle.ON) {
      return false;
    }
    Skill<?> skill = adaptation.getSkill();
    return skill == null || resolve(skill, data, CommonPreferences.SKILL_ENABLED) == CommonPreferences.Toggle.ON;
  }

  /** Sets the switch of the page that shows {@code skill} to the player, see {@link #page}. */
  public static boolean setSkillEnabled(Skill<?> skill, AdaptPlayer player, CommonPreferences.Toggle value) {
    if (!canEdit(skill, player)) {
      return false;
    }
    Skill<?> page = page(skill, player.getData());
    if (page != skill && !canEdit(page, player)) {
      return false;
    }
    PreferencePolicy policy = policy(page, CommonPreferences.SKILL_ENABLED);
    if (!policy.playerEditable || !available(CommonPreferences.SKILL_ENABLED, policy, value, 1)) {
      return false;
    }
    player.getData().getPreferences().setSkill(SkillHost.lineKey(page), CommonPreferences.SKILL_ENABLED.id(), value.name());
    player.requestSave();
    reconcileSkill(page, player);
    ClientUiService.changed(player);
    return true;
  }

  public static boolean resetSkill(Skill<?> skill, AdaptPlayer player) {
    if (!canEdit(skill, player)) {
      return false;
    }
    Skill<?> page = page(skill, player.getData());
    if (page != skill && !canEdit(page, player)) {
      return false;
    }
    if (player.getData().getPreferences().resetSkill(SkillHost.lineKey(page))) {
      player.requestSave();
    }
    reconcileSkill(page, player);
    ClientUiService.changed(player);
    return true;
  }

  public static void reconcile(Adaptation<?> adaptation, AdaptPlayer player) {
    try {
      adaptation.onPlayerPreferencesChanged(PlayerApi.profile(player));
    } catch (RuntimeException error) {
      AdaptLog.warn("Could not reconcile preferences for " + adaptation.getId().toString() + " and player " + player.getPlayer().uuid());
      AdaptLog.error(error);
    }
    AdaptServer server = server();
    if (server != null) {
      server.synchronizeRecipeBook(player.getPlayer().uuid(), player);
    }
  }

  public static void reconcileOnline(Adaptation<?> adaptation) {
    AdaptServer server = server();
    if (server == null) {
      return;
    }
    for (AdaptPlayer player : server.getLearnedAdaptPlayerSnapshot(adaptation.getId().toString())) {
      Tasks.runEntity(player.getPlayer(), () -> {
        if (player.isRuntimeReady()) {
          reconcile(adaptation, player);
        }
      });
    }
  }

  public static void reconcileOnline() {
    AdaptServer server = server();
    if (server == null || server.getSkillRegistry() == null) {
      return;
    }
    for (AdaptPlayer player : server.getOnlineAdaptPlayerSnapshot()) {
      Tasks.runEntity(player.getPlayer(), () -> {
        if (player.isRuntimeReady()) {
          for (Skill<?> skill : server.getSkillRegistry().getSkills()) {
            reconcileSkill(skill, player);
          }
        }
      });
    }
  }

  public static void reconcileOnline(Skill<?> skill) {
    AdaptServer server = server();
    if (server == null) {
      return;
    }
    for (AdaptPlayer player : server.getOnlineAdaptPlayerSnapshot()) {
      Tasks.runEntity(player.getPlayer(), () -> {
        if (player.isRuntimeReady()) {
          reconcileSkill(skill, player);
        }
      });
    }
  }

  /** Reconciles every adaptation the switch of {@code skill}'s page governs, a mutated page's parents' included. */
  public static void reconcileSkill(Skill<?> skill, AdaptPlayer player) {
    PreferenceConfirmation.clear(player.getPlayer());
    List<Adaptation<?>> governed = skill instanceof MutatedSkill mutated ? mutated.switchedAdaptations()
        : SkillHost.ownedAdaptations(skill);
    for (Adaptation<?> adaptation : governed) {
      reconcile(adaptation, player);
    }
  }

  private static boolean isCurrentSkill(Skill<?> skill) {
    AdaptServer server = server();
    return server != null && server.getSkillRegistry().getSkill(SkillHost.lineKey(skill)) == skill;
  }

  private static boolean canEdit(Skill<?> skill, AdaptPlayer player) {
    if (!AdaptConfig.get().isPlayerSettingsEnabled() || skill == null || player == null
        || !player.isRuntimeReady() || !skill.isEnabled() || !isCurrentSkill(skill)) {
      return false;
    }
    PlayerRef owner = player.getPlayer();
    return owner != null && owner.isOnline() && Tasks.isOwnedByCurrentRegion(owner)
        && skill.hasUsePermission(owner);
  }

  private static boolean canEdit(Adaptation<?> adaptation, AdaptPlayer player) {
    if (!AdaptConfig.get().isPlayerSettingsEnabled() || adaptation == null || player == null
        || !player.isRuntimeReady() || !adaptation.isEnabled()
        || adaptation.getSkill() == null || !adaptation.getSkill().isEnabled()
        || !isCurrentSkill(adaptation.getSkill())
        || !SkillHost.owns(adaptation.getSkill(), adaptation)) {
      return false;
    }
    PlayerRef owner = player.getPlayer();
    return owner != null && owner.isOnline() && Tasks.isOwnedByCurrentRegion(owner)
        && adaptation.hasUsePermission(owner)
        && adaptation.getSkill().hasUsePermission(owner)
        && editLevel(adaptation, player) > 0;
  }

  /**
   * The level the player's settings for {@code adaptation} apply at: the level they learned or keep it at, or the level
   * a held upgrade lends an original whose own code still runs for them.
   */
  public static int editLevel(Adaptation<?> adaptation, AdaptPlayer player) {
    return Math.max(AdaptationHost.of(adaptation).level(player), adaptation.getLevel(player.getPlayer()));
  }

  private static AdaptServer server() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
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
      if (preference.parse(value).isEmpty()) {
        throw new IllegalArgumentException(path + ".allowedValues contains an invalid choice: " + value);
      }
    }
    if (preference.parse(policy.defaultValue).isEmpty() || !policy.allowedValues.contains(policy.defaultValue)) {
      throw new IllegalArgumentException(path + ".defaultValue must be an allowed choice");
    }
  }
}
