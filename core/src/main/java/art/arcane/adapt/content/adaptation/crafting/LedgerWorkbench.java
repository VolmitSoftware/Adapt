package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.tool.MutationToolGuard;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.content.adaptation.crafting.blocks.CraftingTrigger;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeChoice;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Crafts stored recipes straight from the player's inventory and backpacks, with no table. Only shaped and shapeless
 * recipes with a plain result that the player may craft are used: Adapt-gated recipes check their gate, tool recipes
 * never run here. Missing parts are crafted on the way from other stored results, up to a chain depth. Thrifty Hands
 * and Bulk Artisan apply as they do at a table; nothing pays skill XP.
 */
public final class LedgerWorkbench {
  private static final int INVENTORY_SLOTS = 36;

  private final Adaptation<?> owner;

  public LedgerWorkbench(Adaptation<?> owner) {
    this.owner = owner;
  }

  /** What one run made: crafts of the asked result, or the parts the first recipe lacks. */
  public record Result(int crafts, Map<Id, Integer> missing) {
  }

  /** The recipes for {@code result} the player may run from a Ledger, in server order. */
  public List<RecipeView> recipes(PlayerRef p, Id result) {
    List<RecipeView> allowed = new ArrayList<>();
    ItemRef probe = AdaptCore.platform().host().items().create(result, 1);
    for (RecipeView recipe : AdaptCore.platform().recipes().recipesFor(probe)) {
      if (allowed(p, recipe)) {
        allowed.add(recipe);
      }
    }
    return allowed;
  }

  private boolean allowed(PlayerRef p, RecipeView recipe) {
    if (recipe.kind() == RecipeView.Kind.OTHER || recipe.result().isEmpty() || MutationToolGuard.isToolRecipe(recipe.key())
        || !CraftingIngredients.isPlain(recipe.result()) || CraftingIngredients.perCraftChoices(recipe).isEmpty()) {
      return false;
    }
    SkillRegistry registry = owner.getServer().getSkillRegistry();
    Adaptation<?> required = registry.getRequiredAdaptation(recipe.key());
    return required == null || UpgradeLoans.level(required, (Adaptation<?> held) -> held.getActiveLevel(p))
        >= registry.getRequiredRecipeLevel(recipe.key());
  }

  /** The most crafts one sneak-click may make of {@code result}: a stack's worth plus Bulk Artisan's batch. */
  public int batchLimit(PlayerRef p, Id result) {
    ItemRef probe = AdaptCore.platform().host().items().create(result, 1);
    List<RecipeView> recipes = recipes(p, result);
    int perCraft = recipes.isEmpty() ? 1 : Math.max(1, recipes.getFirst().result().amount());
    int stack = Math.max(1, probe.maxStackSize() / perCraft);
    CraftingBulkArtisan bulk = original(CraftingBulkArtisan.class);
    return stack + (bulk == null ? 0 : bulk.bonusCrafts(p, perCraft));
  }

  /** The parts one craft of {@code result} lacks from {@code carried}; empty when it can be made. */
  public Map<Id, Integer> missing(PlayerRef p, Bench carried, Id result, List<Id> pages, int depth) {
    Bench bench = carried.copy();
    if (craftOnce(p, bench, result, pages, depth, new ArrayList<>(), new ArrayList<>(), false)) {
      return Map.of();
    }
    List<RecipeView> recipes = recipes(p, result);
    return recipes.isEmpty() ? Map.of() : deficits(CraftingIngredients.perCraftChoices(recipes.getFirst()), bench.view());
  }

  /** Crafts {@code result} up to {@code limit} times and hands the items over; nothing changes when none can be made. */
  public Result craft(PlayerRef p, Id result, List<Id> pages, int depth, int limit) {
    Bench bench = Bench.of(p.inventory());
    List<ItemRef> made = new ArrayList<>();
    List<Set<Id>> materials = new ArrayList<>();
    int crafts = 0;
    while (crafts < limit && craftOnce(p, bench, result, pages, depth, made, materials, false)) {
      crafts++;
    }
    if (crafts == 0) {
      return new Result(0, missing(p, bench, result, pages, depth));
    }
    boolean[] committed = {false};
    if (!owner.payItemCost(p, "materials", null, crafts, () -> committed[0] = commit(p, bench, made)) || !committed[0]) {
      return new Result(0, Map.of());
    }
    List<MutationBlock<?>> blocks = owner.getSkill() == null ? List.of() : owner.getSkill().getBlocks();
    for (MutationBlock<?> block : blocks) {
      if (block instanceof CraftingTrigger trigger) {
        trigger.crafted(p, result);
      }
    }
    CraftingThriftyHands thrifty = original(CraftingThriftyHands.class);
    if (thrifty != null) {
      thrifty.refundCrafts(p, materials);
    }
    return new Result(crafts, Map.of());
  }

  /** Whether the player carries {@code type} in the inventory or in a backpack there. */
  public static boolean carries(PlayerRef p, Id type) {
    return Bench.of(p.inventory()).holds(type);
  }

  private boolean craftOnce(PlayerRef p, Bench bench, Id result, List<Id> pages, int depth, List<ItemRef> made,
                            List<Set<Id>> materials, boolean intoBench) {
    List<RecipeView> recipes = recipes(p, result);
    for (RecipeView recipe : recipes) {
      if (attempt(bench, recipe, made, materials, intoBench)) {
        return true;
      }
    }
    if (depth <= 0) {
      return false;
    }
    for (RecipeView recipe : recipes) {
      List<RecipeChoice> choices = CraftingIngredients.perCraftChoices(recipe);
      Bench trial = bench.copy();
      List<ItemRef> trialMade = new ArrayList<>();
      List<Set<Id>> trialMaterials = new ArrayList<>();
      for (int step = 0; step < choices.size(); step++) {
        if (attempt(trial, recipe, trialMade, trialMaterials, intoBench)) {
          bench.adopt(trial);
          made.addAll(trialMade);
          materials.addAll(trialMaterials);
          return true;
        }
        Id part = pageFor(lacking(choices, trial.view()), pages, result);
        if (part == null || !craftOnce(p, trial, part, pages, depth - 1, trialMade, trialMaterials, true)) {
          break;
        }
      }
    }
    return false;
  }

  private static boolean attempt(Bench bench, RecipeView recipe, List<ItemRef> made, List<Set<Id>> materials,
                                 boolean intoBench) {
    List<RecipeChoice> choices = CraftingIngredients.perCraftChoices(recipe);
    CraftingIngredients.BatchPlan plan = CraftingIngredients.plan(choices, bench.view(), 1, 0);
    if (plan.crafts() < 1) {
      return false;
    }
    bench.consume(plan.costs());
    if (intoBench) {
      bench.add(recipe.result().copy());
    } else {
      made.add(recipe.result().copy());
      Set<Id> types = new HashSet<>();
      for (RecipeChoice choice : choices) {
        Id type = choice.representativeType();
        if (type != null) {
          types.add(type);
        }
      }
      materials.add(types);
    }
    return true;
  }

  private static Id pageFor(RecipeChoice lacking, List<Id> pages, Id result) {
    if (lacking == null) {
      return null;
    }
    for (Id page : pages) {
      if (!page.equals(result) && lacking.test(AdaptCore.platform().host().items().create(page, 1))) {
        return page;
      }
    }
    return null;
  }

  private static RecipeChoice lacking(List<RecipeChoice> choices, ItemRef[] storage) {
    return shortfall(choices, storage).keySet().stream().findFirst().orElse(null);
  }

  private static Map<Id, Integer> deficits(List<RecipeChoice> choices, ItemRef[] storage) {
    Map<Id, Integer> missing = new LinkedHashMap<>();
    for (Map.Entry<RecipeChoice, Integer> deficit : shortfall(choices, storage).entrySet()) {
      Id type = deficit.getKey().representativeType();
      if (type != null) {
        missing.merge(type, deficit.getValue(), Integer::sum);
      }
    }
    return missing;
  }

  private static Map<RecipeChoice, Integer> shortfall(List<RecipeChoice> choices, ItemRef[] storage) {
    Map<RecipeChoice, Integer> needed = new LinkedHashMap<>();
    for (RecipeChoice choice : choices) {
      needed.merge(choice, 1, Integer::sum);
    }
    Map<RecipeChoice, Integer> shortfall = new LinkedHashMap<>();
    for (Map.Entry<RecipeChoice, Integer> entry : needed.entrySet()) {
      int available = 0;
      for (ItemRef item : storage) {
        if (item != null && entry.getKey().test(item) && (entry.getKey().isExact() || CraftingIngredients.isPlain(item))) {
          available += item.amount();
        }
      }
      if (available < entry.getValue()) {
        shortfall.put(entry.getKey(), entry.getValue() - available);
      }
    }
    return shortfall;
  }

  private boolean commit(PlayerRef p, Bench bench, List<ItemRef> made) {
    PlayerInventoryRef inventory = p.inventory();
    List<ItemRef> handed = new ArrayList<>(made);
    Map<Integer, ItemRef[]> backpacks = new LinkedHashMap<>();
    for (Slot slot : bench.slots) {
      if (slot.inventorySlot < 0) {
        if (slot.amount > 0) {
          handed.add(slot.item.copyWithAmount(slot.amount));
        }
        continue;
      }
      int used = slot.item.amount() - slot.amount;
      if (used <= 0) {
        continue;
      }
      ItemRef remainder = CraftingIngredients.remainder(slot.item.type(), used);
      if (remainder != null) {
        handed.add(remainder);
      }
      ItemRef left = slot.amount > 0 ? slot.item.copyWithAmount(slot.amount) : null;
      if (slot.contentIndex < 0) {
        if (left != null) {
          inventory.setItem(slot.inventorySlot, left);
        } else {
          inventory.clear(slot.inventorySlot);
        }
        continue;
      }
      ItemRef[] contents = backpacks.computeIfAbsent(slot.inventorySlot,
          (Integer index) -> BackpackItem.readContents(inventory.item(index)));
      contents[slot.contentIndex] = left;
    }
    for (Map.Entry<Integer, ItemRef[]> backpack : backpacks.entrySet()) {
      writeBackpack(inventory.item(backpack.getKey()), backpack.getValue());
    }
    for (ItemRef item : handed) {
      for (ItemRef leftover : inventory.addItem(item)) {
        if (leftover != null && !leftover.isEmpty() && leftover.amount() > 0) {
          p.world().dropItemNaturally(p.location().position(), leftover);
        }
      }
    }
    return true;
  }

  private static void writeBackpack(ItemRef backpack, ItemRef[] contents) {
    BackpackItem.Data data = BackpackItem.dataOf(backpack);
    boolean bundle = BackpackItem.modeOf(backpack) == BackpackItem.Mode.BUNDLE;
    ItemRef[] remaining = bundle ? BackpackItem.compact(contents) : contents;
    int used = bundle ? BackpackItem.heapWeight(BackpackItem.readHeap(remaining)) : BackpackItem.countStored(remaining);
    BackpackItem.write(backpack, BackpackItem.Data.of(data.getId(), BackpackItem.Mode.parse(data.getMode()),
        data.getCapacity(), used), remaining);
  }

  private <A extends Adaptation<?>> A original(Class<A> type) {
    if (owner.getSkill() == null) {
      return null;
    }
    for (Adaptation<?> adaptation : owner.getSkill().getAdaptations()) {
      if (type.isInstance(adaptation)) {
        return type.cast(adaptation);
      }
    }
    return null;
  }

  /** One stack the run may draw on: a slot of the inventory or of a backpack there, or a part crafted on the way. */
  private static final class Slot {
    private final int inventorySlot;
    private final int contentIndex;
    private final ItemRef item;
    private int amount;

    private Slot(int inventorySlot, int contentIndex, ItemRef item, int amount) {
      this.inventorySlot = inventorySlot;
      this.contentIndex = contentIndex;
      this.item = item;
      this.amount = amount;
    }
  }

  /** A working copy of what the player carries, consumed and refilled while a run is planned. */
  public static final class Bench {
    private List<Slot> slots;

    private Bench(List<Slot> slots) {
      this.slots = slots;
    }

    public static Bench of(PlayerInventoryRef inventory) {
      List<Slot> slots = new ArrayList<>();
      for (int index = 0; index < INVENTORY_SLOTS; index++) {
        ItemRef item = inventory.item(index);
        if (item == null || item.isEmpty() || item.type().isAir()) {
          continue;
        }
        if (!BackpackItem.isBackpack(item)) {
          slots.add(new Slot(index, -1, item.copy(), item.amount()));
          continue;
        }
        ItemRef[] contents;
        try {
          contents = BackpackItem.readContents(item);
        } catch (IllegalStateException unreadable) {
          continue;
        }
        for (int content = 0; content < contents.length; content++) {
          if (contents[content] != null) {
            slots.add(new Slot(index, content, contents[content].copy(), contents[content].amount()));
          }
        }
      }
      return new Bench(slots);
    }

    ItemRef[] view() {
      ItemRef[] view = new ItemRef[slots.size()];
      for (int index = 0; index < view.length; index++) {
        Slot slot = slots.get(index);
        view[index] = slot.amount > 0 ? slot.item.copyWithAmount(slot.amount) : null;
      }
      return view;
    }

    boolean holds(Id type) {
      for (Slot slot : slots) {
        if (slot.amount > 0 && slot.item.type().equals(type)) {
          return true;
        }
      }
      return false;
    }

    void consume(int[] costs) {
      for (int index = 0; index < costs.length; index++) {
        slots.get(index).amount -= Math.max(0, costs[index]);
      }
    }

    void add(ItemRef item) {
      slots.add(new Slot(-1, -1, item.copy(), item.amount()));
    }

    Bench copy() {
      List<Slot> copied = new ArrayList<>(slots.size());
      for (Slot slot : slots) {
        copied.add(new Slot(slot.inventorySlot, slot.contentIndex, slot.item, slot.amount));
      }
      return new Bench(copied);
    }

    void adopt(Bench trial) {
      slots = trial.slots;
    }
  }
}
