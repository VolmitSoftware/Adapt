package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record AdaptationRequirement(String adaptationId, int level) {
  public static List<AdaptationRequirement> parseAll(String ownerId, List<String> entries) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }

    List<AdaptationRequirement> parsed = new ArrayList<>(entries.size());
    for (String entry : entries) {
      AdaptationRequirement requirement = parse(entry);
      if (requirement == null) {
        Adapt.warn("Ignored malformed requirement \"" + entry + "\" in adaptations/" + ownerId
            + ".toml; use adaptationId:level with a level of at least 1.");
        continue;
      }
      parsed.add(requirement);
    }
    return List.copyOf(parsed);
  }

  private static AdaptationRequirement parse(String entry) {
    if (entry == null) {
      return null;
    }
    int split = entry.indexOf(':');
    if (split < 0) {
      return null;
    }
    String adaptationId = entry.substring(0, split).trim().toLowerCase(Locale.ROOT);
    String levelText = entry.substring(split + 1).trim();
    if (adaptationId.isEmpty() || levelText.isEmpty() || levelText.length() > 9) {
      return null;
    }
    for (int index = 0; index < levelText.length(); index++) {
      char digit = levelText.charAt(index);
      if (digit < '0' || digit > '9') {
        return null;
      }
    }
    int level = Integer.parseInt(levelText);
    return level < 1 ? null : new AdaptationRequirement(adaptationId, level);
  }
}
