package art.arcane.adapt.content.adaptation.seaborrne.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

/** Leviathan's Tide Shrine: a right-click on a block face underwater sets a shrine there, paid with a nautilus shell. */
public final class TideShrine extends MutationTool<CustomItem.Plain> {
  public static final Id ID = Id.adapt("seaborne/tool/tide_shrine");

  private final Leviathan leviathan;

  public TideShrine(Leviathan leviathan) {
    super(ToolItems.plain(ID, Id.minecraft("conduit")).build(), leviathan, 1);
    this.leviathan = leviathan;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(RecipeSpec.shaped(ID, List.of("PLP", "LNL", "PLP"), Map.of(
        'P', Ingredient.of(Id.minecraft("prismarine")),
        'L', Ingredient.of(Id.minecraft("sea_lantern")),
        'N', Ingredient.of(Id.minecraft("nautilus_shell"))), create(CustomItem.Plain.INSTANCE)).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
    if (use.rightClick() && use.block() != null && use.face() != null) {
      leviathan.placeShrine(use.player(), use.block().relative(use.face()), use.level());
    }
  }
}
