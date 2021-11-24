package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.api.value.MaterialCount;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;

import java.util.List;

public interface AdaptRecipe
{
    ItemStack getResult();

    String getKey();

    static Shapeless.ShapelessBuilder shapeless()
    {
        return Shapeless.builder();
    }

    static Shaped.ShapedBuilder shaped()
    {
        return Shaped.builder();
    }

    static Smithing.SmithingBuilder smithing()
    {
        return Smithing.builder();
    }

    void register();

    @Builder
    @Data
    class Shapeless implements AdaptRecipe
    {
        private ItemStack result;
        @Singular
        private List<Material> ingredients;
        @Override
        public ItemStack getResult() {
            return null;
        }

        @Override
        public String getKey() {
            return null;
        }

        public void register()
        {
            ShapelessRecipe s = new ShapelessRecipe(new NamespacedKey(Adapt.instance, getKey()), result);
            ingredients.forEach(s::addIngredient);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Shapeless Crafting Recipe " + s.getKey());
        }
    }

    @Builder
    @Data
    class Shaped implements AdaptRecipe
    {
        private ItemStack result;
        @Singular
        private List<MaterialChar> ingredients;
        @Singular
        private List<String> shapes;
        @Override
        public ItemStack getResult() {
            return null;
        }

        @Override
        public String getKey() {
            return null;
        }

        public void register()
        {
            ShapedRecipe s = new ShapedRecipe(new NamespacedKey(Adapt.instance, getKey()), result);
            s.shape(shapes.toArray(new String[0]));
            ingredients.forEach(i -> s.setIngredient(i.getCharacter(), i.getMaterial()));
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Shaped Crafting Recipe " + s.getKey());
        }
    }

    @Builder
    @Data
    class Smithing implements AdaptRecipe
    {
        private ItemStack result;
        private Material base;
        private Material addition;
        @Override
        public ItemStack getResult() {
            return null;
        }

        @Override
        public String getKey() {
            return null;
        }

        public void register()
        {
            SmithingRecipe s = new SmithingRecipe(new NamespacedKey(Adapt.instance, getKey()), result, new RecipeChoice.ExactChoice(new ItemStack(base)), new RecipeChoice.ExactChoice(new ItemStack(addition)));
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Smithing Table Recipe " + s.getKey());
        }
    }
}
