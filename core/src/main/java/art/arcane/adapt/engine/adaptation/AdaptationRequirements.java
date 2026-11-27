package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class AdaptationRequirements {
  private static final String SEPARATOR = ", ";
  private static final Set<String> WARNED_UNRESOLVED = ConcurrentHashMap.newKeySet();

  private AdaptationRequirements() {
  }

  public static Adaptation<?> find(Id adaptationId) {
    if (adaptationId == null) {
      return null;
    }
    for (Skill<?> skill : registeredSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.getId().equals(adaptationId)) {
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
      Adaptation<?> required = find(requirement.adaptation());
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
    Set<Id> known = new HashSet<>();
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        known.add(adaptation.getId());
      }
    }
    for (Skill<?> skill : skills) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        for (AdaptationRequirement requirement : adaptation.getRequirements()) {
          if (!known.contains(requirement.adaptation()) && WARNED_UNRESOLVED.add(adaptation.getId().toString() + "->" + requirement.adaptation())) {
            AdaptLog.warn("Adaptation " + adaptation.getId().toString() + " requires unknown adaptation "
                + requirement.adaptation() + "; players cannot learn it until that adaptation is registered.");
          }
        }
      }
    }
  }

  public static String describe(AdaptationRequirement requirement) {
    Adaptation<?> adaptation = find(requirement.adaptation());
    return AdaptLanguage.text(
        GuiMessages.REQUIREMENT_LEVEL,
        untrusted("adaptation", adaptation == null ? requirement.adaptation().toString() : plainName(adaptation)),
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
    return displayName == null ? adaptation.getId().toString() : C.stripColor(displayName);
  }

  private static int learnedLevel(Adaptation<?> adaptation, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(SkillHost.lineKey(adaptation.getSkill()));
    PlayerAdaptation stored = line == null ? null : line.getAdaptation(adaptation.getId().toString());
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
            && breaksRequirement(candidate, adaptation.getId(), fromLevel, toLevel)
            && learnedLevel(candidate, data) > 0) {
          dependents.add(candidate);
        }
      }
    }
    return dependents;
  }

  private static boolean breaksRequirement(Adaptation<?> candidate, Id adaptationName, int fromLevel, int toLevel) {
    for (AdaptationRequirement requirement : candidate.getRequirements()) {
      if (requirement.level() > toLevel
          && requirement.level() <= fromLevel
          && requirement.adaptation().equals(adaptationName)) {
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
    data.getSkillLineNullable(SkillHost.lineKey(dependent.getSkill())).setAdaptation(dependent, 0);
    clearReceipts(dependent, data, 0, previousLevel);
    AdaptLog.info("Removed " + dependent.getId().toString() + " level " + previousLevel + " from " + playerName
        + " because " + cause.adaptation().getId().toString() + " dropped from level " + cause.previousLevel()
        + " to " + causeLevel + ".");
    return new Lowering(dependent, previousLevel);
  }

  private static void clearReceipts(Adaptation<?> adaptation, PlayerData data, int targetLevel, int previousLevel) {
    PlayerSkillLine line = data.getSkillLineNullable(SkillHost.lineKey(adaptation.getSkill()));
    if (line != null) {
      AdaptationLearningTransaction.clearRefundReceipts(line, adaptation.getId().toString(), targetLevel, previousLevel);
    }
  }

  private static List<Skill<?>> registeredSkills() {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    SkillRegistry registry = server == null ? null : server.getSkillRegistry();
    return registry == null ? List.of() : registry.getSkills();
  }

  public record Lowering(Adaptation<?> adaptation, int previousLevel) {
  }
}
