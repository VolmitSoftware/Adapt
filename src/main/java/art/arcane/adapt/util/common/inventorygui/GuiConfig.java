package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import org.bukkit.Material;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiConfig {
  public static final int MIN_FIXED_SKILL_ROWS = 2;
  public static final int UNRANKED = Integer.MAX_VALUE;
  private static final String SKILL_ICONS = "gui.skillIcons";
  private static final String ADAPTATION_ICONS = "gui.adaptationIcons";
  private static final String SKILL_ORDER = "gui.skillOrder";
  private static final String ADAPTATION_ORDER = "gui.adaptationOrder";
  private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
  private static volatile AdaptConfig warnedFor = null;

  private GuiConfig() {
  }

  public static int skillsGuiRows() {
    AdaptConfig.Gui gui = gui();
    return clampSkillsGuiRows(gui == null ? 0 : gui.getSkillsGuiRows());
  }

  public static CustomModel skillModel(String skillName, Material defaultIcon, CustomModel model) {
    return applyConfiguredMaterial(model, defaultIcon, skillIcon(skillName, defaultIcon));
  }

  public static CustomModel adaptationModel(String adaptationName, Material defaultIcon, CustomModel model) {
    return applyConfiguredMaterial(model, defaultIcon, adaptationIcon(adaptationName, defaultIcon));
  }

  public static Material skillIcon(String skillName, Material defaultIcon) {
    AdaptConfig.Gui gui = gui();
    return resolveMaterial(gui == null ? null : gui.getSkillIcons(), SKILL_ICONS, skillName, defaultIcon);
  }

  public static Material adaptationIcon(String adaptationName, Material defaultIcon) {
    AdaptConfig.Gui gui = gui();
    return resolveMaterial(gui == null ? null : gui.getAdaptationIcons(), ADAPTATION_ICONS, adaptationName, defaultIcon);
  }

  public static Map<String, Integer> skillOrder(Collection<String> knownNames) {
    AdaptConfig.Gui gui = gui();
    return ranks(gui == null ? null : gui.getSkillOrder(), knownNames, SKILL_ORDER);
  }

  public static Map<String, Integer> adaptationOrder(String skillName, Collection<String> knownNames) {
    AdaptConfig.Gui gui = gui();
    Map<String, List<String>> configured = gui == null ? null : gui.getAdaptationOrder();
    List<String> order = configured == null ? null : lookupList(configured, skillName);
    return ranks(order, knownNames, ADAPTATION_ORDER + "." + skillName);
  }

  public static int rankOf(Map<String, Integer> ranks, String name) {
    if (ranks == null || ranks.isEmpty() || name == null) {
      return UNRANKED;
    }

    Integer rank = ranks.get(name.toLowerCase(Locale.ROOT));
    return rank == null ? UNRANKED : rank;
  }

  public static void resetWarnings() {
    WARNED.clear();
    warnedFor = null;
  }

  static int clampSkillsGuiRows(int configured) {
    if (configured < 0) {
      warnOnce("skillsGuiRows:" + configured, "gui.skillsGuiRows=" + configured + " is out of range, auto-sizing instead.");
      return 0;
    }
    if (configured == 0) {
      return 0;
    }
    if (configured < MIN_FIXED_SKILL_ROWS) {
      warnOnce(
          "skillsGuiRows:" + configured,
          "gui.skillsGuiRows=" + configured + " raised to " + MIN_FIXED_SKILL_ROWS + ", the navigation row needs its own row."
      );
      return MIN_FIXED_SKILL_ROWS;
    }
    if (configured > GuiLayout.MAX_ROWS) {
      warnOnce("skillsGuiRows:" + configured, "gui.skillsGuiRows=" + configured + " clamped to " + GuiLayout.MAX_ROWS + ".");
      return GuiLayout.MAX_ROWS;
    }

    return configured;
  }

  static CustomModel applyConfiguredMaterial(CustomModel model, Material defaultIcon, Material configured) {
    if (configured == null || configured == defaultIcon) {
      return model;
    }
    if (model == null) {
      return new CustomModel(configured, 0, CustomModel.EMPTY_KEY);
    }
    if (model.model() != 0 || model.material() != defaultIcon) {
      return model;
    }

    return new CustomModel(configured, 0, model.modelKey());
  }

  static Material resolveMaterial(Map<String, String> overrides, String section, String name, Material defaultIcon) {
    if (overrides == null || overrides.isEmpty() || name == null || name.isBlank()) {
      return defaultIcon;
    }

    String configured = lookup(overrides, name);
    if (configured == null || configured.isBlank()) {
      return defaultIcon;
    }

    Material material = parseMaterial(configured);
    if (material == null) {
      warnOnce(
          section + ":" + name.toLowerCase(Locale.ROOT),
          section + "." + name + "=" + configured.trim() + " is not a usable material, keeping " + defaultIcon.name() + "."
      );
      return defaultIcon;
    }

    return material;
  }

  static Map<String, Integer> ranks(List<String> configured, Collection<String> knownNames, String section) {
    if (configured == null || configured.isEmpty()) {
      return Map.of();
    }

    Set<String> known = new KSet<>();
    if (knownNames != null) {
      for (String name : knownNames) {
        if (name != null && !name.isBlank()) {
          known.add(name.toLowerCase(Locale.ROOT));
        }
      }
    }

    KMap<String, Integer> ranks = new KMap<>();
    int next = 0;
    for (String entry : configured) {
      if (entry == null || entry.isBlank()) {
        continue;
      }

      String key = entry.trim().toLowerCase(Locale.ROOT);
      if (!known.isEmpty() && !known.contains(key)) {
        warnOnce(section + ":" + key, section + " lists unknown entry '" + entry.trim() + "', ignoring it.");
        continue;
      }
      if (ranks.containsKey(key)) {
        continue;
      }

      ranks.put(key, next);
      next++;
    }

    return ranks;
  }

  private static AdaptConfig.Gui gui() {
    AdaptConfig config = AdaptConfig.get();
    resetWarningsOnReload(config);
    return config == null ? null : config.getGui();
  }

  private static String lookup(Map<String, String> overrides, String name) {
    String direct = overrides.get(name);
    if (direct != null) {
      return direct;
    }

    for (Map.Entry<String, String> entry : overrides.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }

    return null;
  }

  private static List<String> lookupList(Map<String, List<String>> overrides, String name) {
    if (name == null || name.isBlank()) {
      return null;
    }

    List<String> direct = overrides.get(name);
    if (direct != null) {
      return direct;
    }

    KList<String> merged = new KList<>();
    for (Map.Entry<String, List<String>> entry : overrides.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
        merged.addAll(entry.getValue());
      }
    }

    return merged.isEmpty() ? null : merged;
  }

  private static Material parseMaterial(String configured) {
    String cleaned = configured.trim();
    int namespace = cleaned.indexOf(':');
    if (namespace >= 0) {
      cleaned = cleaned.substring(namespace + 1);
    }

    try {
      Material material = Material.valueOf(cleaned.toUpperCase(Locale.ROOT).replace(' ', '_'));
      if (material == Material.AIR || material.isLegacy()) {
        return null;
      }
      return material;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static void warnOnce(String key, String message) {
    resetWarningsOnReload(AdaptConfig.get());
    if (WARNED.add(key)) {
      Adapt.warn(message);
    }
  }

  private static void resetWarningsOnReload(AdaptConfig config) {
    if (warnedFor == config) {
      return;
    }

    synchronized (GuiConfig.class) {
      if (warnedFor != config) {
        WARNED.clear();
        warnedFor = config;
      }
    }
  }
}
