package art.arcane.adapt.client.view;

import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.advancement.AdvancementManager;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Walks every view the server sends a client, for a fresh player, a played one and one holding several mutations (one
 * with tools, one with a signature), and checks that every icon standing for a skill, mutated skill, adaptation,
 * upgrade, mutated adaptation, signature, tool or resource names art that ships in the client resources. Views that
 * carry only ids (player view, kept and lent lists, spending rows, XP guides, spread partners) are checked through the
 * catalog entry the client draws them with. The inventory per view goes to stdout and
 * {@code build/reports/icon-art/views.txt}.
 *
 * <p>Drawn as items on purpose, so never checked here: preference choices and toggle groups (their art is optional per
 * owner), advancement milestones (each names its own item), the master level toast (no skill stands for it), the
 * "mutation available" and welcome toasts (notices, not skills), and the spread's "+N" overflow medallion (a count).
 */
class ViewSpritesTest {
  private static final Path REPORT = Path.of("build/reports/icon-art/views.txt");
  private static final Path GUI = Path.of("../adapters/client-common/src/main/resources/assets/adapt/textures/gui");
  private static final String PREFIX = IconSprites.NAMESPACE + ':' + IconSprites.ROOT;
  private static final String TOOLS_PAIR_LEAD = "herbalism";
  private static final String TOOLS_PAIR_PARTNER = "crafting";

  @TempDir
  Path dataFolder;

  private final Map<String, Map<String, Integer>> inventory = new LinkedHashMap<>();
  private final List<String> listing = new ArrayList<>();
  private final List<String> missing = new ArrayList<>();
  private final List<String> duplicates = new ArrayList<>();

  @Test
  void everyViewIconHasItsOwnArt() throws IOException {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      SkillPair signaturePair = signaturePair(registry);
      AdaptPlayer fresh = adapt.join(platform.player("Fresh", 0.5D, 61D, 0.5D));
      AdaptPlayer played = adapt.join(platform.player("Played", 4.5D, 61D, 0.5D));
      FakePlayer mutatedRef = platform.player("Mutated", 8.5D, 61D, 0.5D);
      AdaptPlayer mutated = adapt.join(mutatedRef);
      play(registry, played);
      play(registry, mutated);
      mutate(registry, mutated, TOOLS_PAIR_LEAD, TOOLS_PAIR_PARTNER);
      mutate(registry, mutated, signaturePair.lead().toString(), signaturePair.partner().toString());
      learnMutated(registry, mutated);

      for (AdaptPlayer player : List.of(fresh, played, mutated)) {
        walk(registry, player, signaturePair);
      }
      toasts(registry);
      chestItems(registry, played, signaturePair);
    }
    List<String> lines = new ArrayList<>();
    inventory.forEach((String view, Map<String, Integer> kinds) -> lines.add(view + ": " + kinds));
    lines.add("");
    lines.addAll(listing);
    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, lines, StandardCharsets.UTF_8);
    inventory.forEach((String view, Map<String, Integer> kinds) -> System.out.println("icons " + view + ": " + kinds));
    assertThat(missing).as("icons drawn as items or without art (inventory in %s)", REPORT).isEmpty();
    assertThat(duplicates).as("items repeated within one mutate menu").isEmpty();
  }

  private void walk(SkillRegistry registry, AdaptPlayer player, SkillPair signaturePair) {
    UUID viewer = player.getPlayer().uuid();
    String who = player.getPlayer().name();
    PlayerData data = player.getData();
    List<SkillPair> mutations = data.getMutations();
    CatalogView catalog = CatalogViews.build(registry, viewer, mutations, true);
    Map<String, SkillView> skills = new HashMap<>();
    Map<String, AdaptationView> adaptations = new HashMap<>();
    for (SkillView skill : catalog.skills()) {
      skills.put(skill.id(), skill);
      check(who + " catalog", kind(registry, skill.id()), skill.id(), skill.icon());
      for (AdaptationView adaptation : skill.adaptations()) {
        adaptations.putIfAbsent(adaptation.id(), adaptation);
        check(who + " catalog", kind(registry.getAdaptation(Id.parse(adaptation.id()))), adaptation.id(), adaptation.icon());
      }
    }

    PlayerView view = PlayerViews.build(registry, player);
    for (SkillLineView line : view.skills()) {
      viaCatalog(who + " player", "skill", line.skillId(), skills.get(line.skillId()) == null ? null : skills.get(line.skillId()).icon());
    }
    for (String adaptation : view.adaptationLevels().keySet()) {
      viaCatalog(who + " player", "learned", adaptation, iconOf(adaptations, adaptation));
    }

    for (SpendView spend : SpendViews.build(registry, player)) {
      viaCatalog(who + " spending", "skill", spend.skillId(), skills.get(spend.skillId()) == null ? null : skills.get(spend.skillId()).icon());
      viaCatalog(who + " spending", spend.source().name().toLowerCase(), spend.adaptationId(), iconOf(adaptations, spend.adaptationId()));
    }

    for (XpGuideView guide : XpGuideViews.build(registry, viewer, mutations)) {
      viaCatalog(who + " xp-guide", "skill", guide.skillId(), skills.get(guide.skillId()) == null ? null : skills.get(guide.skillId()).icon());
      for (XpGuideView.Section section : guide.sections()) {
        if (!section.skillId().isEmpty()) {
          viaCatalog(who + " xp-guide", "parent", section.skillId(), skills.get(section.skillId()).icon());
        }
      }
    }

    for (ToolView tool : ToolViews.build(registry, viewer)) {
      check(who + " tools", "tool", tool.toolId(), tool.icon());
    }
    for (RecipeView recipe : RecipeViews.build(registry, viewer)) {
      String sprite = recipe.result().icon().spriteId();
      if (sprite != null && sprite.startsWith(PREFIX)) {
        check(who + " recipes", "tool", recipe.adaptationId(), recipe.result().icon());
      }
    }

    for (SkillPair mutation : mutations) {
      MutatedSkill skill = registry.getMutatedSkill(mutation);
      for (Adaptation<?> kept : skill.getKept()) {
        viaCatalog(who + " kept", "kept", kept.getId().toString(), iconOf(adaptations, kept.getId().toString()));
      }
      for (Lending.Running running : Lending.running(player.getPlayer(), player, registry, mutation)) {
        viaCatalog(who + " lent", "lent", running.original().getId().toString(), iconOf(adaptations, running.original().getId().toString()));
      }
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        check(who + " hud", kind(adaptation), adaptation.getId().toString(), ViewIcons.adaptation(adaptation));
        check(who + " toast", "unlock " + kind(adaptation), adaptation.getId().toString(),
            AdvancementManager.unlockToast(viewer, adaptation).icon());
      }
      check(who + " toast", "level-up mutated-skill", SkillHost.lineKey(skill), ViewIcons.skill(skill));
    }
    check(who + " toast", "knowledge", "knowledge", ViewIcons.knowledge());

    List<List<String>> pairs = List.of(List.of(TOOLS_PAIR_LEAD, TOOLS_PAIR_PARTNER),
        List.of(signaturePair.lead().toString(), signaturePair.partner().toString()), List.of("swords", "stealth"), List.of("ranged", "rift"));
    for (List<String> pair : pairs) {
      Skill<?> lead = registry.getSkill(pair.get(0));
      spread(registry, who, skills, MutationSpreadViews.build(registry, data, lead, null, AdaptConfig.get().getMutations(), ""));
      spread(registry, who, skills, MutationSpreadViews.build(registry, data, lead, registry.getSkill(pair.get(1)),
          AdaptConfig.get().getMutations(), ""));
    }
  }

  private void spread(SkillRegistry registry, String who, Map<String, SkillView> skills, MutationSpreadView spread) {
    String view = who + " spread " + spread.lead() + (spread.partner().isEmpty() ? "" : "+" + spread.partner());
    for (MutationEntry entry : spread.mutated()) {
      check(view, "result " + kind(registry.getAdaptation(Id.parse(entry.id()))), entry.id(), entry.icon());
    }
    for (MutationSide side : List.of(spread.leadSide(), spread.partnerSide())) {
      for (MutationEntry entry : side.upgrades()) {
        check(view, "upgrade", entry.id(), entry.icon());
      }
      for (MutationEntry entry : side.kept()) {
        check(view, "kept", entry.id(), entry.icon());
      }
      for (MutationEntry entry : side.tools()) {
        check(view, "tool", entry.id(), entry.icon());
      }
      for (MutationEntry entry : side.lost()) {
        check(view, "lost", entry.id(), entry.icon());
      }
    }
    for (MutationCandidate candidate : spread.candidates()) {
      viaCatalog(view, "partner", candidate.skillId(), skills.get(candidate.skillId()).icon());
    }
  }

  /** The unlock and discovery toasts of every registered skill and original, as the advancement catalog sends them. */
  private void toasts(SkillRegistry registry) {
    for (Skill<?> skill : registry.getSkills()) {
      toasts(ComponentHost.of(skill).buildAdvancements());
    }
  }

  private void toasts(AdaptAdvancement advancement) {
    String key = advancement.getKey();
    if (key.startsWith("skill_") || key.startsWith("adaptation_")) {
      IconRef icon = advancement.getClientIcon();
      check("advancement toasts", key.substring(0, key.indexOf('_')), key, icon == null ? IconRef.item("none") : icon);
    } else {
      count("advancement toasts", "milestone (item)");
    }
    if (advancement.getChildren() != null) {
      for (AdaptAdvancement child : advancement.getChildren()) {
        toasts(child);
      }
    }
  }

  /**
   * Players without the client mod see the mutate menu's upgrades and the mutation's own adaptations as items: within
   * one pair those items must all differ, or two tiles would look the same.
   */
  private void chestItems(SkillRegistry registry, AdaptPlayer player, SkillPair signaturePair) {
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner || !Kits.mergeable(lead) || !Kits.mergeable(partner)) {
          continue;
        }
        MutationOffers.Preview preview = MutationOffers.preview(player.getData(), lead, partner);
        Map<String, String> seen = new HashMap<>();
        List<Adaptation<?>> shown = new ArrayList<>(preview.upgrades());
        shown.addAll(preview.mutated());
        for (Adaptation<?> adaptation : shown) {
          String item = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(), ComponentHost.of(adaptation).getModel()).material().toString();
          String before = seen.putIfAbsent(item, adaptation.getId().toString());
          if (before != null) {
            duplicates.add(SkillHost.lineKey(lead) + "+" + SkillHost.lineKey(partner) + ": " + item + " for " + before + " and " + adaptation.getId().toString());
          }
        }
        count("chest mutate menu", "pairs");
      }
    }
    MutationOffers.Preview signed = MutationOffers.preview(player.getData(), registry.getSkill(signaturePair.lead()),
        registry.getSkill(signaturePair.partner()));
    assertThat(signed.mutated()).anyMatch((Adaptation<?> adaptation) -> adaptation instanceof MutationAdaptation own
        && own.getSignature() != null && adaptation.getIcon().equals(own.getSignature().getIcon()));
    listing.addAll(duplicates);
  }

  private void viaCatalog(String view, String kind, String id, IconRef icon) {
    if (icon == null) {
      missing.add(view + " " + kind + " " + id + " -> not in the catalog");
      return;
    }
    check(view, kind, id, icon);
  }

  private void check(String view, String kind, String id, IconRef icon) {
    count(view, kind);
    String sprite = icon.spriteId();
    if (sprite == null) {
      missing.add(view + " " + kind + " " + id + " -> item " + icon.itemId());
      return;
    }
    List<String> files = new ArrayList<>();
    for (IconSprites.Layer layer : IconSprites.layers(sprite)) {
      String file = layer.spriteId().substring(PREFIX.length());
      files.add(file);
      if (!Files.isRegularFile(GUI.resolve(file))) {
        missing.add(view + " " + kind + " " + id + " -> " + file);
      }
    }
    listing.add(view + " | " + kind + " " + id + " -> " + String.join(" + ", files));
  }

  private void count(String view, String kind) {
    inventory.computeIfAbsent(view, (String ignored) -> new TreeMap<>()).merge(kind, 1, Integer::sum);
  }

  private static IconRef iconOf(Map<String, AdaptationView> adaptations, String id) {
    AdaptationView view = adaptations.get(id);
    return view == null ? null : view.icon();
  }

  private static String kind(SkillRegistry registry, String skillId) {
    return registry.getMutatedSkill(PairKeys.parse(skillId)) != null ? "mutated-skill" : "skill";
  }

  private static String kind(Adaptation<?> adaptation) {
    if (adaptation instanceof MutationAdaptation own) {
      return own.getSignature() != null ? "signature" : "mutated";
    }
    return adaptation != null && adaptation.getId().path().contains("/upgrade/") ? "upgrade" : "adaptation";
  }

  /** The first pair, in registry order, whose mutated skill has a signature in place of a recipe. */
  private static SkillPair signaturePair(SkillRegistry registry) {
    Set<String> taken = new TreeSet<>(List.of(TOOLS_PAIR_LEAD, TOOLS_PAIR_PARTNER));
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner || taken.contains(SkillHost.lineKey(lead)) || taken.contains(SkillHost.lineKey(partner))
            || !Kits.mergeable(lead) || !Kits.mergeable(partner)) {
          continue;
        }
        MutatedSkill mutated = registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId()));
        if (mutated != null && mutated.getMutationAdaptations().stream().anyMatch((MutationAdaptation own) -> own.getSignature() != null)) {
          return mutated.getPair();
        }
      }
    }
    throw new AssertionError("no pair has a signature");
  }

  /** Every skill at level 30 with knowledge, and each skill's first original learned. */
  private static void play(SkillRegistry registry, AdaptPlayer player) {
    PlayerData data = player.getData();
    data.setMasterXp(1_000_000D);
    for (Skill<?> skill : registry.getSkills()) {
      PlayerSkillLine line = data.getSkillLine(SkillHost.lineKey(skill));
      line.setXp(XP.getXpForLevel(30) + 1D);
      line.setKnowledge(200);
      line.settleWithoutRewards();
      if (!skill.getAdaptations().isEmpty()) {
        line.setAdaptation(skill.getAdaptations().getFirst(), 1);
      }
    }
  }

  private static void mutate(SkillRegistry registry, AdaptPlayer player, String lead, String partner) {
    SkillMutations.Result result = SkillMutations.mutate(player.getData(), registry.getSkill(lead), registry.getSkill(partner),
        player.getPlayer().name());
    assertThat(result).as(lead + " + " + partner).isEqualTo(SkillMutations.Result.MUTATED);
  }

  /** Learns every adaptation of the player's mutated skills at level 1: upgrades, recipes and signatures. */
  private static void learnMutated(SkillRegistry registry, AdaptPlayer player) {
    for (SkillPair mutation : player.getData().getMutations()) {
      MutatedSkill skill = registry.getMutatedSkill(mutation);
      PlayerSkillLine line = player.getData().getSkillLine(mutation.lead().toString());
      line.setKnowledge(5_000);
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        line.setAdaptation(adaptation, 1);
      }
    }
  }
}
