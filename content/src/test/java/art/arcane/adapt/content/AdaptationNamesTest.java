package art.arcane.adapt.content;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;

import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.localization.catalog.MutatedMessages;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.MessageKey;
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
      Map.entry("adapt:axe/throwing_axe", "the thrown axe is the object it names"),
      Map.entry("adapt:blocking/mirror_block", "the block is the move; Mirror alone says nothing"),
      Map.entry("adapt:brewing/lingering", "Lingering alone is a bare adjective"),
      Map.entry("adapt:brewing/super_heated", "Super Heated alone is a bare adjective"),
      Map.entry("adapt:crafting/xp", "XP alone is ambiguous"),
      Map.entry("adapt:crafting/leather", "Craftable says what it unlocks, not which skill"),
      Map.entry("adapt:crafting/skulls", "Craftable says what it unlocks, not which skill"),
      Map.entry("adapt:enchanting/quick_enchant", "Quick-Click alone is bare"),
      Map.entry("adapt:excavation/haste", "Hasty alone is a bare adjective"),
      Map.entry("adapt:golem/mending", "Mending alone is the vanilla enchantment"),
      Map.entry("adapt:hunter/big_game", "Big Game Hunter is a set phrase"),
      Map.entry("adapt:nether/netherrack_mason", "Netherrack is the block it works"),
      Map.entry("adapt:rift/gate", "the Rift Gate is the gate it crafts"),
      Map.entry("adapt:rift/visage", "Visage alone says nothing"),
      Map.entry("adapt:rift/conduit", "Conduit alone is the vanilla block"),
      Map.entry("adapt:stealth/vision", "Vision alone is bare"),
      Map.entry("adapt:tame/health", "Tame names the pet the stat belongs to"),
      Map.entry("adapt:tame/damage", "Tame names the pet the stat belongs to"),
      Map.entry("adapt:tame/health_regeneration", "Tame names the pet the stat belongs to"),
      Map.entry("adapt:unarmed/power", "Power alone is bare"));

  @TempDir
  Path data;

  private TestAdapt adapt;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
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
          if (KEEPS_ITS_SKILL.containsKey(adaptation.getId().toString())) {
            kept.add(adaptation.getId().toString());
          } else {
            repeats.add(page.getKey() + ": " + adaptation.getId().toString() + " = " + shown);
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
      clashes.addAll(clashes(SkillHost.lineKey(mutated), mutated.getAdaptations()));
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
        String previous = owners.putIfAbsent(full, adaptation.getId().toString());
        if (previous != null && !previous.equals(adaptation.getId().toString())) {
          clashes.add(full + ": " + previous + ", " + adaptation.getId().toString());
        }
      }
    }
    for (MutatedSkill mutated : mutatedSkills()) {
      for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
        if (adaptation.getSignature() != null) {
          String full = fullName(adaptation).toLowerCase(Locale.ROOT);
          assertThat(owners.get(full)).as("signature %s shares the name of", adaptation.getId().toString()).isNull();
        }
      }
    }
    assertThat(clashes).isEmpty();
  }

  @Test
  void mutationAdaptationsLeaveOutTheAdjectiveTheirSkillAlreadyCarries() {
    for (MutatedSkill mutated : mutatedSkills()) {
      String adjective = MutatedText.adjective(MutatedText.ENGLISH, mutated.parents().partner());
      for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
        if (adaptation.getSignature() != null) {
          assertThat(shortName(adaptation)).as(adaptation.getId().toString()).isEqualTo(fullName(adaptation));
        } else {
          assertThat(fullName(adaptation)).as(adaptation.getId().toString()).contains(adjective);
          assertThat(shortName(adaptation)).as(adaptation.getId().toString()).doesNotContain(adjective)
              .isEqualTo(MutatedMessages.recipe(adaptation.getRecipe()).english());
        }
      }
    }
  }

  @Test
  void everyShortNameBelongsToARegisteredAdaptationAndIsShorterThanItsFullName() {
    Map<String, SimpleAdaptation<?>> byShortNameKey = new HashMap<>();
    for (Skill<?> skill : registry.getSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation instanceof SimpleAdaptation<?> simple && simple.getLocalizationKey() != null) {
          byShortNameKey.put(simple.getLocalizationKey() + ".short_name", simple);
        }
      }
    }
    for (MessageKey declared : AdaptLanguage.builtInCatalog().keys()) {
      if (!declared.id().endsWith(".short_name")) {
        continue;
      }
      TextKey key = (TextKey) declared;
      SimpleAdaptation<?> adaptation = byShortNameKey.get(key.id());
      assertThat(adaptation).as(key.id()).isNotNull();
      assertThat(shortName(adaptation)).as(key.id()).isEqualTo(key.english());
      assertThat(fullName(adaptation).length()).as(key.id()).isGreaterThan(shortName(adaptation).length());
    }
  }

  /** Each registered skill's adaptations with the upgrades its mutations bring, as its own pages list them. */
  private Map<String, List<Adaptation<?>>> pagesBySkill() {
    Map<String, List<Adaptation<?>>> pages = new LinkedHashMap<>();
    for (Skill<?> skill : registry.getSkills()) {
      pages.put(skill.getId().path(), new ArrayList<>(skill.getAdaptations()));
    }
    Set<String> upgrades = new LinkedHashSet<>();
    for (MutatedSkill mutated : mutatedSkills()) {
      for (Adaptation<?> adaptation : mutated.getAdaptations()) {
        if (adaptation instanceof SkillUpgrade<?> upgrade && upgrades.add(upgrade.getId().toString())) {
          pages.get(upgrade.getSkill().getId().path()).add(upgrade);
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
          mutated.add(registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId())));
        }
      }
    }
    return mutated;
  }

  private static List<String> clashes(String page, List<? extends Adaptation<?>> adaptations) {
    Map<String, String> seen = new HashMap<>();
    List<String> clashes = new ArrayList<>();
    for (Adaptation<?> adaptation : adaptations) {
      String previous = seen.putIfAbsent(shortName(adaptation).toLowerCase(Locale.ROOT), adaptation.getId().toString());
      if (previous != null) {
        clashes.add(page + ": " + previous + ", " + adaptation.getId().toString());
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
