package art.arcane.adapt.client.view;


import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.content.BuiltinAddon;
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
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      List<SkillPair> mutations = new ArrayList<>();
      for (Skill<?> lead : registry.getSkills()) {
        assertThat(MutationIcons.tint(lead)).as("tint of " + SkillHost.lineKey(lead)).isNotEqualTo(IconSprites.NO_TINT);
        for (Skill<?> partner : registry.getSkills()) {
          if (lead != partner) {
            mutations.add(new SkillPair(lead.getId(), partner.getId()));
          }
        }
      }
      assertThat(mutations).hasSize(552);
      CatalogView catalog = CatalogViews.build(registry, UUID.randomUUID(), mutations, false);
      for (SkillView skill : catalog.skills()) {
        MutatedSkill mutated = registry.getMutatedSkill(PairKeys.parse(skill.id()));
        String kind = mutated == null ? "skill" : "mutated-skill";
        check(kind + " " + skill.id(), drawn(skill.icon(), mutated == null ? IconSprites.skill(IconSprites.contentId(skill.id())) : null),
            inventory, used, missing);
        Map<String, Adaptation<?>> owners = new HashMap<>();
        for (Adaptation<?> adaptation : (mutated == null ? registry.getSkill(skill.id()) : mutated).getAdaptations()) {
          owners.put(adaptation.getId().toString(), adaptation);
        }
        for (AdaptationView view : skill.adaptations()) {
          Adaptation<?> adaptation = owners.get(view.id());
          boolean own = adaptation instanceof MutationAdaptation;
          check(kind(adaptation) + " " + view.id(),
              drawn(view.icon(), own ? null : IconSprites.adaptation(IconSprites.contentId(view.id()))), inventory, used,
              missing);
        }
        if (mutated != null) {
          for (Adaptation<?> kept : mutated.getKept()) {
            check("kept " + kept.getId().toString(), IconSprites.adaptation(kept.getId()), inventory, used, missing);
          }
        }
      }
      for (SkillPair pair : mutations) {
        for (MutationAdaptation adaptation : registry.getMutatedSkill(pair).getMutationAdaptations()) {
          Signature<?> signature = adaptation.getSignature();
          if (signature != null) {
            check("signature " + signature.getId().toString(), MutationIcons.signature(signature), inventory, used, missing);
          }
        }
      }
      for (MutationTool<?> tool : MutationTools.all(registry)) {
        check("tool " + tool.item().id().toString(), IconSprites.tool(tool.item().id()), inventory, used, missing);
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
    return adaptation != null && adaptation.getId().path().contains("/upgrade/") ? "upgrade" : "adaptation";
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
