package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;

/**
 * A crafted arrow that fires from any bow as a plain arrow and gives its upgrade's effect only to a holder, and only
 * when the shot spent it (Infinity and creative shots spend nothing).
 */
public abstract class MunitionArrow extends MutationTool<MutationTool.NoData> {
  private static final int ARROWS_PER_CRAFT = 4;

  private final Id catalyst;

  protected MunitionArrow(String id, Adaptation<?> owner, Id catalyst, TextKey name) {
    super(id, owner, RangedProjectiles.ARROW, 1, NoData.class, name);
    this.catalyst = catalyst;
  }

  @Override
  public List<RecipeSpec> recipes() {
    List<Ingredient> inputs = new ArrayList<>(ARROWS_PER_CRAFT + 1);
    for (int arrow = 0; arrow < ARROWS_PER_CRAFT; arrow++) {
      inputs.add(Ingredient.of(RangedProjectiles.ARROW));
    }
    inputs.add(Ingredient.of(catalyst));
    ItemRef result = create(new NoData());
    result.setAmount(ARROWS_PER_CRAFT);
    return List.of(new RecipeSpec.Shapeless(Id.adapt(id()), inputs, result, 1));
  }

  @Override
  protected void use(ToolUse use) {
  }

  @Override
  protected int maxStackSize() {
    return 64;
  }

  /** The holder's level when {@code shot} spent one of these arrows from a player, else 0. */
  public int spentBy(EntityShootBowEvent shot) {
    return shot.shooter() instanceof PlayerRef player && shot.consumeItem() && is(shot.consumable())
        ? level(player) : 0;
  }
}
