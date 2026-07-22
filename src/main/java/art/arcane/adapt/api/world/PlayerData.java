/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.world;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.notification.ActionBarNotification;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.notification.TitleNotification;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.format.Form;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@Data
@NoArgsConstructor
public class PlayerData {
  private static final Set<String> REMOVED_AXES_ADAPTATIONS = Set.of(
      "axe-orchardist",
      "axe-sap-tap",
      "axe-timber-mark"
  );
  private static final Set<String> REMOVED_AXES_STATS = Set.of(
      "axe.orchardist.trees-replanted",
      "axe.sap-tap.sap-drawn",
      "axe.timber-mark.marks-felled"
  );
  private static final Set<String> REMOVED_AXES_ADVANCEMENTS = Set.of(
      "adaptation_axe-orchardist",
      "adaptation_axe-sap-tap",
      "adaptation_axe-timber-mark",
      "challenge_axe_orchardist_500",
      "challenge_axe_sap_tap_500",
      "challenge_axe_timber_200",
      "challenge_axe_timber_40"
  );
  private static final String REMOVED_DOWSING_ADAPTATION = "excavation-dowsing";
  private static final String REMOVED_DOWSING_STAT = "excavation.dowsing.pockets-found";
  private static final Set<String> REMOVED_DOWSING_ADVANCEMENTS = Set.of(
      "adaptation_excavation-dowsing",
      "challenge_excavation_dowsing_200"
  );
  private static final String REMOVED_RIFT_STEP_ADAPTATION = "rift-step";
  private static final String REMOVED_RIFT_STEP_STAT = "rift.step.saves";
  private static final Set<String> REMOVED_RIFT_STEP_ADVANCEMENTS = Set.of(
      "adaptation_rift-step",
      "challenge_rift_step_50",
      "challenge_rift_step_500"
  );

  private final KMap<String, PlayerSkillLine> skillLines = new KMap<>();
  private KMap<String, Double> stats = new KMap<>();
  private String last = "none";
  private KSet<String> advancements = new KSet<>();
  private Discovery<String> seenBiomes = new Discovery<>();
  private Discovery<EntityType> seenMobs = new Discovery<>();
  private Discovery<Material> seenFoods = new Discovery<>();
  private Discovery<Material> seenItems = new Discovery<>();
  private Discovery<String> seenRecipes = new Discovery<>();
  private Discovery<String> seenEnchants = new Discovery<>();
  private Discovery<String> seenWorlds = new Discovery<>();
  private Discovery<String> seenPeople = new Discovery<>();
  private Discovery<World.Environment> seenEnvironments = new Discovery<>();
  private Discovery<String> seenPotionEffects = new Discovery<>();
  private Discovery<String> seenBlocks = new Discovery<>();
  private KList<XPMultiplier> multipliers = new KList<>();
  private long wisdom = 0;
  private double multiplier = 0;
  private long lastLogin = 0;
  private double masterXp = 1;
  private double lastMasterXp = 0;
  private volatile boolean effectsEnabled = true;
  private String inspiredSkill = "";
  private long inspiredAssignedAt = 0;
  private PlayerMutationData mutationData = new PlayerMutationData();
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private transient boolean inspiredPopupPending;
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private transient volatile AdaptPlayer runtimeOwner;

  public static PlayerData fromJson(String json) {
    PlayerData data = Json.fromJson(json, PlayerData.class);
    if (data == null) {
      return null;
    }
    data.getMutationData().normalize();
    data.normalizeInspiredAssignment();
    data.removeRetiredAxesContent();
    data.removeRetiredDowsingContent();
    data.removeRetiredRiftStepContent();
    return data;
  }

  public PlayerMutationData getMutationData() {
    if (mutationData == null) {
      mutationData = new PlayerMutationData();
    }
    return mutationData;
  }

  public void giveMasterXp(double xp) {
    masterXp += xp;
  }

  public void globalXPMultiplier(double v, int duration) {
    multipliers.add(new XPMultiplier(v, duration));
  }

  public boolean isGranted(String advancement) {
    return advancements.contains(advancement);
  }

  public boolean ensureGranted(String advancement) {
    return advancements.add(advancement);
  }

  public double getStat(String key) {
    Double d = stats.get(key);
    return d == null ? 0 : d;
  }

  public void addStat(String key, double amt) {
    stats.merge(key, amt, Double::sum);
    AdaptPlayer owner = runtimeOwner;
    if (owner != null) {
      owner.onStatChanged(key);
    }
  }

  void bindRuntimeOwner(AdaptPlayer owner) {
    runtimeOwner = owner;
    for (PlayerSkillLine line : skillLines.values()) {
      if (line != null) {
        line.bindRuntimeOwner(owner);
      }
    }
  }

  void unbindRuntimeOwner(AdaptPlayer owner) {
    if (runtimeOwner == owner) {
      runtimeOwner = null;
    }
    for (PlayerSkillLine line : skillLines.values()) {
      if (line != null) {
        line.unbindRuntimeOwner(owner);
      }
    }
  }

  public void update(AdaptPlayer p) {
    double m = 1D;
    m += collectActivePlayerMultiplierBonus();
    m += collectGlobalMultiplierBonus();

    if (m <= 0) {
      m = 0.01;
    }

    if (m > 1000) {
      m = 1000;
    }

    multiplier = m;
    pulseInspired(p);

    for (java.util.Map.Entry<java.lang.String, art.arcane.adapt.api.world.PlayerSkillLine> entry : skillLines.entrySet()) {
      String lineId = entry.getKey();
      Skill<?> loadedSkill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(lineId);
      if (loadedSkill == null) {
        // Never prune unknown lines automatically; missing skills can be transient
        // during startup/reload or due temporary config disables.
        continue;
      }

      PlayerSkillLine lineData = entry.getValue();
      if (lineData == null) {
        skillLines.remove(lineId);
        continue;
      }

      if (lineData.getXp() == 0 && lineData.getKnowledge() == 0 && lineData.getPooledXp() == 0 && lineData.getAdaptations().isEmpty()) {
        skillLines.remove(lineId, lineData);
        lineData.unbindRuntimeOwner(runtimeOwner);
        refreshLearnedIndex();
        continue;
      }

      lineData.update(p, lineId, this);
    }

    int oldLevel = (int) XP.getLevelForXp(getLastMasterXp());
    int level = (int) XP.getLevelForXp(getMasterXp());

    if (oldLevel != level) {
      setLastMasterXp(getMasterXp());
      p.getNot().queue(SoundNotification.builder()
              .sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE)
              .volume(1f)
              .pitch(0.54f)
              .group("lvl")
              .build(),
          SoundNotification.builder()
              .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
              .volume(1f)
              .pitch(0.44f)
              .group("lvl")
              .build(),
          SoundNotification.builder()
              .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
              .volume(1f)
              .pitch(0.74f)
              .group("lvl")
              .build(),
          SoundNotification.builder()
              .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
              .volume(1f)
              .pitch(1.34f)
              .group("lvl")
              .build(),
          TitleNotification.builder()
              .in(250)
              .stay(1450)
              .out(2250)
              .group("lvl")
              .title("")
              .subtitle(C.GOLD + AdaptLanguage.text(
                  SnippetsMessages.GUI_LEVEL_VALUE,
                  trusted("level", level)
              ))
              .build());
      p.getActionBarNotifier().queue(
          ActionBarNotification.builder()
              .duration(450)
              .group("power")
              .title(C.GOLD + AdaptLanguage.text(
                  RuntimeMessages.MAX_ABILITY_POWER,
                  trusted("power", Form.f(level * AdaptConfig.get().getPowerPerLevel(), 0) + C.GRAY)
              ))
              .build());

      MutationSVC mutationService = MutationSVC.get();
      if (mutationService != null && mutationService.getManager() != null) {
        mutationService.onLevelChanged(p, oldLevel, level);
      }

    }
  }

  private double collectActivePlayerMultiplierBonus() {
    double bonus = 0D;
    for (int i = multipliers.size() - 1; i >= 0; i--) {
      XPMultiplier active = multipliers.get(i);
      if (active == null || active.isExpired()) {
        multipliers.remove(i);
        continue;
      }
      bonus += active.getMultiplier();
    }
    return bonus;
  }

  private double collectGlobalMultiplierBonus() {
    double bonus = 0D;
    KList<XPMultiplier> globalMultipliers = Adapt.instance.getAdaptServer().getData().getMultipliers();
    for (int i = 0; i < globalMultipliers.size(); i++) {
      XPMultiplier active = globalMultipliers.get(i);
      if (active == null || active.isExpired()) {
        continue;
      }
      bonus += active.getMultiplier();
    }
    return bonus;
  }

  public int getAvailablePower() {
    return getMaxPower() - getUsedPower();
  }

  public boolean hasPowerAvailable() {
    return hasPowerAvailable(1);
  }

  public boolean hasPowerAvailable(int amount) {
    if (isDebugLearning()) {
      return true;
    }

    return getAvailablePower() >= amount;
  }

  private boolean isDebugLearning() {
    AdaptPlayer owner = runtimeOwner;
    return owner != null && owner.getPlayer() != null && AdaptDebugMode.isActive(owner.getPlayer().getUniqueId());
  }

  public int getUsedPower() {
    int usedPower = 0;
    for (PlayerSkillLine line : skillLines.values()) {
      if (line == null) {
        continue;
      }

      for (PlayerAdaptation adaptation : line.getAdaptations().values()) {
        if (adaptation == null) {
          continue;
        }
        usedPower += adaptation.getLevel();
      }
    }
    return usedPower;
  }

  public int getLevel() {
    return (int) XP.getLevelForXp(getMasterXp());
  }

  public int getMaxPower() {
    return (int) (XP.getLevelForXp(getMasterXp()) * AdaptConfig.get().getPowerPerLevel());
  }

  public PlayerSkillLine getSkillLine(String skillLine) {
    if (Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillLine) == null) {
      return null;
    }

    synchronized (skillLines) {
      try {
        PlayerSkillLine s = skillLines.get(skillLine);

        if (s != null) {
          return s;
        }
      } catch (Throwable e) {
        e.printStackTrace();
        Adapt.error("Failed to get skill line " + skillLine);
      }

      PlayerSkillLine s = new PlayerSkillLine();
      s.setLine(skillLine);
      s.bindRuntimeOwner(runtimeOwner);
      skillLines.put(skillLine, s);
      return s;
    }
  }

  public PlayerSkillLine getSkillLineNullable(String skillLine) {
    return skillLines.get(skillLine);
  }

  public void resetMonotonyForOtherSkills(String currentSkill) {
    if (currentSkill == null || currentSkill.isBlank() || currentSkill.equals(last)) {
      return;
    }

    last = currentSkill;
    String inspiredCandidate = null;
    double inspiredPressure = 0.0D;
    for (Map.Entry<String, PlayerSkillLine> entry : skillLines.entrySet()) {
      String lineId = entry.getKey();
      PlayerSkillLine line = entry.getValue();
      if (line == null || lineId == null || lineId.isBlank() || currentSkill.equals(lineId)) {
        continue;
      }

      double recoveredPressure = line.relaxStalenessForActivitySwitch();
      if (isBetterInspiredCandidate(lineId, recoveredPressure, inspiredCandidate, inspiredPressure)) {
        inspiredCandidate = lineId;
        inspiredPressure = recoveredPressure;
      }
    }

    assignInspiredCandidate(inspiredCandidate);
  }

  private void assignInspiredCandidate(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return;
    }

    AdaptConfig.XpIntegrity integrity = AdaptConfig.get().getXpIntegrity();
    if (integrity == null) {
      return;
    }

    long now = System.currentTimeMillis();
    long cooldownMillis = Math.max(0L, integrity.getInspiredCooldownMillis());
    if (!isInspiredCooldownComplete(now, cooldownMillis)) {
      return;
    }

    inspiredSkill = candidate;
    inspiredAssignedAt = now;
    inspiredPopupPending = integrity.isInspiredPopupEnabled();
  }

  private boolean isInspiredCooldownComplete(long now, long cooldownMillis) {
    if (inspiredAssignedAt <= 0) {
      return true;
    }

    long elapsed = now - inspiredAssignedAt;
    return elapsed >= 0 && elapsed >= cooldownMillis;
  }

  private boolean isBetterInspiredCandidate(String lineId, double pressure, String currentCandidate, double currentPressure) {
    if (pressure <= 0.0D) {
      return false;
    }
    if (currentCandidate == null) {
      return true;
    }

    int pressureComparison = Double.compare(pressure, currentPressure);
    return pressureComparison > 0 || (pressureComparison == 0 && lineId.compareTo(currentCandidate) < 0);
  }

  private void pulseInspired(AdaptPlayer p) {
    if (!inspiredPopupPending) {
      return;
    }

    inspiredPopupPending = false;
    AdaptConfig.XpIntegrity integrity = AdaptConfig.get().getXpIntegrity();
    if (integrity == null || !integrity.isInspiredPopupEnabled() || inspiredSkill == null || inspiredSkill.isBlank()) {
      return;
    }

    Skill<?> skill = p.getServer().getSkillRegistry().getSkill(inspiredSkill);
    if (skill == null) {
      return;
    }

    p.getActionBarNotifier().queue(ActionBarNotification.builder()
        .duration(1250)
        .group("inspired")
        .title(skill.getDisplayName() + C.RESET + " " + C.GREEN + AdaptLanguage.text(SnippetsMessages.XP_INSPIRED))
        .build());
  }

  private void normalizeInspiredAssignment() {
    inspiredSkill = inspiredSkill == null ? "" : inspiredSkill.trim();
    inspiredAssignedAt = Math.max(0L, inspiredAssignedAt);
    if (inspiredSkill.isEmpty()) {
      inspiredAssignedAt = 0;
    }
  }

  private void removeRetiredAxesContent() {
    PlayerSkillLine axes = skillLines.get("axes");
    if (axes != null) {
      for (String adaptationId : REMOVED_AXES_ADAPTATIONS) {
        axes.getAdaptations().remove(adaptationId);
      }
    }

    if (stats == null) {
      stats = new KMap<>();
    }
    for (String statKey : REMOVED_AXES_STATS) {
      stats.remove(statKey);
    }

    if (advancements == null) {
      advancements = new KSet<>();
    }
    for (String advancementKey : REMOVED_AXES_ADVANCEMENTS) {
      advancements.remove(advancementKey);
    }
  }

  private void removeRetiredDowsingContent() {
    PlayerSkillLine excavation = skillLines.get("excavation");
    if (excavation != null) {
      excavation.getAdaptations().remove(REMOVED_DOWSING_ADAPTATION);
    }

    if (stats == null) {
      stats = new KMap<>();
    }
    stats.remove(REMOVED_DOWSING_STAT);

    if (advancements == null) {
      advancements = new KSet<>();
    }
    for (String advancementKey : REMOVED_DOWSING_ADVANCEMENTS) {
      advancements.remove(advancementKey);
    }
  }

  private void removeRetiredRiftStepContent() {
    PlayerSkillLine rift = skillLines.get("rift");
    if (rift != null) {
      rift.getAdaptations().remove(REMOVED_RIFT_STEP_ADAPTATION);
    }

    if (stats == null) {
      stats = new KMap<>();
    }
    stats.remove(REMOVED_RIFT_STEP_STAT);

    if (advancements == null) {
      advancements = new KSet<>();
    }
    for (String advancementKey : REMOVED_RIFT_STEP_ADVANCEMENTS) {
      advancements.remove(advancementKey);
    }
  }

  public void addWisdom() {
    wisdom++;
  }

  public void clearXp() {
    for (PlayerSkillLine line : skillLines.values()) {
      line.setXp(0);
      line.setLastXP(0);
      line.setLastLevel(0);
      line.setPooledXp(0);
      line.setPooledNotifyXp(0);
      line.setPoolStartedAt(0);
      line.setPoolLastEarnAt(0);
      line.setMonotonyCounter(0);
      line.setMonotonyMultiplier(1.0);
      line.setLastXpTimestamp(0);
      line.setSkillStaleness(new PlayerSkillLine.RewardStalenessState());
      line.getActivityStaleness().clear();
      line.getAdaptations().clear();
    }
    masterXp = 1;
    lastMasterXp = 0;
    inspiredSkill = "";
    inspiredAssignedAt = 0;
    inspiredPopupPending = false;
    refreshLearnedIndex();
  }

  public void clearKnowledge() {
    for (PlayerSkillLine line : skillLines.values()) {
      line.setKnowledge(0);
    }
  }

  public void clearAdaptations() {
    for (PlayerSkillLine line : skillLines.values()) {
      line.getAdaptations().clear();
    }
    refreshLearnedIndex();
  }

  public void clearStats() {
    stats.clear();
  }

  public void clearDiscoveries() {
    seenBiomes = new Discovery<>();
    seenMobs = new Discovery<>();
    seenFoods = new Discovery<>();
    seenItems = new Discovery<>();
    seenRecipes = new Discovery<>();
    seenEnchants = new Discovery<>();
    seenWorlds = new Discovery<>();
    seenPeople = new Discovery<>();
    seenEnvironments = new Discovery<>();
    seenPotionEffects = new Discovery<>();
    seenBlocks = new Discovery<>();
  }

  public void clearMutations() {
    getMutationData().clearAll();
    refreshLearnedIndex();
  }

  public void pruneAdaptationsForPowerBudget() {
    if (isDebugLearning()) {
      return;
    }

    int usedPower = getUsedPower();
    int maxPower = getMaxPower();

    while (usedPower > maxPower) {
      String worstSkill = null;
      String worstAdaptation = null;
      int worstLevel = Integer.MAX_VALUE;

      for (java.util.Map.Entry<java.lang.String, art.arcane.adapt.api.world.PlayerSkillLine> skillEntry : skillLines.entrySet()) {
        for (java.util.Map.Entry<java.lang.String, art.arcane.adapt.api.world.PlayerAdaptation> adaptEntry : skillEntry.getValue().getAdaptations().entrySet()) {
          int level = adaptEntry.getValue().getLevel();
          if (level > 0 && level < worstLevel) {
            worstLevel = level;
            worstSkill = skillEntry.getKey();
            worstAdaptation = adaptEntry.getKey();
          }
        }
      }

      if (worstSkill == null) {
        break;
      }

      PlayerAdaptation adapt = skillLines.get(worstSkill).getAdaptations().get(worstAdaptation);
      if (adapt.getLevel() <= 1) {
        skillLines.get(worstSkill).getAdaptations().remove(worstAdaptation);
        usedPower -= 1;
      } else {
        adapt.setLevel(adapt.getLevel() - 1);
        usedPower -= 1;
      }
    }
    refreshLearnedIndex();
  }

  private void refreshLearnedIndex() {
    AdaptPlayer owner = runtimeOwner;
    if (owner != null && owner.getServer() != null) {
      owner.getServer().refreshLearnedAdaptations(owner);
    }
  }

  public void clearAll() {
    clearXp();
    clearKnowledge();
    clearAdaptations();
    clearStats();
    clearDiscoveries();
    advancements.clear();
    multipliers.clear();
    wisdom = 0;
    clearMutations();
  }

  public String toJson(boolean raw) {
    synchronized (skillLines) {
      for (PlayerSkillLine line : skillLines.values()) {
        if (line != null) {
          line.flushXpPool(null);
        }
      }
      return Json.toJson(this, !raw);
    }
  }
}
