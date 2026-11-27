package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The XP guide is generated from the sources each skill declares. Every registered skill declares how it earns XP, each
 * line it shows is declared text, and a skill class pays XP only through a declared source, so a new award cannot exist
 * without a guide line that says how to earn it.
 */
class XpGuideSourcesTest {
  private static final Path SKILLS = Path.of("../content/src/main/java/art/arcane/adapt/content/skill");
  private static final Pattern CALL = Pattern.compile("(?<![\\w.])(xp|xpS|xpSilent)\\(");
  /** Spatial pulses around a block pay whoever stands near it, not one player, so they name no source. */
  private static final Pattern SPATIAL = Pattern.compile("^\\s*\\w+\\.location\\(\\),");

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
  void everyRegisteredSkillDeclaresHowItEarnsXp() {
    assertThat(registry.getSkills()).isNotEmpty();
    for (Skill<?> skill : registry.getSkills()) {
      assertThat(skill).isInstanceOf(SimpleSkill.class);
      SimpleSkill<?> simple = (SimpleSkill<?>) skill;
      assertThat(simple.getXpSources()).as(skill.getId().toString()).isNotEmpty();
      assertThat(skill.getXpGuide()).as(skill.getId().toString()).doesNotHaveDuplicates();
      Set<String> keys = new HashSet<>();
      for (XpSource source : simple.getXpSources()) {
        assertThat(keys.add(source.key())).as("%s %s", skill.getId(), source.key()).isTrue();
        assertThat(source.rewardKey()).isEqualTo(skill.getId() + ":" + source.key());
        assertThat(skill.getXpGuide()).as(skill.getId().toString()).contains(source.guide());
      }
    }
  }

  @Test
  void everyGuideLineIsDeclaredText() {
    for (Skill<?> skill : registry.getSkills()) {
      for (Text line : skill.getXpGuide()) {
        assertThat(AdaptLanguage.declared(line.key())).as("%s %s", skill.getId(), line.key()).isNotNull();
      }
    }
  }

  @Test
  void aSourcesGuideLineShowsOnceInDeclarationOrder() {
    Skill<?> kinetics = registry.getSkill(Id.adapt("kinetics"));
    assertThat(kinetics.getXpGuide()).extracting(Text::key).containsExactly("adapt.xp_guide.kinetics.mace", "adapt.xp_guide.kinetics.spear",
        "adapt.xp_guide.kinetics.knockback", "adapt.xp_guide.kinetics.fall", "adapt.xp_guide.kinetics.levitation",
        "adapt.xp_guide.kinetics.anvil");
    Skill<?> excavation = registry.getSkill(Id.adapt("excavation"));
    assertThat(excavation.getXpGuide()).extracting(Text::key).containsExactly("adapt.xp_guide.excavation.combat", "adapt.xp_guide.excavation.dig",
        "adapt.xp_guide.excavation.throw", "adapt.xp_guide.farmed_mobs", "adapt.xp_guide.placed_blocks");
  }

  @Test
  void skillClassesPayXpOnlyThroughDeclaredSources() throws IOException {
    Map<String, List<String>> direct = new TreeMap<>();
    try (Stream<Path> paths = Files.list(SKILLS)) {
      for (Path path : paths.filter((Path p) -> p.getFileName().toString().matches("Skill[A-Z]\\w+\\.java")).toList()) {
        String source = Files.readString(path);
        Matcher call = CALL.matcher(source);
        while (call.find()) {
          String arguments = arguments(source, call.end());
          if (source.substring(Math.max(0, call.start() - 20), call.start()).contains("void ")
              || SPATIAL.matcher(arguments).find()) {
            continue;
          }
          direct.computeIfAbsent(path.getFileName().toString(), (String ignored) -> new ArrayList<>()).add(arguments.trim());
        }
      }
    }
    assertThat(direct).as("xp paid without a declared XpSource").isEmpty();
  }

  private static String arguments(String source, int from) {
    int depth = 1;
    for (int index = from; index < source.length(); index++) {
      char c = source.charAt(index);
      if (c == '(') {
        depth++;
      } else if (c == ')' && --depth == 0) {
        return source.substring(from, index);
      }
    }
    return "";
  }
}
