package art.arcane.adapt.bukkit.natives;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapelessRecipe;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Paper 1.21.1 server recipes for {@link ServerRecipes}. */
final class IdentityRecipes {
  private IdentityRecipes() {
  }

  /** A server recipe that can be rebuilt for one recipe book placement. */
  private interface Placeable {
    CraftingRecipe forPlacement(List<ItemStack> available);
  }

  static Recipe shaped(org.bukkit.inventory.ShapedRecipe recipe) {
    return new BukkitShaped(recipe);
  }

  static Recipe shapeless(org.bukkit.inventory.ShapelessRecipe recipe) {
    return new BukkitShapeless(recipe);
  }

  /**
   * Places an identity recipe from the recipe book into the player's open crafting grid, as the server would, with the
   * stacks its identity ingredients accept. False when the recipe or the open menu is not one this handles.
   */
  static boolean place(Player viewer, NamespacedKey key, boolean makeAll) {
    ServerPlayer player = ServerHandles.player(viewer);
    if (player == null || !(player.containerMenu instanceof RecipeBookMenu<?, ?> menu)
        || menu.getRecipeBookType() != RecipeBookType.CRAFTING) {
      return false;
    }
    Optional<RecipeHolder<?>> holder = MinecraftServer.getServer().getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(key));
    if (holder.isEmpty() || !(holder.get().value() instanceof Placeable recipe)) {
      return false;
    }
    List<ItemStack> available = new ArrayList<>(player.getInventory().items);
    for (int slot = 0; slot < menu.getSize(); slot++) {
      if (menu.shouldMoveToInventory(slot)) {
        available.add(menu.getSlot(slot).getItem());
      }
    }
    menu.handlePlacement(makeAll, new RecipeHolder<>(holder.get().id(), recipe.forPlacement(available)), player);
    return true;
  }

  private static Predicate<ItemStack> identity(RecipeChoice choice) {
    return choice instanceof IdentityChoice identity ? (ItemStack stack) -> identity.test(CraftItemStack.asCraftMirror(stack)) : null;
  }

  private static final class BukkitShaped extends CraftShapedRecipe {
    BukkitShaped(org.bukkit.inventory.ShapedRecipe source) {
      super(source.getKey(), source.getResult());
      setGroup(source.getGroup());
      setCategory(source.getCategory());
      shape(source.getShape());
      for (Map.Entry<Character, RecipeChoice> entry : source.getChoiceMap().entrySet()) {
        if (entry.getValue() != null) {
          setIngredient(entry.getKey(), entry.getValue());
        }
      }
    }

    @Override
    public void addToCraftingManager() {
      Map<Character, RecipeChoice> choices = getChoiceMap();
      String[] shape = getShape();
      for (int row = 0; row < shape.length; row++) {
        StringBuilder defined = new StringBuilder(shape[row].length());
        for (char key : shape[row].toCharArray()) {
          defined.append(choices.get(key) == null ? ' ' : key);
        }
        shape[row] = defined.toString();
      }
      Map<Character, Ingredient> ingredients = new HashMap<>();
      Map<Ingredient, Predicate<ItemStack>> identities = new IdentityHashMap<>();
      for (Map.Entry<Character, RecipeChoice> entry : choices.entrySet()) {
        if (entry.getValue() == null) {
          continue;
        }
        Ingredient ingredient = toNMS(entry.getValue(), false);
        ingredients.put(entry.getKey(), ingredient);
        Predicate<ItemStack> identity = identity(entry.getValue());
        if (identity != null) {
          identities.put(ingredient, identity);
        }
      }
      ShapedRecipePattern pattern = ShapedRecipePattern.of(ingredients, shape);
      List<Predicate<ItemStack>> slots = new ArrayList<>(pattern.ingredients().size());
      for (Ingredient ingredient : pattern.ingredients()) {
        slots.add(identities.get(ingredient));
      }
      MinecraftServer.getServer().getRecipeManager().addRecipe(new RecipeHolder<>(CraftNamespacedKey.toMinecraft(getKey()),
          new Shaped(getGroup(), CraftRecipe.getCategory(getCategory()), pattern, CraftItemStack.asNMSCopy(getResult()),
              new IdentitySlots(slots), this)));
    }
  }

  private static final class BukkitShapeless extends CraftShapelessRecipe {
    BukkitShapeless(org.bukkit.inventory.ShapelessRecipe source) {
      super(source.getKey(), source.getResult());
      setGroup(source.getGroup());
      setCategory(source.getCategory());
      for (RecipeChoice choice : source.getChoiceList()) {
        addIngredient(choice);
      }
    }

    @Override
    public void addToCraftingManager() {
      List<RecipeChoice> choices = getChoiceList();
      NonNullList<Ingredient> ingredients = NonNullList.withSize(choices.size(), Ingredient.EMPTY);
      List<Predicate<ItemStack>> slots = new ArrayList<>(choices.size());
      for (int index = 0; index < choices.size(); index++) {
        ingredients.set(index, toNMS(choices.get(index), true));
        slots.add(identity(choices.get(index)));
      }
      MinecraftServer.getServer().getRecipeManager().addRecipe(new RecipeHolder<>(CraftNamespacedKey.toMinecraft(getKey()),
          new Shapeless(getGroup(), CraftRecipe.getCategory(getCategory()), CraftItemStack.asNMSCopy(getResult()), ingredients,
              new IdentitySlots(slots), this)));
    }
  }

  private static final class Shaped extends ShapedRecipe implements Placeable {
    private final ItemStack result;
    private final IdentitySlots slots;
    private final BukkitShaped source;

    Shaped(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, IdentitySlots slots,
           BukkitShaped source) {
      super(group, category, pattern, result);
      this.result = result;
      this.slots = slots;
      this.source = source;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
      int width = getWidth();
      int height = getHeight();
      if (input.width() != width || input.height() != height) {
        return false;
      }
      return slots.shaped(getIngredients(), width, height, input::getItem, width, height, 0, 0, true)
          || slots.shaped(getIngredients(), width, height, input::getItem, width, height, 0, 0, false);
    }

    @Override
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe(NamespacedKey id) {
      return new BukkitShaped(source);
    }

    @Override
    public CraftingRecipe forPlacement(List<ItemStack> available) {
      ShapedRecipePattern pattern = new ShapedRecipePattern(getWidth(), getHeight(),
          slots.forPlacement(getIngredients(), available), Optional.empty());
      return new Shaped(getGroup(), category(), pattern, result, slots, source);
    }
  }

  private static final class Shapeless extends ShapelessRecipe implements Placeable {
    private final ItemStack result;
    private final IdentitySlots slots;
    private final BukkitShapeless source;

    Shapeless(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients,
              IdentitySlots slots, BukkitShapeless source) {
      super(group, category, result, ingredients);
      this.result = result;
      this.slots = slots;
      this.source = source;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
      return slots.shapeless(getIngredients(), input.items());
    }

    @Override
    public org.bukkit.inventory.ShapelessRecipe toBukkitRecipe(NamespacedKey id) {
      return new BukkitShapeless(source);
    }

    @Override
    public CraftingRecipe forPlacement(List<ItemStack> available) {
      return new Shapeless(getGroup(), category(), result, slots.forPlacement(getIngredients(), available), slots, source);
    }
  }
}
