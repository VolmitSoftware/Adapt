package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.PreferenceStore;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link PreferenceStore} over the engine's saved choices. Preferences are matched to the owner's engine preferences
 * by id and their values by name, so a public preference reads and writes the same save as the menus.
 */
public final class EnginePreferenceStore implements PreferenceStore {
  private final EnginePlayerProfile profile;

  EnginePreferenceStore(EnginePlayerProfile profile) {
    this.profile = Objects.requireNonNull(profile, "profile");
  }

  @Override
  public <E extends Enum<E>> E get(Id owner, PlayerPreference<E> preference) {
    Objects.requireNonNull(preference, "preference");
    String resolved = resolvedName(owner, preference.id());
    if (resolved == null) {
      return preference.defaultValue();
    }
    for (E value : preference.type().getEnumConstants()) {
      if (value.name().equals(resolved)) {
        return value;
      }
    }
    return preference.defaultValue();
  }

  @Override
  public Optional<String> saved(Id owner, String preference) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(preference, "preference");
    Skill<?> skill = skill(owner);
    String stored = skill != null
        ? profile.data().getPreferences().getSkill(SkillHost.lineKey(skill), preference)
        : profile.data().getPreferences().get(owner.toString(), preference);
    return Optional.ofNullable(stored);
  }

  @Override
  public <E extends Enum<E>> boolean set(Id owner, PlayerPreference<E> preference, E value) {
    Objects.requireNonNull(preference, "preference");
    Objects.requireNonNull(value, "value");
    return set(owner, preference.id(), value.name());
  }

  @Override
  public boolean set(Id owner, String preference, String value) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(preference, "preference");
    Objects.requireNonNull(value, "value");
    profile.requireOwner();
    Skill<?> skill = skill(owner);
    if (skill != null) {
      CommonPreferences.Toggle toggle = CommonPreferences.SKILL_ENABLED.id().equals(preference)
          ? CommonPreferences.SKILL_ENABLED.parse(value).orElse(null) : null;
      return toggle != null && PlayerPreferences.setSkillEnabled(skill, profile.runtime(), toggle);
    }
    Adaptation<?> adaptation = profile.registry().getAdaptation(owner);
    art.arcane.adapt.api.preference.PlayerPreference<?> engine = adaptation == null ? null
        : preference(adaptation, preference);
    return engine != null && setParsed(adaptation, engine, value);
  }

  @Override
  public void clear(Id owner, String preference) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(preference, "preference");
    profile.requireOwner();
    Skill<?> skill = skill(owner);
    if (skill != null) {
      PlayerPreferences.clearSkill(skill, profile.runtime(), preference);
      return;
    }
    Adaptation<?> adaptation = profile.registry().getAdaptation(owner);
    if (adaptation != null) {
      PlayerPreferences.clear(adaptation, profile.runtime(), preference);
    }
  }

  private String resolvedName(Id owner, String preference) {
    Objects.requireNonNull(owner, "owner");
    Skill<?> skill = skill(owner);
    if (skill != null) {
      return CommonPreferences.SKILL_ENABLED.id().equals(preference)
          ? PlayerPreferences.resolve(skill, profile.data(), CommonPreferences.SKILL_ENABLED).name() : null;
    }
    Adaptation<?> adaptation = profile.registry().getAdaptation(owner);
    art.arcane.adapt.api.preference.PlayerPreference<?> engine = adaptation == null ? null
        : preference(adaptation, preference);
    return engine == null ? null : resolve(adaptation, engine).name();
  }

  private <E extends Enum<E>> E resolve(Adaptation<?> adaptation,
                                        art.arcane.adapt.api.preference.PlayerPreference<E> preference) {
    return PlayerPreferences.resolve(adaptation, profile.data(), PlayerPreferences.editLevel(adaptation,
        profile.runtime()), preference);
  }

  private <E extends Enum<E>> boolean setParsed(Adaptation<?> adaptation,
                                                art.arcane.adapt.api.preference.PlayerPreference<E> preference,
                                                String value) {
    E parsed = preference.parse(value).orElse(null);
    return parsed != null && PlayerPreferences.set(adaptation, profile.runtime(), preference, parsed);
  }

  private Skill<?> skill(Id owner) {
    return profile.registry().getSkill(owner);
  }

  private static art.arcane.adapt.api.preference.PlayerPreference<?> preference(Adaptation<?> adaptation,
                                                                                    String id) {
    for (art.arcane.adapt.api.preference.PlayerPreference<?> candidate : adaptation.getPlayerPreferences()) {
      if (candidate.id().equals(id)) {
        return candidate;
      }
    }
    return null;
  }
}
