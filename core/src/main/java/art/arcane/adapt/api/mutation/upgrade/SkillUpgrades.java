package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.adapt.localization.catalog.UpgradeMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.blocks.UpgradedAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.sound.MutationSoundPlayer;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.platform.recipe.RecipeSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The 72 upgraded adaptations by convention, and the rule that decides which adaptations of a skill a player may hold.
 *
 * <p>An upgrade of skill {@code s} named {@code "Duelist's Verdict"} has the id {@code upgraded-s-duelists-verdict},
 * the localization prefix {@code upgrade.s.duelists_verdict} and the class
 * {@code art.arcane.adapt.content.adaptation.<package>.upgraded.DuelistsVerdict}, where {@code <package>} is the
 * skill's existing adaptation package.
 */
public final class SkillUpgrades {
  public static final String ID_PREFIX = "upgraded-";
  private static final String CONTENT_PACKAGE = "art.arcane.adapt.content.adaptation.";
  private static final Map<String, String> PACKAGES = Map.ofEntries(
      Map.entry("swords", "sword"),
      Map.entry("axes", "axe"),
      Map.entry("unarmed", "unarmed"),
      Map.entry("ranged", "ranged"),
      Map.entry("blocking", "blocking"),
      Map.entry("stealth", "stealth"),
      Map.entry("hunter", "hunter"),
      Map.entry("tragoul", "tragoul"),
      Map.entry("agility", "agility"),
      Map.entry("kinetics", "kinetics"),
      Map.entry("rift", "rift"),
      Map.entry("chronos", "chronos"),
      Map.entry("pickaxe", "pickaxe"),
      Map.entry("excavation", "excavation"),
      Map.entry("herbalism", "herbalism"),
      Map.entry("architect", "architect"),
      Map.entry("crafting", "crafting"),
      Map.entry("enchanting", "enchanting"),
      Map.entry("brewing", "brewing"),
      Map.entry("discovery", "discovery"),
      Map.entry("taming", "taming"),
      Map.entry("golemancy", "golemancy"),
      Map.entry("seaborne", "seaborrne"),
      Map.entry("nether", "nether"));
  private static final Map<String, UpgradeSpec> BY_ID = index();

  /** Plays every upgrade's sound cue, so the per-adaptation cooldown and pitch rotation hold across the server. */
  public static final MutationSoundPlayer SOUNDS = new MutationSoundPlayer(System::currentTimeMillis);

  private SkillUpgrades() {
  }

  /** All 72 specs, in catalog order. */
  public static List<UpgradeSpec> all() {
    return List.copyOf(BY_ID.values());
  }

  /** The spec of an upgrade id; fails for an id that is not one of the 72. */
  public static UpgradeSpec require(String id) {
    UpgradeSpec spec = BY_ID.get(id);
    if (spec == null) {
      throw new IllegalArgumentException("No upgraded adaptation has the id " + id + "; expected one of " + BY_ID.keySet());
    }
    return spec;
  }

  public static boolean isUpgradeId(String id) {
    return BY_ID.containsKey(id);
  }

  /**
   * Whether the player may hold this adaptation: an upgrade only while they hold a mutation containing its skill, a
   * mutated adaptation only while they hold its mutation, a mutation block never, and any other adaptation only while
   * they have not mutated its skill, since the upgrades replace a mutated skill's originals. A signature is never held;
   * it reads the level of the mutated adaptation it replaces.
   */
  public static boolean holdable(Adaptation<?> adaptation, PlayerData data) {
    if (adaptation == null || data == null || adaptation.getSkill() == null || adaptation instanceof MutationBlock<?>
        || adaptation instanceof Signature<?>) {
      return false;
    }
    if (adaptation instanceof MutationAdaptation mutated) {
      return data.getMutations().contains(mutated.getMutation());
    }
    boolean mutated = data.mutationOf(adaptation.getSkill().getName()) != null;
    return mutated == (adaptation instanceof SkillUpgrade<?>);
  }

  /**
   * The level a player who mutated the adaptation's skill works with it at without learning it: for an original that
   * no upgrade of its skill fuses, its free {@link #keptFloor} or the level the player raised it to with the mutated
   * line's knowledge, whichever is higher; 0 for anything else.
   */
  public static int heritageLevel(Adaptation<?> adaptation, PlayerData data) {
    int floor = keptFloor(adaptation, data);
    if (floor <= 0) {
      return 0;
    }
    return Math.max(floor, Math.min(storedLevel(adaptation, data), adaptation.getMaxLevel()));
  }

  /**
   * The free level a player who mutated the adaptation's skill keeps it at: {@link #keptLevel} for an original that no
   * upgrade of its skill fuses, so what it does and the items and recipes it made keep working; 0 for anything else.
   * Levels above it are bought and refunded like any learned level.
   */
  public static int keptFloor(Adaptation<?> adaptation, PlayerData data) {
    if (data == null || !kept(adaptation) || data.mutationOf(adaptation.getSkill().getName()) == null) {
      return 0;
    }
    return keptLevel(adaptation);
  }

  /**
   * The level the player paid to raise a kept original to, stored on the mutated line; 0 while it sits at its free
   * floor. A raised original takes a power slot like any learned adaptation.
   */
  public static int raisedLevel(Adaptation<?> adaptation, PlayerData data) {
    int floor = keptFloor(adaptation, data);
    if (floor <= 0) {
      return 0;
    }
    int stored = Math.min(storedLevel(adaptation, data), adaptation.getMaxLevel());
    return stored > floor ? stored : 0;
  }

  private static int storedLevel(Adaptation<?> adaptation, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(adaptation.getSkill().getName());
    PlayerAdaptation stored = line == null ? null : line.getAdaptation(adaptation.getName());
    return stored == null || stored.isRegionGranted() ? 0 : Math.max(0, stored.getLevel());
  }

  /**
   * The level a mutated skill keeps the original at: its kept level, raised to the level its recipes need so every
   * item it unlocked stays craftable, and never above its top level.
   */
  public static int keptLevel(Adaptation<?> adaptation) {
    return UpgradeCaches.KEPT_LEVELS.computeIfAbsent(adaptation, SkillUpgrades::resolveKeptLevel);
  }

  private static int resolveKeptLevel(Adaptation<?> adaptation) {
    int level = adaptation.getKeptLevel();
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      level = Math.max(level, recipe.requiredLevel());
    }
    return Math.max(1, Math.min(level, adaptation.getMaxLevel()));
  }

  /** Whether the adaptation is an original that no upgrade of its skill fuses, so its skill's mutations keep it. */
  public static boolean kept(Adaptation<?> adaptation) {
    return adaptation != null && adaptation.getSkill() != null && !(adaptation instanceof SkillUpgrade<?>)
        && !(adaptation instanceof MutationAdaptation) && !(adaptation instanceof MutationBlock<?>)
        && !(adaptation instanceof Signature<?>) && !UpgradeLoans.fused(adaptation);
  }

  /** The originals of {@code skill} that its mutations keep, in registration order. */
  public static List<Adaptation<?>> kept(Skill<?> skill) {
    if (skill == null) {
      return List.of();
    }
    List<Adaptation<?>> kept = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (kept(adaptation)) {
        kept.add(adaptation);
      }
    }
    return List.copyOf(kept);
  }

  /**
   * Names of the originals the player runs without having learned them: every kept original of a mutated skill, and
   * every original that an upgrade the player learned fuses.
   */
  public static Set<String> granted(PlayerData data, Function<String, Skill<?>> skills) {
    List<SkillMutation> mutations = data == null ? List.of() : data.getMutations();
    if (mutations.isEmpty()) {
      return Set.of();
    }
    Set<String> granted = new HashSet<>();
    for (SkillMutation mutation : mutations) {
      PlayerSkillLine line = data.getSkillLineNullable(mutation.id());
      for (String parent : List.of(mutation.lead(), mutation.partner())) {
        Skill<?> skill = skills.apply(parent);
        if (skill == null) {
          continue;
        }
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (kept(adaptation) || (line != null && fusedByLearned(adaptation, skill, line))) {
            granted.add(adaptation.getName());
          }
        }
      }
    }
    return Set.copyOf(granted);
  }

  private static boolean fusedByLearned(Adaptation<?> original, Skill<?> skill, PlayerSkillLine line) {
    for (Adaptation<?> upgrade : skill.getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.fuses(original)
          && line.getAdaptationLevel(upgrade.getName()) > 0) {
        return true;
      }
    }
    return false;
  }

  /** A stat line saying {@code original} works at {@code level}. */
  static String grantedLine(Adaptation<?> original, int level) {
    return C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(UpgradeMessages.GRANTED_LEVEL,
        trusted("adaptation", C.stripColor(original.getDisplayName())), trusted("level", level));
  }

  /** The adaptation's English display name without colours, as the catalog's source lists name it; null if unknown. */
  static String englishName(Adaptation<?> adaptation) {
    if (!(adaptation instanceof SimpleAdaptation<?> simple)) {
      return null;
    }
    TextKey key = simple.getDisplayNameKey();
    if (key != null) {
      return key.english();
    }
    String localizationKey = simple.getLocalizationKey();
    if (localizationKey == null || !AdaptMessages.catalog().ids().contains(localizationKey + ".name")) {
      return null;
    }
    return AdaptMessages.require(localizationKey + ".name") instanceof TextKey name ? name.english() : null;
  }

  static String slug(String name) {
    return name.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  /** The existing adaptation package of a skill, such as {@code sword} for {@code swords}; fails for other ids. */
  public static String contentPackage(String skillId) {
    String contentPackage = PACKAGES.get(skillId);
    if (contentPackage == null) {
      throw new IllegalArgumentException("No adaptation package for skill " + skillId);
    }
    return contentPackage;
  }

  /** A display name in CamelCase without apostrophes or gaps: "Duelist's Verdict" becomes DuelistsVerdict. */
  public static String className(String name) {
    StringBuilder builder = new StringBuilder();
    for (String word : name.replace("'", "").split("[^A-Za-z0-9]+")) {
      if (!word.isEmpty()) {
        builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return builder.toString();
  }

  private static Map<String, UpgradeSpec> index() {
    Map<String, UpgradeSpec> index = new LinkedHashMap<>();
    List<String> classes = new ArrayList<>();
    for (SkillBlocks skill : SkillBlockCatalog.all()) {
      String contentPackage = PACKAGES.get(skill.skillId());
      if (contentPackage == null) {
        throw new IllegalStateException("No adaptation package for skill " + skill.skillId());
      }
      for (UpgradedAdaptation upgrade : skill.upgrades()) {
        String slug = slug(upgrade.name());
        String id = ID_PREFIX + skill.skillId() + "-" + slug;
        String className = CONTENT_PACKAGE + contentPackage + ".upgraded." + className(upgrade.name());
        UpgradeSpec spec = new UpgradeSpec(skill.skillId(), upgrade, id,
            "upgrade." + skill.skillId() + "." + slug.replace('-', '_'), className);
        if (index.put(id, spec) != null || classes.contains(className)) {
          throw new IllegalStateException("Two upgrades share the id or class of " + upgrade.name());
        }
        classes.add(className);
      }
    }
    return index;
  }
}
