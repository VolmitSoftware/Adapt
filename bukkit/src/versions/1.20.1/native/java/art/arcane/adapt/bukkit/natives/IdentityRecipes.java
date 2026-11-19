package art.arcane.adapt.bukkit.natives;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftShapedRecipe;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftShapelessRecipe;
import org.bukkit.craftbukkit.v1_20_R1.util.CraftNamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Paper 1.20.1 server recipes for {@link ServerRecipes}. */
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
    if (player == null || !(player.containerMenu instanceof RecipeBookMenu<?> menu)
        || menu.getRecipeBookType() != RecipeBookType.CRAFTING) {
      return false;
    }
    Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> recipe =
        MinecraftServer.getServer().getRecipeManager().byKey(CraftNamespacedKey.toMinecraft(key));
    if (recipe.isEmpty() || !(recipe.get() instanceof Placeable placeable)) {
      return false;
    }
    List<ItemStack> available = new ArrayList<>(player.getInventory().items);
    for (int slot = 0; slot < menu.getSize(); slot++) {
      if (menu.shouldMoveToInventory(slot)) {
        available.add(menu.getSlot(slot).getItem());
      }
    }
    menu.handlePlacement(makeAll, placeable.forPlacement(available), player);
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
      String[] shape = getShape();
      Map<Character, RecipeChoice> choices = getChoiceMap();
      int width = shape[0].length();
      NonNullList<Ingredient> ingredients = NonNullList.withSize(shape.length * width, Ingredient.EMPTY);
      List<Predicate<ItemStack>> slots = new ArrayList<>(shape.length * width);
      for (int row = 0; row < shape.length; row++) {
        for (int column = 0; column < width; column++) {
          RecipeChoice choice = choices.get(shape[row].charAt(column));
          ingredients.set(row * width + column, toNMS(choice, false));
          slots.add(identity(choice));
        }
      }
      MinecraftServer.getServer().getRecipeManager().addRecipe(new Shaped(CraftNamespacedKey.toMinecraft(getKey()), getGroup(),
          CraftRecipe.getCategory(getCategory()), width, shape.length, ingredients, CraftItemStack.asNMSCopy(getResult()),
          new IdentitySlots(slots), this));
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
      MinecraftServer.getServer().getRecipeManager().addRecipe(new Shapeless(CraftNamespacedKey.toMinecraft(getKey()), getGroup(),
          CraftRecipe.getCategory(getCategory()), CraftItemStack.asNMSCopy(getResult()), ingredients, new IdentitySlots(slots), this));
    }
  }

  private static final class Shaped extends ShapedRecipe implements Placeable {
    private final ItemStack result;
    private final IdentitySlots slots;
    private final BukkitShaped source;

    Shaped(ResourceLocation id, String group, CraftingBookCategory category, int width, int height,
           NonNullList<Ingredient> ingredients, ItemStack result, IdentitySlots slots, BukkitShaped source) {
      super(id, group, category, width, height, ingredients, result);
      this.result = result;
      this.slots = slots;
      this.source = source;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
      List<ItemStack> items = container.getItems();
      int gridWidth = container.getWidth();
      int gridHeight = container.getHeight();
      IdentitySlots.Grid grid = (int x, int y) -> items.get(x + y * gridWidth);
      for (int offsetX = 0; offsetX <= gridWidth - getWidth(); offsetX++) {
        for (int offsetY = 0; offsetY <= gridHeight - getHeight(); offsetY++) {
          if (slots.shaped(getIngredients(), getWidth(), getHeight(), grid, gridWidth, gridHeight, offsetX, offsetY, true)
              || slots.shaped(getIngredients(), getWidth(), getHeight(), grid, gridWidth, gridHeight, offsetX, offsetY, false)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public org.bukkit.inventory.ShapedRecipe toBukkitRecipe() {
      return new BukkitShaped(source);
    }

    @Override
    public CraftingRecipe forPlacement(List<ItemStack> available) {
      return new Shaped(getId(), getGroup(), category(), getWidth(), getHeight(),
          slots.forPlacement(getIngredients(), available), result, slots, source);
    }
  }

  private static final class Shapeless extends ShapelessRecipe implements Placeable {
    private final ItemStack result;
    private final IdentitySlots slots;
    private final BukkitShapeless source;

    Shapeless(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result,
              NonNullList<Ingredient> ingredients, IdentitySlots slots, BukkitShapeless source) {
      super(id, group, category, result, ingredients);
      this.result = result;
      this.slots = slots;
      this.source = source;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
      return slots.shapeless(getIngredients(), container.getItems());
    }

    @Override
    public org.bukkit.inventory.ShapelessRecipe toBukkitRecipe() {
      return new BukkitShapeless(source);
    }

    @Override
    public CraftingRecipe forPlacement(List<ItemStack> available) {
      return new Shapeless(getId(), getGroup(), category(), result, slots.forPlacement(getIngredients(), available), slots, source);
    }
  }
}
