package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.content.item.ToolItems;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

/**
 * A crafted arrow that fires from any bow as a plain arrow and gives its upgrade's effect only to a holder, and only
 * when the shot spent it (Infinity and creative shots spend nothing).
 */
public abstract class MunitionArrow extends MutationTool<CustomItem.Plain> {
  private static final int MAX_STACK = 64;
  private static final int ARROWS_PER_CRAFT = 4;

  private final Id catalyst;

  protected MunitionArrow(Id id, Adaptation<?> owner, Id catalyst) {
    super(ToolItems.plain(id, RangedProjectiles.ARROW).maxStackSize(MAX_STACK).build(), owner, 1);
    this.catalyst = catalyst;
  }

  @Override
  public List<RecipeSpec> recipes() {
    List<Ingredient> inputs = new ArrayList<>(ARROWS_PER_CRAFT + 1);
    for (int arrow = 0; arrow < ARROWS_PER_CRAFT; arrow++) {
      inputs.add(Ingredient.of(RangedProjectiles.ARROW));
    }
    inputs.add(Ingredient.of(catalyst));
    ItemRef result = create(CustomItem.Plain.INSTANCE);
    result.setAmount(ARROWS_PER_CRAFT);
    return List.of(RecipeSpec.shapeless(item().id(), inputs, result).withRequiredLevel(1));
  }

  @Override
  public void use(ToolUse use) {
  }


  /** The holder's level when {@code shot} spent one of these arrows from a player, else 0. */
  public int spentBy(EntityShootBowEvent shot) {
    return shot.shooter() instanceof PlayerRef player && shot.consumeItem() && is(shot.consumable())
        ? level(player) : 0;
  }
}
