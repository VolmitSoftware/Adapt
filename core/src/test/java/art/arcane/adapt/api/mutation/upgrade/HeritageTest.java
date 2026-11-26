package art.arcane.adapt.api.mutation.upgrade;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.recipe.AdaptRecipeBook;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every original of every skill, for a player who mutated it: an upgrade fuses it and it runs at the level the upgrade
 * lends, or the mutation keeps it at its kept level, free and without a power slot. Every registered recipe stays
 * craftable and never yields an item its crafter cannot use.
 */
class HeritageTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static final Map<String, Mutated> KEPT_ONLY = new HashMap<>();
  private static final Map<String, Mutated> UPGRADED = new HashMap<>();
  private static final Map<String, String> ITEMS_HANDLED_BY_UPGRADE = Map.of(
      "chronos-time-bottle", "upgraded-chronos-time-lord",
      "hunter-snare-line", "upgraded-hunter-stalkers-trail");

  private record Mutated(FakePlayer player, AdaptPlayer profile) {
  }

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, CoreContent.skillTypes());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    List<SkillBlocks> skills = SkillBlockCatalog.all();
    for (int index = 0; index + 1 < skills.size(); index += 2) {
      String lead = skills.get(index).skillId();
      String partner = skills.get(index + 1).skillId();
      Mutated keptOnly = mutate(platform, "K" + index, lead, partner);
      Mutated upgraded = mutate(platform, "U" + index, lead, partner);
      for (String skill : List.of(lead, partner)) {
        for (Adaptation<?> upgrade : registry.getSkill(skill).getUpgrades()) {
          upgraded.profile().getData().getSkillLine(skill).setAdaptation(upgrade, upgrade.getMaxLevel());
        }
        KEPT_ONLY.put(skill, keptOnly);
        UPGRADED.put(skill, upgraded);
      }
    }
    platform.scheduler().advance(200);
  }

  private static Mutated mutate(TestPlatform platform, String name, String lead, String partner) {
    FakePlayer player = platform.player(name, 0.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(player);
    new MutationKit(adapt, profile).mutate(lead, partner);
    return new Mutated(player, profile);
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void everyOriginalIsFusedByAnUpgradeOrKept() {
    List<String> failures = new ArrayList<>();
    for (SkillBlocks catalog : SkillBlockCatalog.all()) {
      Skill<?> skill = registry.getSkill(catalog.skillId());
      Mutated keptOnly = KEPT_ONLY.get(catalog.skillId());
      Mutated upgraded = UPGRADED.get(catalog.skillId());
      for (Adaptation<?> original : skill.getAdaptations()) {
        int bare = original.getLevel(keptOnly.player());
        int full = original.getLevel(upgraded.player());
        if (UpgradeLoans.fused(original)) {
          int granted = granted(skill, original);
          if (bare != 0 || UpgradeLoans.level(original, (Adaptation<?> held) -> held.getLevel(upgraded.profile())) != granted) {
            failures.add(original.getName() + " is fused but reads " + bare + " without and "
                + UpgradeLoans.level(original, (Adaptation<?> held) -> held.getLevel(upgraded.profile()))
                + " with its upgrade, not " + granted);
          }
        } else if (bare != SkillUpgrades.keptLevel(original) || full != bare || !keptOnly.profile().hasAdaptation(original.getName())) {
          failures.add(original.getName() + " is kept but reads " + bare + " and " + full);
        }
        if (SkillUpgrades.holdable(original, keptOnly.profile().getData())) {
          failures.add(original.getName() + " is learnable after mutating");
        }
        if (!SkillUpgrades.kept(original) && !UpgradeLoans.fused(original)) {
          failures.add(original.getName() + " is neither fused nor kept");
        }
      }
    }
    assertThat(failures).isEmpty();
  }

  @Test
  void keptOriginalsTakeNoPowerAndCountAsLearned() {
    for (Mutated mutated : KEPT_ONLY.values()) {
      assertThat(mutated.profile().getData().getUsedPower()).isZero();
    }
    for (Mutated mutated : UPGRADED.values()) {
      assertThat(mutated.profile().getData().getUsedPower()).isEqualTo(6);
    }
    for (SkillBlocks catalog : SkillBlockCatalog.all()) {
      Mutated keptOnly = KEPT_ONLY.get(catalog.skillId());
      for (Adaptation<?> kept : SkillUpgrades.kept(registry.getSkill(catalog.skillId()))) {
        assertThat(adapt.core().getAdaptServer().hasOnlineLearner(keptOnly.player().uuid(), kept.getName()))
            .as("%s indexed as learned", kept.getName()).isTrue();
        assertThat(kept.learnedCandidates(0L)).as("%s candidates", kept.getName()).contains(keptOnly.profile());
      }
    }
  }

  @Test
  void theMutatedSkillListsWhatItKeeps() {
    SkillBlocks lead = SkillBlockCatalog.all().get(0);
    SkillBlocks partner = SkillBlockCatalog.all().get(1);
    MutatedSkill mutated = new MutationKit(adapt, KEPT_ONLY.get(lead.skillId()).profile()).mutated(lead.skillId(), partner.skillId());
    List<Adaptation<?>> expected = new ArrayList<>(SkillUpgrades.kept(registry.getSkill(lead.skillId())));
    expected.addAll(SkillUpgrades.kept(registry.getSkill(partner.skillId())));

    assertThat(mutated.getKept()).containsExactlyElementsOf(expected);
    assertThat(mutated.getAdaptations()).doesNotContainAnyElementsOf(expected);
    if (!expected.isEmpty()) {
      assertThat(mutated.getDescriptionWithKept()).startsWith(mutated.getDescription())
          .contains(MutatedSkill.keptLine(expected.getFirst()));
    }
  }

  @Test
  void everyRecipeStaysCraftableAndItsItemWorksForItsCrafter() {
    List<String> failures = new ArrayList<>();
    for (AdaptRecipeBook.Unlock unlock : registry.getRegisteredRecipeUnlocks()) {
      Adaptation<?> adaptation = unlock.adaptation();
      String skill = adaptation.getSkill().getName();
      for (Mutated mutated : List.of(KEPT_ONLY.get(skill), UPGRADED.get(skill))) {
        boolean crafted = !craft(mutated.player(), unlock).isCancelled();
        int level = itemLevel(adaptation, mutated.player());
        if (crafted && level < unlock.requiredLevel()) {
          failures.add(mutated.player().name() + " crafts " + unlock.key() + " but " + adaptation.getName()
              + " runs at " + level);
        }
        if (!crafted && level >= unlock.requiredLevel()) {
          failures.add(mutated.player().name() + " cannot craft " + unlock.key() + " at " + adaptation.getName() + " " + level);
        }
      }
      if (adaptation instanceof Signature<?> signature) {
        if (craft(holder(signature), unlock).isCancelled()) {
          failures.add(unlock.key() + " is dead for a player holding " + signature.getName());
        }
      } else if (craft(UPGRADED.get(skill).player(), unlock).isCancelled()) {
        failures.add(unlock.key() + " is dead for a mutated player holding every upgrade");
      }
    }
    assertThat(failures).isEmpty();
  }

  /** A player who mutated the signature's pair and learned it at its top level. */
  private static FakePlayer holder(Signature<?> signature) {
    MutationOverride row = signature.getRow();
    FakePlayer player = platform.player("S" + signature.getName().hashCode(), 0.5D, 61D, 0.5D);
    MutationKit kit = new MutationKit(adapt, adapt.join(player));
    kit.mutate(row.leadId(), row.partnerId());
    kit.learn(row.leadId(), row.partnerId(), row.recipe(), signature.getMaxLevel());
    return player;
  }

  /**
   * The level the item an adaptation's recipe makes works at for the player: the original's own level, or, for an
   * original whose upgrade handles that item itself, the upgrade's level.
   */
  private static int itemLevel(Adaptation<?> adaptation, FakePlayer player) {
    String handler = ITEMS_HANDLED_BY_UPGRADE.get(adaptation.getName());
    int level = adaptation.getActiveLevel(player);
    if (handler == null || level > 0) {
      return level;
    }
    return registry.getAdaptation(handler).getActiveLevel(player) > 0 ? adaptation.getMaxLevel() : 0;
  }

  private static int granted(Skill<?> skill, Adaptation<?> original) {
    int granted = 0;
    for (Adaptation<?> upgrade : skill.getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.fuses(original)) {
        granted = Math.max(granted, skillUpgrade.lend(original, upgrade.getMaxLevel()));
      }
    }
    return granted;
  }

  private static CraftItemEvent craft(FakePlayer player, AdaptRecipeBook.Unlock unlock) {
    FakeItem swiftness = FakeItem.of("potion");
    swiftness.setPotionContents(new PotionContents(Id.minecraft("swiftness"), null, List.of(), null));
    return adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("stone"), FakeItem.empty(), unlock.key(),
        CraftItemEvent.RecipeKind.SHAPED, List.of(swiftness)));
  }
}
