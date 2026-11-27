package art.arcane.adapt.api.item;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A brewing stand recipe an adaptation adds. Only a stand whose owner has learned the adaptation brews it.
 *
 * @param id           the recipe's own id
 * @param ingredient   the item in the top slot
 * @param basePotion   what the bottles must be
 * @param result       what each bottle becomes
 * @param brewingTicks how long it brews; vanilla is 400
 * @param fuelCost     blaze fuel used; vanilla is 1 of 20 per powder
 */
@ApiStatus.AvailableSince("1.0.0")
public record BrewingRecipe(Id id, Id ingredient, ItemRef basePotion, ItemRef result, int brewingTicks, int fuelCost) {
  public BrewingRecipe {
    ContentIds.require(id, "brewing recipe");
    Objects.requireNonNull(ingredient, "ingredient");
    Objects.requireNonNull(basePotion, "basePotion");
    Objects.requireNonNull(result, "result");
    if (brewingTicks < 1 || fuelCost < 0) {
      throw new IllegalArgumentException("Invalid brewing time or fuel cost for " + id);
    }
  }
}
