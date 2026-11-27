package art.arcane.adapt.client.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lookups over one {@link CatalogView}, and what the chest menus would show a player as the server reported it in
 * {@link PlayerAccess}: which skills the skills list shows in which order, and a skill page's adaptation rows.
 * Adaptations carry their catalog name, which names their skill wherever that is not shown, except through
 * {@link #shown} and {@link #groups}, which serve their own skill's pages under the short names the server sent.
 */
public final class CatalogIndex {
  private final CatalogView catalog;
  private final Map<String, String> shortNames;
  private final Map<String, SkillView> skills = new LinkedHashMap<>();
  private final Map<String, AdaptationView> adaptations = new LinkedHashMap<>();
  private final Map<String, AdaptationView> shown = new LinkedHashMap<>();

  public CatalogIndex(CatalogView catalog, Map<String, String> shortNames) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.shortNames = Objects.requireNonNull(shortNames, "shortNames");
    for (SkillView skill : catalog.skills()) {
      skills.put(skill.id(), skill);
      for (AdaptationView adaptation : skill.adaptations()) {
        adaptations.put(adaptation.id(), adaptation);
        String shortName = shortNames.get(adaptation.id());
        shown.put(adaptation.id(), shortName == null ? adaptation : adaptation.named(shortName));
      }
    }
  }

  public CatalogView catalog() {
    return catalog;
  }

  /** The short names this index was built with, by adaptation id. */
  public Map<String, String> shortNames() {
    return shortNames;
  }

  public SkillView skill(String id) {
    return skills.get(id);
  }

  public AdaptationView adaptation(String id) {
    return adaptations.get(id);
  }

  /** The adaptation as its own skill's pages show it: under its short name when the server sent one. */
  public AdaptationView shown(String id) {
    return shown.get(id);
  }

  public List<AdaptationView> adaptations() {
    return List.copyOf(adaptations.values());
  }

  /** Display name of an adaptation, or its id when the catalog does not know it. */
  public String adaptationName(String id) {
    AdaptationView adaptation = adaptations.get(id);
    return adaptation == null ? id : adaptation.name();
  }

  /** The skills the chest skills menu lists for the player, in its order; skills the catalog does not know are left out. */
  public List<SkillView> visibleSkills(PlayerView player) {
    List<SkillView> shown = new ArrayList<>();
    for (String id : access(player).skills()) {
      SkillView skill = skills.get(id);
      if (skill != null) {
        shown.add(skill);
      }
    }
    return shown;
  }

  /**
   * The adaptation rows the chest page of {@code skillId} shows the player, in its order and as {@link #shown}; empty
   * when the player may not open that page. Adaptations the catalog does not know are left out, and so are rows left
   * empty by that.
   */
  public List<List<AdaptationView>> groups(PlayerView player, String skillId) {
    List<List<AdaptationView>> arranged = new ArrayList<>();
    for (List<String> row : access(player).rows(skillId)) {
      List<AdaptationView> views = new ArrayList<>();
      for (String id : row) {
        AdaptationView adaptation = shown.get(id);
        if (adaptation != null) {
          views.add(adaptation);
        }
      }
      if (!views.isEmpty()) {
        arranged.add(List.copyOf(views));
      }
    }
    return arranged;
  }

  /** What the player may see and do, or {@link PlayerAccess#NONE} before a player view arrived. */
  public static PlayerAccess access(PlayerView player) {
    return player == null ? PlayerAccess.NONE : player.access();
  }

  /** The player's line in {@code skillId}, or null. */
  public static SkillLineView line(PlayerView player, String skillId) {
    if (player == null) {
      return null;
    }
    for (SkillLineView line : player.skills()) {
      if (line.skillId().equals(skillId)) {
        return line;
      }
    }
    return null;
  }

  /** The paid level of an adaptation; 0 when not learned. */
  public static int level(PlayerView player, String adaptationId) {
    if (player == null) {
      return 0;
    }
    Integer level = player.adaptationLevels().get(adaptationId);
    return level == null ? 0 : Math.max(0, level);
  }

  /** How many of a skill's adaptations the player holds: the power that skill uses, one per adaptation. */
  public static int powerUsed(PlayerView player, SkillView skill) {
    int held = 0;
    for (AdaptationView adaptation : skill.adaptations()) {
      if (level(player, adaptation.id()) > 0) {
        held++;
      }
    }
    return held;
  }

  /** The value a preference resolves to for the player, or its default when the player view does not list it. */
  public static String preference(PlayerView player, PreferenceOwner owner, String ownerId, PreferenceView preference) {
    if (player != null) {
      PreferenceKey key = new PreferenceKey(owner, ownerId, preference.id());
      for (PreferenceValue value : player.preferences()) {
        if (value.key().equals(key)) {
          return value.value();
        }
      }
    }
    return preference.defaultValue();
  }
}
