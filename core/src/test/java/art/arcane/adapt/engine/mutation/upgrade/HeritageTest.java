package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.recipe.AdaptRecipeBook;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
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
      "adapt:chronos/time_bottle", "adapt:chronos/upgrade/time_lord",
      "adapt:hunter/snare_line", "adapt:hunter/upgrade/stalkers_trail");

  private record Mutated(FakePlayer player, AdaptPlayer profile) {
  }

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    List<Skill<?>> skills = registry.getSkills();
    for (int index = 0; index + 1 < skills.size(); index += 2) {
      String lead = skills.get(index).getId().path();
      String partner = skills.get(index + 1).getId().path();
      Mutated keptOnly = mutate(platform, "K" + index, lead, partner);
      Mutated upgraded = mutate(platform, "U" + index, lead, partner);
      for (String skill : List.of(lead, partner)) {
        for (Adaptation<?> upgrade : SkillHost.of(registry.getSkill(skill)).getUpgrades()) {
          upgraded.profile().getData().getSkillLine(skill).setAdaptation(upgrade, upgrade.getMaxLevel());
        }
        KEPT_ONLY.put(SkillHost.lineKey(registry.getSkill(skill)), keptOnly);
        UPGRADED.put(SkillHost.lineKey(registry.getSkill(skill)), upgraded);
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
    for (Skill<?> skill : registry.getSkills()) {
      Mutated keptOnly = KEPT_ONLY.get(skill.getId().toString());
      Mutated upgraded = UPGRADED.get(skill.getId().toString());
      for (Adaptation<?> original : skill.getAdaptations()) {
        int bare = original.getLevel(keptOnly.player());
        int full = original.getLevel(upgraded.player());
        if (Lending.isSource(original)) {
          int granted = granted(skill, original);
          if (bare != 0 || Lending.level(original, (Adaptation<?> held) -> AdaptationHost.of(held).level(upgraded.profile())) != granted) {
            failures.add(original.getId().toString() + " is fused but reads " + bare + " without and "
                + Lending.level(original, (Adaptation<?> held) -> AdaptationHost.of(held).level(upgraded.profile()))
                + " with its upgrade, not " + granted);
          }
        } else if (bare != SkillUpgrades.keptLevel(original) || full != bare || !keptOnly.profile().hasAdaptation(original.getId())) {
          failures.add(original.getId().toString() + " is kept but reads " + bare + " and " + full);
        }
        if (SkillUpgrades.holdable(original, keptOnly.profile().getData())) {
          failures.add(original.getId().toString() + " is learnable after mutating");
        }
        if (!SkillUpgrades.kept(original) && !Lending.isSource(original)) {
          failures.add(original.getId().toString() + " is neither fused nor kept");
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
    for (Skill<?> skill : registry.getSkills()) {
      Mutated keptOnly = KEPT_ONLY.get(skill.getId().toString());
      for (Adaptation<?> kept : SkillUpgrades.kept(skill)) {
        assertThat(adapt.core().getAdaptServer().hasOnlineLearner(keptOnly.player().uuid(), kept.getId().toString()))
            .as("%s indexed as learned", kept.getId().toString()).isTrue();
        assertThat(kept.learnedCandidates(0L)).as("%s candidates", kept.getId().toString()).contains(PlayerApi.profile(keptOnly.profile()));
      }
    }
  }

  @Test
  void theMutatedSkillListsWhatItKeeps() {
    Skill<?> lead = registry.getSkills().get(0);
    Skill<?> partner = registry.getSkills().get(1);
    MutatedSkill mutated = new MutationKit(adapt, KEPT_ONLY.get(lead.getId().toString()).profile())
        .mutated(lead.getId().path(), partner.getId().path());
    List<Adaptation<?>> expected = new ArrayList<>(SkillUpgrades.kept(lead));
    expected.addAll(SkillUpgrades.kept(partner));

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
      String skill = adaptation instanceof Signature<?> signature ? signature.getSlot().pair().lead().toString()
          : SkillHost.lineKey(adaptation.getSkill());
      for (Mutated mutated : List.of(KEPT_ONLY.get(skill), UPGRADED.get(skill))) {
        boolean crafted = !craft(mutated.player(), unlock).isCancelled();
        int level = itemLevel(adaptation, mutated.player());
        if (crafted && level < unlock.requiredLevel()) {
          failures.add(mutated.player().name() + " crafts " + unlock.key() + " but " + adaptation.getId().toString()
              + " runs at " + level);
        }
        if (!crafted && level >= unlock.requiredLevel()) {
          failures.add(mutated.player().name() + " cannot craft " + unlock.key() + " at " + adaptation.getId().toString() + " " + level);
        }
      }
      if (adaptation instanceof Signature<?> signature) {
        if (craft(holder(signature), unlock).isCancelled()) {
          failures.add(unlock.key() + " is dead for a player holding " + signature.getId().toString());
        }
      } else if (craft(UPGRADED.get(skill).player(), unlock).isCancelled()) {
        failures.add(unlock.key() + " is dead for a mutated player holding every upgrade");
      }
    }
    assertThat(failures).isEmpty();
  }

  /** A player who mutated the signature's pair and learned it at its top level. */
  private static FakePlayer holder(Signature<?> signature) {
    MutationSlot slot = signature.getSlot();
    FakePlayer player = platform.player("S" + signature.getId().toString().hashCode(), 0.5D, 61D, 0.5D);
    MutationKit kit = new MutationKit(adapt, adapt.join(player));
    String lead = slot.pair().lead().path();
    String partner = slot.pair().partner().path();
    kit.mutate(lead, partner);
    kit.learn(lead, partner, slot.recipe(), signature.getMaxLevel());
    return player;
  }

  /**
   * The level the item an adaptation's recipe makes works at for the player: the original's own level, or, for an
   * original whose upgrade handles that item itself, the upgrade's level.
   */
  private static int itemLevel(Adaptation<?> adaptation, FakePlayer player) {
    String handler = ITEMS_HANDLED_BY_UPGRADE.get(adaptation.getId().toString());
    int level = adaptation.getActiveLevel(player);
    if (handler == null || level > 0) {
      return level;
    }
    return registry.getAdaptation(Id.parse(handler)).getActiveLevel(player) > 0 ? adaptation.getMaxLevel() : 0;
  }

  private static int granted(Skill<?> skill, Adaptation<?> original) {
    int granted = 0;
    for (Adaptation<?> upgrade : SkillHost.of(skill).getUpgrades()) {
      if (upgrade instanceof SkillUpgrade<?> skillUpgrade && skillUpgrade.isSource(original)) {
        granted = Math.max(granted, skillUpgrade.lend(original, upgrade.getMaxLevel()));
      }
    }
    return granted;
  }

  private static CraftItemEvent craft(FakePlayer player, AdaptRecipeBook.Unlock unlock) {
    FakeItem swiftness = FakeItem.of("potion");
    swiftness.setPotionContents(PotionContents.of(Id.minecraft("swiftness")));
    return adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("stone"), FakeItem.empty(), unlock.key(),
        CraftItemEvent.RecipeKind.SHAPED, List.of(swiftness)));
  }
}
