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

import java.util.ArrayList;
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
    List<Adaptation<?>> dependents = new ArrayList<>();
    for (Skill<?> skill : registeredSkills()) {
      for (Adaptation<?> candidate : skill.getAdaptations()) {
        if (candidate != adaptation
            && requiresAbove(candidate, adaptation.getName(), targetLevel)
            && learnedLevel(candidate, data) > 0) {
          dependents.add(candidate);
        }
      }
    }
    return dependents;
  }

  public static List<Removal> removeUnmet(PlayerData data, String playerName) {
    List<Removal> removals = new ArrayList<>();
    boolean removedAny = true;
    while (removedAny) {
      removedAny = false;
      for (Skill<?> skill : registeredSkills()) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          Removal removal = removeIfUnmet(adaptation, data, playerName);
          if (removal != null) {
            removals.add(removal);
            removedAny = true;
          }
        }
      }
    }
    return removals;
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

  private static Adaptation<?> find(String adaptationId) {
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

  private static int learnedLevel(Adaptation<?> adaptation, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(adaptation.getSkill().getName());
    PlayerAdaptation stored = line == null ? null : line.getAdaptation(adaptation.getName());
    return stored == null || stored.isRegionGranted() ? 0 : Math.max(0, stored.getLevel());
  }

  private static boolean requiresAbove(Adaptation<?> candidate, String adaptationName, int level) {
    for (AdaptationRequirement requirement : candidate.getRequirements()) {
      if (requirement.level() > level && requirement.adaptationId().equalsIgnoreCase(adaptationName)) {
        return true;
      }
    }
    return false;
  }

  private static Removal removeIfUnmet(Adaptation<?> adaptation, PlayerData data, String playerName) {
    int level = learnedLevel(adaptation, data);
    if (level <= 0) {
      return null;
    }
    List<AdaptationRequirement> missing = missing(adaptation, data);
    if (missing.isEmpty()) {
      return null;
    }

    data.getSkillLineNullable(adaptation.getSkill().getName()).setAdaptation(adaptation, 0);
    Adapt.info("Removed " + adaptation.getName() + " level " + level + " from " + playerName
        + " because its requirements are no longer met: " + requirementIds(missing) + ".");
    return new Removal(adaptation, level);
  }

  private static String requirementIds(List<AdaptationRequirement> requirements) {
    List<String> ids = new ArrayList<>(requirements.size());
    for (AdaptationRequirement requirement : requirements) {
      ids.add(requirement.adaptationId() + ":" + requirement.level());
    }
    return String.join(SEPARATOR, ids);
  }

  private static List<Skill<?>> registeredSkills() {
    Adapt instance = Adapt.instance;
    AdaptServer server = instance == null ? null : instance.getAdaptServer();
    SkillRegistry registry = server == null ? null : server.getSkillRegistry();
    return registry == null ? List.of() : registry.getSkills();
  }

  public record Removal(Adaptation<?> adaptation, int previousLevel) {
  }
}
