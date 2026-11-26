package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

/** Leviathan's Tide Shrine: a right-click on a block face underwater sets a shrine there, paid with a nautilus shell. */
public final class TideShrine extends MutationTool<MutationTool.NoData> {
  public static final String ID = "tool-seaborne-tide-shrine";

  private final Leviathan leviathan;

  public TideShrine(Leviathan leviathan) {
    super(ID, leviathan, Id.minecraft("conduit"), 1, NoData.class,
        SeabornMessages.TIDE_SHRINE_NAME, SeabornMessages.TIDE_SHRINE_LORE1);
    this.leviathan = leviathan;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shaped(Id.adapt(ID), List.of("PLP", "LNL", "PLP"), Map.of(
        'P', Ingredient.of(Id.minecraft("prismarine")),
        'L', Ingredient.of(Id.minecraft("sea_lantern")),
        'N', Ingredient.of(Id.minecraft("nautilus_shell"))), create(new NoData()), 1));
  }

  @Override
  protected void use(ToolUse use) {
    if (use.rightClick() && use.block() != null && use.face() != null) {
      leviathan.placeShrine(use.player(), use.block().relative(use.face()), use.level());
    }
  }
}
