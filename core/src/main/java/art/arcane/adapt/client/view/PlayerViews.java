package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillMenus;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.gui.SkillsGui;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds a player's {@link PlayerView} from their live data. Call on the player's owning thread. */
public final class PlayerViews {
  private PlayerViews() {
  }

  public static PlayerView build(SkillRegistry registry, AdaptPlayer player) {
    PlayerData data = player.getData();
    List<SkillLineView> skills = new ArrayList<>();
    Map<String, Integer> levels = new HashMap<>();
    List<PreferenceValue> preferences = new ArrayList<>();
    for (Skill<?> registered : registry.getSkills()) {
      Skill<?> skill = SkillMutations.listedSkill(registry, data, registered);
      if (skill == null) {
        continue;
      }
      PlayerSkillLine stored = data.getSkillLineNullable(SkillHost.lineKey(skill));
      PlayerSkillLine line = stored == null ? new PlayerSkillLine() : stored;
      skills.add(new SkillLineView(SkillHost.lineKey(skill), line.getXp(), line.getLevel(), line.getLevelProgress(),
          line.getXPForLevelUpAbsolute(), line.getKnowledge()));
      preferences.add(new PreferenceValue(
          new PreferenceKey(PreferenceOwner.SKILL, SkillHost.lineKey(skill), CommonPreferences.SKILL_ENABLED.id()),
          PlayerPreferences.resolve(skill, data, CommonPreferences.SKILL_ENABLED).name()));
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        int level = paidLevel(stored, adaptation.getId().toString());
        if (level <= 0) {
          continue;
        }
        levels.put(adaptation.getId().toString(), level);
        addPreferences(preferences, adaptation, data, level);
      }
      if (skill instanceof MutatedSkill mutated) {
        for (Adaptation<?> kept : mutated.getKept()) {
          int raised = SkillUpgrades.raisedLevel(kept, data);
          if (raised > 0) {
            levels.put(kept.getId().toString(), raised);
          }
          addPreferences(preferences, kept, data, SkillUpgrades.heritageLevel(kept, data));
        }
        PlayerRef online = player.getPlayer();
        if (online != null) {
          for (Lending.Running running : Lending.running(online, player, registry, mutated.getPair())) {
            addPreferences(preferences, running.original(), data, running.level());
          }
        }
      }
    }
    preferences.sort(Comparator.comparing(PreferenceValue::key));
    PlayerRef ref = player.getPlayer();
    PlayerAccess access = ref == null ? PlayerAccess.NONE
        : LanguageAudience.call(ref.uuid(), () -> access(registry, player, ref));
    return new PlayerView(header(data), skills, levels, preferences, access);
  }

  private static void addPreferences(List<PreferenceValue> preferences, Adaptation<?> adaptation, PlayerData data, int level) {
    for (PlayerPreference<?> preference : adaptation.getPlayerPreferences()) {
      preferences.add(new PreferenceValue(
          new PreferenceKey(PreferenceOwner.ADAPTATION, adaptation.getId().toString(), preference.id()),
          PlayerPreferences.resolve(adaptation, data, level, preference).name()));
    }
  }

  /**
   * A cheap stamp of the stored numbers a view is built from: master xp, wisdom, effects, region power bonus, and each
   * skill line's xp, knowledge and paid adaptation levels. Equal data gives an equal stamp. Preferences, permissions
   * and config are not covered.
   */
  public static long fingerprint(PlayerData data) {
    long stamp = Double.doubleToLongBits(data.getMasterXp());
    stamp = 31L * stamp + data.getWisdom();
    stamp = 31L * stamp + data.getRegionPowerBonus();
    stamp = 31L * stamp + (data.isEffectsEnabled() ? 1L : 0L);
    for (Map.Entry<String, PlayerSkillLine> entry : data.getSkillLines().entrySet()) {
      PlayerSkillLine line = entry.getValue();
      if (line == null) {
        continue;
      }
      long lineStamp = 31L * Double.doubleToLongBits(line.getXp()) + line.getKnowledge();
      for (PlayerAdaptation adaptation : line.getAdaptations().values()) {
        if (adaptation != null && !adaptation.isRegionGranted()) {
          lineStamp += (long) adaptation.getId().hashCode() * (adaptation.getLevel() + 1L);
        }
      }
      stamp += (entry.getKey().hashCode() * 1_000_003L) ^ lineStamp;
    }
    return stamp;
  }

  /** What the chest menus would show {@code ref}: the skills list and each openable skill's adaptation rows. */
  static PlayerAccess access(SkillRegistry registry, AdaptPlayer player, PlayerRef ref) {
    List<String> skills = new ArrayList<>();
    for (Skill<?> skill : SkillsGui.visibleSkills(player, ref)) {
      skills.add(SkillHost.lineKey(skill));
    }
    Map<String, List<List<String>>> rows = new HashMap<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (skill == null || !skill.isEnabled() || !skill.hasUsePermission(ref)) {
        continue;
      }
      rows.put(SkillHost.lineKey(skill), rowIds(skill, ref));
    }
    for (SkillPair mutation : player.getData().getMutations()) {
      MutatedSkill mutated = registry.getMutatedSkill(mutation);
      if (mutated != null && mutated.hasUsePermission(ref)) {
        rows.put(SkillHost.lineKey(mutated), rowIds(mutated, ref));
      }
    }
    AdaptConfig config = AdaptConfig.get();
    return new PlayerAccess(AdaptDebugMode.isActive(ref), !config.isHardcoreNoRefunds(), config.isPlayerSettingsEnabled(),
        skills, rows);
  }

  private static List<List<String>> rowIds(Skill<?> skill, PlayerRef ref) {
    List<List<String>> skillRows = new ArrayList<>();
    for (List<Adaptation<?>> row : SkillMenus.adaptationRows(skill, ref)) {
      List<String> ids = new ArrayList<>(row.size());
      for (Adaptation<?> adaptation : row) {
        ids.add(adaptation.getId().toString());
      }
      skillRows.add(ids);
    }
    return skillRows;
  }

  private static PlayerHeader header(PlayerData data) {
    double exact = XP.getLevelForXp(data.getMasterXp());
    double progress = Math.min(1D, Math.max(0D, exact - Math.floor(exact)));
    return new PlayerHeader(data.getLevel(), progress, data.getUsedPower(), data.getMaxPower(), data.getWisdom(),
        data.isEffectsEnabled());
  }

  private static int paidLevel(PlayerSkillLine line, String adaptationId) {
    PlayerAdaptation stored = line == null ? null : line.getAdaptation(adaptationId);
    return stored == null || stored.isRegionGranted() ? 0 : Math.max(0, stored.getLevel());
  }
}
