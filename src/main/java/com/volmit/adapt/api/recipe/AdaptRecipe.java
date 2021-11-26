package com.volmit.adapt.api.recipe;

import com.volmit.adapt.Adapt;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.List;

public interface AdaptRecipe {
    ItemStack getResult();

    String getKey();

    default NamespacedKey getNSKey() {
        return new NamespacedKey(Adapt.instance, getKey());
    }

    static Shapeless.ShapelessBuilder shapeless() {
        return Shapeless.builder();
    }

    static Shaped.ShapedBuilder shaped() {
        return Shaped.builder();
    }

    static Smithing.SmithingBuilder smithing() {
        return Smithing.builder();
    }

    static Stonecutter.StonecutterBuilder stonecutter() {
        return Stonecutter.builder();
    }

    static Smoker.SmokerBuilder smoker() {
        return Smoker.builder();
    }

    static Blast.BlastBuilder blast() {
        return Blast.builder();
    }

    static Furnace.FurnaceBuilder furnace() {
        return Furnace.builder();
    }

    static Merchant.MerchantBuilder merchant() {
        return Merchant.builder();
    }

    static Campfire.CampfireBuilder campfire() {
        return Campfire.builder();
    }

    void register();

    boolean is(Recipe recipe);

    void unregister();

    @Builder
    @Data
    class Smoker implements AdaptRecipe {
        private String key;
        private ItemStack result;
        private Material ingredient;
        private float experience = 1;
        private int cookTime = 20;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            SmokingRecipe s = new SmokingRecipe(new NamespacedKey(Adapt.instance, getKey()), result, ingredient, experience, cookTime);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Smoker Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof SmokingRecipe s && s.getKey().equals(getNSKey());
        }


        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Smoker Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Furnace implements AdaptRecipe {
        private String key;
        private ItemStack result;
        private Material ingredient;
        private float experience = 1;
        private int cookTime = 20;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            FurnaceRecipe s = new FurnaceRecipe(new NamespacedKey(Adapt.instance, getKey()), result, ingredient, experience, cookTime);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Furnace Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof FurnaceRecipe s && s.getKey().equals(getNSKey());
        }


        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Furnace Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Campfire implements AdaptRecipe {
        private String key;
        private ItemStack result;
        private Material ingredient;
        private float experience = 1;
        private int cookTime = 20;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            CampfireRecipe s = new CampfireRecipe(new NamespacedKey(Adapt.instance, getKey()), result, ingredient, experience, cookTime);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Campfire Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof CampfireRecipe s && s.getKey().equals(getNSKey());
        }

        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Campfire Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Blast implements AdaptRecipe {
        private String key;
        private ItemStack result;
        private Material ingredient;
        private float experience = 1;
        private int cookTime = 20;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            BlastingRecipe s = new BlastingRecipe(new NamespacedKey(Adapt.instance, getKey()), result, ingredient, experience, cookTime);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Blast Furnace Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof BlastingRecipe s && s.getKey().equals(getNSKey());
        }


        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Blast Furnace Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Merchant implements AdaptRecipe {
        private String key;
        private ItemStack result;
        @Singular
        private List<ItemStack> ingredients;
        private float priceMultiplier = 1f;
        private int villagerXp = 1;
        private int uses = 1;
        private int maxUses = 1;

        @Override
        public ItemStack getResult() {
            return null;
        }

        private MerchantRecipe build()
        {
            MerchantRecipe s = new MerchantRecipe(result, uses, maxUses, true, villagerXp, priceMultiplier);
            ingredients.forEach(s::addIngredient);
            return s;
        }

        public void register() {
            Bukkit.getServer().addRecipe(build());
            Adapt.verbose("Registered Merchant Recipe " + getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof MerchantRecipe s && recipe.equals(build());
        }


        @Override
        public void unregister() {
            Adapt.verbose("Unable to unregister merchant recipes? " + getKey());
        }
    }

    @Builder
    @Data
    class Shapeless implements AdaptRecipe {
        private String key;
        private ItemStack result;
        @Singular
        private List<Material> ingredients;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            ShapelessRecipe s = new ShapelessRecipe(new NamespacedKey(Adapt.instance, getKey()), result);
            ingredients.forEach(s::addIngredient);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Shapeless Crafting Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof ShapelessRecipe s && s.getKey().equals(getNSKey());
        }


        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Shapeless Crafting Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Stonecutter implements AdaptRecipe {
        private String key;
        private ItemStack result;
        private Material ingredient;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            StonecuttingRecipe s = new StonecuttingRecipe(new NamespacedKey(Adapt.instance, getKey()), result, ingredient);
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Stone Cutter Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof StonecuttingRecipe s && s.getKey().equals(getNSKey());
        }


        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Stone Cutter Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Shaped implements AdaptRecipe {
        private String key;
        private ItemStack result;
        @Singular
        private List<MaterialChar> ingredients;
        @Singular
        private List<String> shapes;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            ShapedRecipe s = new ShapedRecipe(new NamespacedKey(Adapt.instance, getKey()), result);
            s.shape(shapes.toArray(new String[0]));
            ingredients.forEach(i -> s.setIngredient(i.getCharacter(), i.getMaterial()));
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Shaped Crafting Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof ShapedRecipe s && s.getKey().equals(getNSKey());
        }

        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Shaped Crafting Recipe " + getKey());
        }
    }

    @Builder
    @Data
    class Smithing implements AdaptRecipe {
        private String key;
        private ItemStack result;
        private Material base;
        private Material addition;

        @Override
        public ItemStack getResult() {
            return null;
        }

        public void register() {
            SmithingRecipe s = new SmithingRecipe(new NamespacedKey(Adapt.instance, getKey()), result, new RecipeChoice.ExactChoice(new ItemStack(base)), new RecipeChoice.ExactChoice(new ItemStack(addition)));
            Bukkit.getServer().addRecipe(s);
            Adapt.verbose("Registered Smithing Table Recipe " + s.getKey());
        }

        @Override
        public boolean is(Recipe recipe) {
            return recipe instanceof SmithingRecipe s && s.getKey().equals(getNSKey());
        }

        @Override
        public void unregister() {
            Bukkit.getServer().removeRecipe(getNSKey());
            Adapt.verbose("Unregistered Smithing Table Recipe " + getKey());
        }
    }
}
