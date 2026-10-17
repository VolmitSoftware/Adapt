package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class AdaptationRequirements {
  private static final String SEPARATOR = ", ";

  private AdaptationRequirements() {
  }

  public static Adaptation<?> find(String adaptationId) {
    if (adaptationId == null) {
      return null;
    }
    for (Skill<?> skill : registeredSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.getName().equalsIgnoreCase(adaptationId)) {
          return adaptation;
        }
      }
    }
    return null;
  }

  public static List<AdaptationRequirement> missing(Adaptation<?> adaptation, PlayerData data) {
    List<AdaptationRequirement> requirements = adaptation.getRequirements();
    if (requirements.isEmpty()) {
      return List.of();
    }

    List<AdaptationRequirement> missing = new ArrayList<>(requirements.size());
    for (AdaptationRequirement requirement : requirements) {
      Adaptation<?> required = find(requirement.adaptationId());
      if (required == null || learnedLevel(required, data) < requirement.level()) {
        missing.add(requirement);
      }
    }
    return missing;
  }

  public static List<Adaptation<?>> dependents(Adaptation<?> adaptation, PlayerData data, int targetLevel) {
    return brokenDependents(adaptation, data, learnedLevel(adaptation, data), targetLevel);
  }

  public static void forfeitLevels(List<Lowering> lowered, PlayerData data, String playerName) {
    for (Lowering lowering : lowered) {
      clearReceipts(lowering.adaptation(), data, learnedLevel(lowering.adaptation(), data), lowering.previousLevel());
    }

    Deque<Lowering> pending = new ArrayDeque<>(lowered);
    while (!pending.isEmpty()) {
      Lowering current = pending.poll();
      int currentLevel = learnedLevel(current.adaptation(), data);
      for (Adaptation<?> dependent : brokenDependents(current.adaptation(), data, current.previousLevel(), currentLevel)) {
        pending.add(remove(dependent, data, current, currentLevel, playerName));
      }
    }
  }

  public static void warnUnresolved(List<Skill<?>> skills) {
    Set<String> known = new HashSet<>();
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        known.add(adaptation.getName().toLowerCase(Locale.ROOT));
      }
    }
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        for (AdaptationRequirement requirement : adaptation.getRequirements()) {
          if (!known.contains(requirement.adaptationId())) {
            Adapt.warn("Adaptation " + adaptation.getName() + " requires unknown adaptation "
                + requirement.adaptationId() + "; players cannot learn it until that adaptation is registered.");
          }
        }
      }
    }
  }

  public static String describe(AdaptationRequirement requirement) {
    Adaptation<?> adaptation = find(requirement.adaptationId());
    return AdaptLanguage.text(
        GuiMessages.REQUIREMENT_LEVEL,
        untrusted("adaptation", adaptation == null ? requirement.adaptationId() : plainName(adaptation)),
        trusted("level", requirement.level())
    );
  }

  public static String describe(List<AdaptationRequirement> requirements) {
    List<String> described = new ArrayList<>(requirements.size());
    for (AdaptationRequirement requirement : requirements) {
      described.add(describe(requirement));
    }
    return String.join(SEPARATOR, described);
  }

  public static String names(List<Adaptation<?>> adaptations) {
    List<String> names = new ArrayList<>(adaptations.size());
    for (Adaptation<?> adaptation : adaptations) {
      names.add(plainName(adaptation));
    }
    return String.join(SEPARATOR, names);
  }

  public static String plainName(Adaptation<?> adaptation) {
    String displayName = adaptation.getDisplayName();
    return displayName == null ? adaptation.getName() : C.stripColor(displayName);
  }

  private static int learnedLevel(Adaptation<?> adaptation, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(adaptation.getSkill().getName());
    PlayerAdaptation stored = line == null ? null : line.getAdaptation(adaptation.getName());
    return stored == null || stored.isRegionGranted() ? 0 : Math.max(0, stored.getLevel());
  }

  private static List<Adaptation<?>> brokenDependents(
      Adaptation<?> adaptation,
      PlayerData data,
      int fromLevel,
      int toLevel
  ) {
    List<Adaptation<?>> dependents = new ArrayList<>();
    if (toLevel >= fromLevel) {
      return dependents;
    }
    for (Skill<?> skill : registeredSkills()) {
      for (Adaptation<?> candidate : skill.getAdaptations()) {
        if (candidate != adaptation
            && breaksRequirement(candidate, adaptation.getName(), fromLevel, toLevel)
            && learnedLevel(candidate, data) > 0) {
          dependents.add(candidate);
        }
      }
    }
    return dependents;
  }

  private static boolean breaksRequirement(Adaptation<?> candidate, String adaptationName, int fromLevel, int toLevel) {
    for (AdaptationRequirement requirement : candidate.getRequirements()) {
      if (requirement.level() > toLevel
          && requirement.level() <= fromLevel
          && requirement.adaptationId().equalsIgnoreCase(adaptationName)) {
        return true;
      }
    }
    return false;
  }

  private static Lowering remove(
      Adaptation<?> dependent,
      PlayerData data,
      Lowering cause,
      int causeLevel,
      String playerName
  ) {
    int previousLevel = learnedLevel(dependent, data);
    data.getSkillLineNullable(dependent.getSkill().getName()).setAdaptation(dependent, 0);
    clearReceipts(dependent, data, 0, previousLevel);
    Adapt.info("Removed " + dependent.getName() + " level " + previousLevel + " from " + playerName
        + " because " + cause.adaptation().getName() + " dropped from level " + cause.previousLevel()
        + " to " + causeLevel + ".");
    return new Lowering(dependent, previousLevel);
  }

  private static void clearReceipts(Adaptation<?> adaptation, PlayerData data, int targetLevel, int previousLevel) {
    PlayerSkillLine line = data.getSkillLineNullable(adaptation.getSkill().getName());
    if (line != null) {
      AdaptationLearningTransaction.clearRefundReceipts(line, adaptation.getName(), targetLevel, previousLevel);
    }
  }

  private static List<Skill<?>> registeredSkills() {
    Adapt instance = Adapt.instance;
    AdaptServer server = instance == null ? null : instance.getAdaptServer();
    SkillRegistry registry = server == null ? null : server.getSkillRegistry();
    return registry == null ? List.of() : registry.getSkills();
  }

  public record Lowering(Adaptation<?> adaptation, int previousLevel) {
  }
}
