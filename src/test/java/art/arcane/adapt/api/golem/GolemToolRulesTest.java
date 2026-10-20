package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GolemToolRulesTest {
  private static final GolemToolNote ROD_NOTE = new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_ROD, null);
  private static final GolemToolNote SILK_NOTE = new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_SILK, null);
  private static final GolemToolNote TIER_NOTE = new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_TIER, "Diamond Pickaxe");
  private static final Map<Material, Integer> COSTS = Map.of(Material.STONE_PICKAXE, 1, Material.IRON_PICKAXE, 4, Material.DIAMOND_PICKAXE, 5,
      Material.NETHERITE_PICKAXE, 6);
  private static final GolemToolNeed ROD = new GolemToolNeed("rod", item -> item.getType() == Material.FISHING_ROD, item -> 0, ROD_NOTE);
  private static final GolemToolNeed DIAMOND = new GolemToolNeed("pickaxe:DIAMOND_PICKAXE",
      item -> item.getType() == Material.DIAMOND_PICKAXE || item.getType() == Material.NETHERITE_PICKAXE,
      item -> COSTS.getOrDefault(item.getType(), Integer.MAX_VALUE), TIER_NOTE);
  private static final GolemToolNeed SILK = new GolemToolNeed("pickaxe:silk", item -> item.getType().name().startsWith("GOLDEN"),
      item -> COSTS.getOrDefault(item.getType(), Integer.MAX_VALUE), SILK_NOTE);

  @Test
  void aPlanWithoutNeedsWorksFreeWithoutANote() {
    GolemToolRules.Choice choice = GolemToolRules.choose(GolemToolPlan.NONE, null, need -> true);

    assertThat(choice.kind()).isEqualTo(GolemToolRules.Kind.FREE);
    assertThat(choice.note()).isNull();
  }

  @Test
  void aSuitableHeldToolIsUsedWithoutAFetch() {
    GolemToolRules.Choice choice = GolemToolRules.choose(new GolemToolPlan(List.of(ROD), false), GolemTestItems.stack(Material.FISHING_ROD, 1),
        need -> true);

    assertThat(choice.kind()).isEqualTo(GolemToolRules.Kind.HELD);
    assertThat(choice.need()).isZero();
    assertThat(choice.note()).isNull();
  }

  @Test
  void withoutASuitableHeldToolTheGolemFetchesOneTheHomeChestOffers() {
    GolemToolRules.Choice choice = GolemToolRules.choose(new GolemToolPlan(List.of(ROD), false), GolemTestItems.stack(Material.STICK, 1),
        need -> true);

    assertThat(choice.kind()).isEqualTo(GolemToolRules.Kind.FETCH);
    assertThat(choice.need()).isZero();
  }

  @Test
  void withNoSuitableToolAnywhereARequiredToolIsMissingWithItsNote() {
    GolemToolRules.Choice choice = GolemToolRules.choose(new GolemToolPlan(List.of(ROD), false), null, need -> false);

    assertThat(choice.kind()).isEqualTo(GolemToolRules.Kind.MISSING);
    assertThat(choice.note()).isEqualTo(ROD_NOTE);
  }

  @Test
  void anOptionalToolThatIsMissingLetsTheWorkGoOnFreeWithTheNote() {
    GolemToolRules.Choice choice = GolemToolRules.choose(new GolemToolPlan(List.of(SILK), true), null, need -> false);

    assertThat(choice.kind()).isEqualTo(GolemToolRules.Kind.FREE);
    assertThat(choice.note()).isEqualTo(SILK_NOTE);
  }

  @Test
  void aBetterOptionInTheChestIsFetchedBeforeAWorseHeldOneIsUsed() {
    GolemToolPlan plan = new GolemToolPlan(List.of(SILK, DIAMOND), false);
    ItemStack diamond = GolemTestItems.stack(Material.DIAMOND_PICKAXE, 1);

    assertThat(GolemToolRules.choose(plan, diamond, need -> true).kind()).isEqualTo(GolemToolRules.Kind.FETCH);

    GolemToolRules.Choice fallback = GolemToolRules.choose(plan, diamond, need -> false);
    assertThat(fallback.kind()).isEqualTo(GolemToolRules.Kind.HELD);
    assertThat(fallback.need()).isEqualTo(1);
    assertThat(fallback.note()).isEqualTo(SILK_NOTE);

    GolemToolRules.Choice missing = GolemToolRules.choose(plan, null, need -> false);
    assertThat(missing.kind()).isEqualTo(GolemToolRules.Kind.MISSING);
    assertThat(missing.note()).isEqualTo(TIER_NOTE);
  }

  @Test
  void theCheapestSuitableToolInTheChestIsChosenAndTiesGoToTheFirstSlot() {
    ItemStack[] contents = {
        GolemTestItems.stack(Material.NETHERITE_PICKAXE, 1),
        null,
        GolemTestItems.stack(Material.IRON_PICKAXE, 1),
        GolemTestItems.stack(Material.DIAMOND_PICKAXE, 1),
        GolemTestItems.stack(Material.DIAMOND_PICKAXE, 1)
    };

    assertThat(GolemToolRules.slot(contents, DIAMOND)).isEqualTo(3);
    assertThat(GolemToolRules.slot(new ItemStack[]{GolemTestItems.stack(Material.IRON_PICKAXE, 1)}, DIAMOND)).isEqualTo(-1);
  }

  @Test
  void stacksOfMoreThanOneAreNeverTakenAsATool() {
    ItemStack[] contents = {GolemTestItems.stack(Material.FISHING_ROD, 2), GolemTestItems.stack(Material.FISHING_ROD, 1)};

    assertThat(GolemToolRules.slot(contents, ROD)).isEqualTo(1);
  }

  @Test
  void eachUseWearsOnePointAndUnbreakingSkipsWearByLevelOverLevelPlusOne() {
    assertThat(GolemToolRules.wear(10, 64, 0, 0.99D)).isEqualTo(new GolemToolRules.Wear(11, true, false));

    assertThat(GolemToolRules.wear(10, 64, 3, 0.24D)).isEqualTo(new GolemToolRules.Wear(11, true, false));
    assertThat(GolemToolRules.wear(10, 64, 3, 0.25D)).isEqualTo(new GolemToolRules.Wear(10, false, false));
    assertThat(GolemToolRules.wear(10, 64, 1, 0.49D)).isEqualTo(new GolemToolRules.Wear(11, true, false));
    assertThat(GolemToolRules.wear(10, 64, 1, 0.5D)).isEqualTo(new GolemToolRules.Wear(10, false, false));
  }

  @Test
  void theUseThatReachesTheMaximumDamageBreaksTheTool() {
    assertThat(GolemToolRules.wear(62, 64, 0, 0D)).isEqualTo(new GolemToolRules.Wear(63, true, false));
    assertThat(GolemToolRules.wear(63, 64, 0, 0D)).isEqualTo(new GolemToolRules.Wear(64, true, true));
  }
}
