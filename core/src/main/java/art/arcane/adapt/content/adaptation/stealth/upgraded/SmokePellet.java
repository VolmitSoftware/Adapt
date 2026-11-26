package art.arcane.adapt.content.adaptation.stealth.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.function.Consumer;

/**
 * Phantom Decoy's Smoke Pellet: right-click while unseen to Shadow Step behind the creature you look at. Each step and
 * each decoy spends one pellet.
 */
public final class SmokePellet extends MutationTool<MutationTool.NoData> {
  static final int CRAFTED = 4;

  private final PhantomDecoy owner;

  SmokePellet(PhantomDecoy owner) {
    super("tool-stealth-smoke-pellet", owner, Id.minecraft("firework_star"), 1, NoData.class,
        StealthMessages.TOOL_SMOKE_PELLET_NAME, StealthMessages.TOOL_SMOKE_PELLET_LORE1);
    this.owner = owner;
  }

  @Override
  public List<RecipeSpec> recipes() {
    ItemRef result = create(new NoData());
    result.setAmount(CRAFTED);
    return List.of(new RecipeSpec.Shapeless(Id.adapt("tool-stealth-smoke-pellet"),
        List.of(Ingredient.of(Id.minecraft("gunpowder")), Ingredient.of(Id.minecraft("ink_sac"))), result, 1));
  }

  @Override
  protected void use(ToolUse use) {
    if (use.rightClick() || use.entity() != null) {
      owner.shadowStep(use);
    }
  }

  @Override
  protected int maxStackSize() {
    return 64;
  }

  /** Takes one pellet from the hands first, then the inventory; false when the player carries none. */
  boolean take(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    if (takeFrom(inventory.mainHand(), inventory::setMainHand) || takeFrom(inventory.offHand(), inventory::setOffHand)) {
      return true;
    }
    for (int slot = 0; slot < inventory.size(); slot++) {
      int index = slot;
      if (takeFrom(inventory.item(slot), (ItemRef rest) -> inventory.setItem(index, rest))) {
        return true;
      }
    }
    return false;
  }

  /** Gives back one pellet that {@link #take} spent on an action that did not happen. */
  void refund(PlayerRef player) {
    List<ItemRef> left = player.inventory().addItem(create(new NoData()));
    for (ItemRef stack : left) {
      player.world().dropItem(player.location().position(), stack);
    }
  }

  private boolean takeFrom(ItemRef stack, Consumer<ItemRef> put) {
    if (!is(stack) || stack.amount() <= 0) {
      return false;
    }
    if (stack.amount() == 1) {
      put.accept(AdaptCore.platform().host().items().empty());
    } else {
      stack.setAmount(stack.amount() - 1);
      put.accept(stack);
    }
    return true;
  }
}
