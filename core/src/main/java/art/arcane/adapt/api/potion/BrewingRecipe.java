package art.arcane.adapt.api.potion;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BrewingRecipe {
  private final String id;
  private final Id ingredient;
  private final ItemRef basePotion, result;
  private final int brewingTime, fuelCost;
}
