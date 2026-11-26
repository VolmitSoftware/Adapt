package art.arcane.adapt.content;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.SkillWords;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.localization.catalog.ShortNameMessages;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Names on a skill's own pages leave out the skill they sit under, and the full names shown everywhere else still say
 * which adaptation they are across the whole catalog.
 */
class AdaptationNamesTest {
  /** The words that name each skill, matched at the start of a word. */
  private static final Map<String, List<String>> SKILL_WORDS = Map.ofEntries(
      Map.entry("agility", List.of("agil")),
      Map.entry("architect", List.of("architect")),
      Map.entry("axes", List.of("axe")),
      Map.entry("blocking", List.of("block")),
      Map.entry("brewing", List.of("brew")),
      Map.entry("chronos", List.of("chrono")),
      Map.entry("crafting", List.of("craft")),
      Map.entry("discovery", List.of("discover")),
      Map.entry("enchanting", List.of("enchant")),
      Map.entry("excavation", List.of("excavat", "shovel")),
      Map.entry("golemancy", List.of("golem")),
      Map.entry("herbalism", List.of("herbalis", "hoe")),
      Map.entry("hunter", List.of("hunter")),
      Map.entry("kinetics", List.of("kinetic")),
      Map.entry("nether", List.of("nether")),
      Map.entry("pickaxe", List.of("pickaxe")),
      Map.entry("ranged", List.of("ranged")),
      Map.entry("rift", List.of("rift")),
      Map.entry("seaborne", List.of("seaborne")),
      Map.entry("stealth", List.of("stealth")),
      Map.entry("swords", List.of("sword")),
      Map.entry("taming", List.of("tame", "taming")),
      Map.entry("tragoul", List.of("tragoul")),
      Map.entry("unarmed", List.of("unarmed")));
  /** Names that keep a word of their skill, and why. */
  private static final Map<String, String> KEEPS_ITS_SKILL = Map.ofEntries(
      Map.entry("axe-throwing-axe", "the thrown axe is the object it names"),
      Map.entry("blocking-mirror-block", "the block is the move; Mirror alone says nothing"),
      Map.entry("brewing-lingering", "Lingering alone is a bare adjective"),
      Map.entry("brewing-super-heated", "Super Heated alone is a bare adjective"),
      Map.entry("crafting-xp", "XP alone is ambiguous"),
      Map.entry("crafting-leather", "Craftable says what it unlocks, not which skill"),
      Map.entry("crafting-skulls", "Craftable says what it unlocks, not which skill"),
      Map.entry("enchanting-quick-enchant", "Quick-Click alone is bare"),
      Map.entry("excavation-haste", "Hasty alone is a bare adjective"),
      Map.entry("golem-mending", "Mending alone is the vanilla enchantment"),
      Map.entry("hunter-big-game", "Big Game Hunter is a set phrase"),
      Map.entry("nether-netherrack-mason", "Netherrack is the block it works"),
      Map.entry("rift-gate", "the Rift Gate is the gate it crafts"),
      Map.entry("rift-visage", "Visage alone says nothing"),
      Map.entry("rift-conduit", "Conduit alone is the vanilla block"),
      Map.entry("stealth-vision", "Vision alone is bare"),
      Map.entry("tame-health", "Tame names the pet the stat belongs to"),
      Map.entry("tame-damage", "Tame names the pet the stat belongs to"),
      Map.entry("tame-health-regeneration", "Tame names the pet the stat belongs to"),
      Map.entry("unarmed-power", "Power alone is bare"));

  @TempDir
  Path data;

  private TestAdapt adapt;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void skillPagesDoNotRepeatTheirSkillOutsideTheAllowList() {
    Map<String, List<Adaptation<?>>> pages = pagesBySkill();
    List<String> repeats = new ArrayList<>();
    Set<String> kept = new LinkedHashSet<>();
    for (Map.Entry<String, List<Adaptation<?>>> page : pages.entrySet()) {
      List<String> words = SKILL_WORDS.get(page.getKey());
      assertThat(words).as("skill words for %s", page.getKey()).isNotNull();
      for (Adaptation<?> adaptation : page.getValue()) {
        String shown = shortName(adaptation);
        if (repeats(shown, words)) {
          if (KEEPS_ITS_SKILL.containsKey(adaptation.getName())) {
            kept.add(adaptation.getName());
          } else {
            repeats.add(page.getKey() + ": " + adaptation.getName() + " = " + shown);
          }
        }
      }
    }
    assertThat(repeats).isEmpty();
    assertThat(kept).as("every allowed name still repeats its skill").containsExactlyInAnyOrderElementsOf(KEEPS_ITS_SKILL.keySet());
  }

  @Test
  void noTwoAdaptationsOfOneSkillPageShareTheirShortName() {
    List<String> clashes = new ArrayList<>();
    for (Map.Entry<String, List<Adaptation<?>>> page : pagesBySkill().entrySet()) {
      clashes.addAll(clashes(page.getKey(), page.getValue()));
    }
    for (MutatedSkill mutated : mutatedSkills()) {
      clashes.addAll(clashes(mutated.getName(), mutated.getAdaptations()));
    }
    assertThat(clashes).isEmpty();
  }

  @Test
  void fullNamesAreUniqueAcrossTheCatalog() {
    Map<String, String> owners = new HashMap<>();
    List<String> clashes = new ArrayList<>();
    for (List<Adaptation<?>> page : pagesBySkill().values()) {
      for (Adaptation<?> adaptation : page) {
        String full = fullName(adaptation).toLowerCase(Locale.ROOT);
        String previous = owners.putIfAbsent(full, adaptation.getName());
        if (previous != null && !previous.equals(adaptation.getName())) {
          clashes.add(full + ": " + previous + ", " + adaptation.getName());
        }
      }
    }
    for (MutatedSkill mutated : mutatedSkills()) {
      for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
        if (adaptation.getSignature() != null) {
          String full = fullName(adaptation).toLowerCase(Locale.ROOT);
          assertThat(owners.get(full)).as("signature %s shares the name of", adaptation.getName()).isNull();
        }
      }
    }
    assertThat(clashes).isEmpty();
  }

  @Test
  void mutationAdaptationsLeaveOutTheAdjectiveTheirSkillAlreadyCarries() {
    for (MutatedSkill mutated : mutatedSkills()) {
      String adjective = SkillWords.of(mutated.getMutation().partner()).adjective();
      for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
        if (adaptation.getSignature() != null) {
          assertThat(shortName(adaptation)).as(adaptation.getName()).isEqualTo(fullName(adaptation));
        } else {
          assertThat(fullName(adaptation)).as(adaptation.getName()).contains(adjective);
          assertThat(shortName(adaptation)).as(adaptation.getName()).doesNotContain(adjective)
              .isEqualTo(adaptation.getRecipe().displayName());
        }
      }
    }
  }

  @Test
  void everyShortNameBelongsToARegisteredAdaptationAndIsShorterThanItsFullName() {
    for (Map.Entry<String, TextKey> entry : ShortNameMessages.all().entrySet()) {
      Adaptation<?> adaptation = registry.getAdaptation(entry.getKey());
      assertThat(adaptation).as(entry.getKey()).isNotNull();
      assertThat(shortName(adaptation)).as(entry.getKey()).isEqualTo(entry.getValue().english());
      assertThat(fullName(adaptation).length()).as(entry.getKey()).isGreaterThan(shortName(adaptation).length());
    }
  }

  /** Each registered skill's adaptations with the upgrades its mutations bring, as its own pages list them. */
  private Map<String, List<Adaptation<?>>> pagesBySkill() {
    Map<String, List<Adaptation<?>>> pages = new LinkedHashMap<>();
    for (Skill<?> skill : registry.getSkills()) {
      pages.put(skill.getName(), new ArrayList<>(skill.getAdaptations()));
    }
    Set<String> upgrades = new LinkedHashSet<>();
    for (MutatedSkill mutated : mutatedSkills()) {
      for (Adaptation<?> adaptation : mutated.getAdaptations()) {
        if (adaptation instanceof SkillUpgrade<?> upgrade && upgrades.add(upgrade.getName())) {
          pages.get(upgrade.getUpgradeSpec().skillId()).add(upgrade);
        }
      }
    }
    assertThat(upgrades).hasSize(72);
    return pages;
  }

  private List<MutatedSkill> mutatedSkills() {
    List<MutatedSkill> mutated = new ArrayList<>();
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead != partner) {
          mutated.add(registry.getMutatedSkill(new SkillMutation(lead.getName(), partner.getName()).id()));
        }
      }
    }
    return mutated;
  }

  private static List<String> clashes(String page, List<? extends Adaptation<?>> adaptations) {
    Map<String, String> seen = new HashMap<>();
    List<String> clashes = new ArrayList<>();
    for (Adaptation<?> adaptation : adaptations) {
      String previous = seen.putIfAbsent(shortName(adaptation).toLowerCase(Locale.ROOT), adaptation.getName());
      if (previous != null) {
        clashes.add(page + ": " + previous + ", " + adaptation.getName());
      }
    }
    return clashes;
  }

  private static boolean repeats(String name, List<String> words) {
    for (String word : words) {
      if (Pattern.compile("\\b" + Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(name).find()) {
        return true;
      }
    }
    return false;
  }

  private static String shortName(Adaptation<?> adaptation) {
    return CatalogViews.plain(adaptation.getShortName());
  }

  private static String fullName(Adaptation<?> adaptation) {
    return CatalogViews.plain(adaptation.getDisplayName());
  }
}
