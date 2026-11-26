package art.arcane.adapt.content.adaptation.crafting.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.TriggerBlock;
import art.arcane.adapt.content.adaptation.crafting.CraftingIngredients;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires when a crafted tool, weapon or armor piece reaches the player, using Masterwork's gear checks. A piece whose
 * ingredients the player crafted moments before does not count, so a loop of planks, sticks and wooden tools never
 * sets it off.
 */
public class CraftingTrigger extends TriggerBlock<CraftingTrigger.Config> {
  private final Map<UUID, Map<Id, Long>> recentCrafts = playerState();

  public CraftingTrigger() {
    super("block-crafting-trigger");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_chestplate"));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    PlayerRef player = e.player();
    if (!hasActiveAdaptation(player)) {
      return;
    }
    Map<Id, Long> recent = recent(player);
    Id type = e.crafted().type();
    boolean gear = CraftingMasterwork.isTool(type) || CraftingMasterwork.isArmor(type);
    boolean fromFreshParts = gear && usesAny(e.recipeKey(), recent);
    recent.put(type, System.currentTimeMillis());
    if (gear && !fromFreshParts) {
      fire(player, null, player.location(), false);
    }
  }

  /** Records a craft made away from a table, so its result counts as a fresh part. */
  public void crafted(PlayerRef player, Id type) {
    recent(player).put(type, System.currentTimeMillis());
  }

  private Map<Id, Long> recent(PlayerRef player) {
    long now = System.currentTimeMillis();
    Map<Id, Long> recent = recentCrafts.computeIfAbsent(player.uuid(), (UUID ignored) -> new ConcurrentHashMap<>());
    recent.values().removeIf((Long crafted) -> now - crafted > getConfig().freshPartsMillis);
    return recent;
  }

  private static boolean usesAny(Id recipeKey, Map<Id, Long> recent) {
    if (recipeKey == null || recent.isEmpty()) {
      return false;
    }
    Optional<RecipeView> recipe = AdaptCore.platform().recipes().recipe(recipeKey);
    if (recipe.isEmpty()) {
      return false;
    }
    for (RecipeChoice choice : CraftingIngredients.perCraftChoices(recipe.get())) {
      for (Id type : choice.types()) {
        if (recent.containsKey(type)) {
          return true;
        }
      }
      for (ItemRef exact : choice.exact()) {
        if (recent.containsKey(exact.type())) {
          return true;
        }
      }
    }
    return false;
  }

  @ConfigDescription("Craft a tool, weapon or armor piece.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long after crafting an item it cannot count as a part of a crafted piece, in milliseconds.", impact = "Higher values stop longer crafting loops from setting the trigger off.")
    long freshPartsMillis = 10_000L;
  }
}
