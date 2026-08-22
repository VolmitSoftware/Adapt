package art.arcane.adapt.papi;

import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.NewtonCurve;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.math.M;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AdaptPlayerSnapshotBuilder {
  private static final double LEVEL_PRECISION = 0.000001D;

  private AdaptPlayerSnapshotBuilder() {
  }

  public static AdaptPlayerSnapshot build(
      AdaptPlayerSnapshot previous,
      PlayerData data,
      AdaptMutationView mutations,
      NewtonCurve curve,
      int configuredMaxLevel,
      double powerPerLevel
  ) {
    double masterXp = data.getMasterXp();
    double masterLevelExact;
    int level;
    String levelText;
    String masterXpText;

    if (previous != null && sameDouble(previous.masterXp(), masterXp)) {
      masterLevelExact = previous.masterLevelExact();
      level = previous.level();
      levelText = previous.levelText();
      masterXpText = previous.masterXpText();
    } else {
      masterLevelExact = curve.computeLevelForXP(masterXp, LEVEL_PRECISION);
      level = (int) masterLevelExact;
      levelText = PlaceholderValues.count(level);
      masterXpText = PlaceholderValues.num(masterXp);
    }

    Map<String, AdaptSkillLineSnapshot> previousSkills = previous == null ? Map.of() : previous.skills();
    Map<String, AdaptSkillLineSnapshot> skills = new HashMap<>();
    Map<String, Integer> adaptationLevels = new HashMap<>();
    int usedPower = 0;
    int knownSkills = 0;
    int learnedAdaptations = 0;

    for (Map.Entry<String, PlayerSkillLine> entry : data.getSkillLines().entrySet()) {
      String skillId = normalize(entry.getKey());
      PlayerSkillLine line = entry.getValue();

      if (skillId == null || line == null) {
        continue;
      }

      int lineLearned = 0;

      for (Map.Entry<String, PlayerAdaptation> owned : line.getAdaptations().entrySet()) {
        PlayerAdaptation adaptation = owned.getValue();

        if (adaptation == null || adaptation.getLevel() <= 0) {
          continue;
        }

        String adaptationId = normalize(owned.getKey());
        usedPower += adaptation.getLevel();
        lineLearned++;

        if (adaptationId != null) {
          adaptationLevels.put(adaptationId, adaptation.getLevel());
        }
      }

      learnedAdaptations += lineLearned;
      AdaptSkillLineSnapshot built = lineSnapshot(previousSkills.get(skillId), line, lineLearned, curve);
      skills.put(skillId, built);

      if (built.band().level() > 0) {
        knownSkills++;
      }
    }

    int maxPower = (int) (masterLevelExact * powerPerLevel);
    int availablePower = maxPower - usedPower;
    double multiplier = data.getMultiplier();
    long wisdom = data.getWisdom();

    return new AdaptPlayerSnapshot(
        masterXp,
        masterXpText,
        masterLevelExact,
        level,
        levelText,
        PlaceholderValues.count(configuredMaxLevel),
        multiplier,
        PlaceholderValues.num(multiplier),
        wisdom,
        PlaceholderValues.count(wisdom),
        maxPower,
        usedPower,
        availablePower,
        PlaceholderValues.count(availablePower),
        PlaceholderValues.count(maxPower),
        PlaceholderValues.count(usedPower),
        knownSkills,
        PlaceholderValues.count(knownSkills),
        learnedAdaptations,
        PlaceholderValues.count(learnedAdaptations),
        Map.copyOf(skills),
        Map.copyOf(adaptationLevels),
        zeroLine(previous, curve),
        mutations == null ? AdaptMutationView.unavailable() : mutations
    );
  }

  private static AdaptSkillLineSnapshot zeroLine(AdaptPlayerSnapshot previous, NewtonCurve curve) {
    if (previous != null) {
      return previous.zeroLine();
    }

    return new AdaptSkillLineSnapshot(
        band(0.0D, curve),
        0L,
        PlaceholderValues.count(0L),
        1.0D,
        PlaceholderValues.num(1.0D),
        0,
        PlaceholderValues.count(0)
    );
  }

  public static AdaptMutationView mutationView(
      MutationSnapshot source,
      boolean enabled,
      long combatLockRemainingMs,
      AdaptCatalogSnapshot catalog
  ) {
    if (source == null) {
      return AdaptMutationView.unavailable();
    }

    long remaining = Math.max(0L, combatLockRemainingMs);
    int expressed = source.expressed().size();
    String slotOneId = normalize(source.slotOneId());
    String slotTwoId = normalize(source.slotTwoId());

    return new AdaptMutationView(
        true,
        enabled,
        slotOneId == null ? "" : slotOneId,
        slotTwoId == null ? "" : slotTwoId,
        mutationName(catalog, source.slotOneId()),
        mutationName(catalog, source.slotTwoId()),
        source.slotOneUnlocked(),
        source.slotTwoUnlocked(),
        source.perfect(),
        expressed,
        PlaceholderValues.count(expressed),
        remaining,
        PlaceholderValues.num(remaining / 1000.0D),
        remaining <= 0L,
        source
    );
  }

  private static String mutationName(AdaptCatalogSnapshot catalog, String mutationId) {
    String id = normalize(mutationId);

    if (id == null) {
      return "";
    }

    if (catalog == null) {
      return id;
    }

    AdaptCatalogMutation mutation = catalog.mutation(id);
    return mutation == null ? id : mutation.nameText();
  }

  private static AdaptSkillLineSnapshot lineSnapshot(
      AdaptSkillLineSnapshot cached,
      PlayerSkillLine line,
      int learnedAdaptations,
      NewtonCurve curve
  ) {
    double xp = line.getXp();
    long knowledge = line.getKnowledge();
    double multiplier = line.getMultiplier();
    boolean sameXp = cached != null && sameDouble(cached.band().xp(), xp);

    if (sameXp
        && cached.knowledge() == knowledge
        && sameDouble(cached.multiplier(), multiplier)
        && cached.learnedAdaptations() == learnedAdaptations) {
      return cached;
    }

    AdaptSkillLevelBand band = sameXp ? cached.band() : band(xp, curve);

    return new AdaptSkillLineSnapshot(
        band,
        knowledge,
        PlaceholderValues.count(knowledge),
        multiplier,
        PlaceholderValues.num(multiplier),
        learnedAdaptations,
        PlaceholderValues.count(learnedAdaptations)
    );
  }

  static AdaptSkillLevelBand band(double xp, NewtonCurve curve) {
    double absoluteLevel = curve.computeLevelForXP(xp, LEVEL_PRECISION);
    int level = (int) Math.floor(absoluteLevel);
    double progress = absoluteLevel - level;
    double currentLevelXp = curve.getXPForLevel(level);
    double nextLevelXp = curve.getXPForLevel(level + 1);
    double xpToNext = M.lerp(nextLevelXp - currentLevelXp, 0, absoluteLevel - (int) absoluteLevel);

    return new AdaptSkillLevelBand(
        xp,
        level,
        absoluteLevel,
        PlaceholderValues.count(level),
        PlaceholderValues.num(xp),
        PlaceholderValues.num(progress),
        PlaceholderValues.percent(progress),
        PlaceholderValues.num(xpToNext),
        PlaceholderValues.num(currentLevelXp),
        PlaceholderValues.num(nextLevelXp)
    );
  }

  private static boolean sameDouble(double left, double right) {
    return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return null;
    }

    String trimmed = raw.trim().toLowerCase(Locale.ROOT);
    return trimmed.isEmpty() ? null : trimmed;
  }
}
