package art.arcane.adapt.client.view;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.SkillWords;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every skill, adaptation, upgrade, mutated skill, mutated adaptation, signature, kept original and mutation tool the
 * catalog can show has its own art in the client resources, and every file in the mutation art folders is used. The
 * icon each one resolves to is listed in {@code build/reports/icon-art/inventory.txt}.
 */
class CatalogSpritesTest {
  private static final Path REPORT = Path.of("build/reports/icon-art/inventory.txt");
  private static final Path GUI = Path.of("../adapters/client-common/src/main/resources/assets/adapt/textures/gui");
  private static final String PREFIX = IconSprites.NAMESPACE + ':' + IconSprites.ROOT;
  private static final List<String> MUTATION_FOLDERS = List.of("emblem", "trim", "glyph", "signature", "tool");

  @TempDir
  Path dataFolder;

  @Test
  void everyCatalogIconHasItsOwnArt() throws IOException {
    Map<String, String> inventory = new TreeMap<>();
    Set<String> used = new TreeSet<>();
    List<String> missing = new ArrayList<>();
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, CoreContent.skillTypes())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      List<SkillMutation> mutations = new ArrayList<>();
      for (Skill<?> lead : registry.getSkills()) {
        assertThat(MutationIcons.tint(lead.getName())).as("tint of " + lead.getName()).isNotEqualTo(IconSprites.NO_TINT);
        for (Skill<?> partner : registry.getSkills()) {
          if (lead != partner && SkillWords.of(lead.getName()) != null && SkillWords.of(partner.getName()) != null) {
            mutations.add(new SkillMutation(lead.getName(), partner.getName()));
          }
        }
      }
      assertThat(mutations).hasSize(552);
      CatalogView catalog = CatalogViews.build(registry, UUID.randomUUID(), mutations, false);
      for (SkillView skill : catalog.skills()) {
        MutatedSkill mutated = registry.getMutatedSkill(skill.id());
        String kind = mutated == null ? "skill" : "mutated-skill";
        check(kind + " " + skill.id(), drawn(skill.icon(), mutated == null ? IconSprites.skill(skill.id()) : null), inventory, used, missing);
        Map<String, Adaptation<?>> owners = new HashMap<>();
        for (Adaptation<?> adaptation : (mutated == null ? registry.getSkill(skill.id()) : mutated).getAdaptations()) {
          owners.put(adaptation.getName(), adaptation);
        }
        for (AdaptationView view : skill.adaptations()) {
          Adaptation<?> adaptation = owners.get(view.id());
          boolean own = adaptation instanceof MutationAdaptation;
          check(kind(adaptation) + " " + view.id(), drawn(view.icon(), own ? null : IconSprites.adaptation(view.id())), inventory, used,
              missing);
        }
        if (mutated != null) {
          for (Adaptation<?> kept : mutated.getKept()) {
            check("kept " + kept.getName(), IconSprites.adaptation(kept.getName()), inventory, used, missing);
          }
        }
      }
      for (Skill<?> skill : registry.getSkills()) {
        for (Signature<?> signature : skill.getSignatures()) {
          check("signature " + signature.getName(), MutationIcons.signature(signature), inventory, used, missing);
        }
      }
      for (MutationTool<?> tool : MutationTools.all(registry)) {
        check("tool " + tool.id(), IconSprites.texture("tool", tool.id()), inventory, used, missing);
      }
    }
    Files.createDirectories(REPORT.getParent());
    List<String> lines = new ArrayList<>();
    inventory.forEach((String owner, String icon) -> lines.add(owner + " -> " + icon));
    Files.write(REPORT, lines, StandardCharsets.UTF_8);
    assertThat(missing).as("icons without art (inventory in %s)", REPORT).isEmpty();
    Set<String> unused = new TreeSet<>();
    for (String folder : MUTATION_FOLDERS) {
      try (Stream<Path> files = Files.list(GUI.resolve(folder))) {
        files.map((Path file) -> folder + '/' + file.getFileName()).filter((String file) -> !used.contains(file)).forEach(unused::add);
      }
    }
    assertThat(unused).as("mutation art nothing draws").isEmpty();
  }

  private static String kind(Adaptation<?> adaptation) {
    if (adaptation instanceof MutationAdaptation mutated) {
      return mutated.getSignature() != null ? "signature-row" : "recipe";
    }
    return adaptation != null && adaptation.getName().startsWith("upgraded-") ? "upgrade" : "adaptation";
  }

  /** The sprite the client draws for {@code icon}: the one the server named, else {@code convention}, or null for none. */
  private static String drawn(IconRef icon, String convention) {
    return icon.spriteId() != null ? icon.spriteId() : convention;
  }

  /** Records the files {@code sprite} stacks; every one must exist, and no sprite at all counts as missing. */
  private static void check(String owner, String sprite, Map<String, String> inventory, Set<String> used, List<String> missing) {
    if (sprite == null) {
      inventory.put(owner, "item");
      missing.add(owner + " -> item");
      return;
    }
    List<String> files = new ArrayList<>();
    for (IconSprites.Layer layer : IconSprites.layers(sprite)) {
      String file = layer.spriteId().substring(PREFIX.length());
      files.add(file);
      used.add(file);
      if (!Files.isRegularFile(GUI.resolve(file))) {
        missing.add(owner + " -> " + file);
      }
    }
    inventory.put(owner, String.join(" + ", files));
  }
}
