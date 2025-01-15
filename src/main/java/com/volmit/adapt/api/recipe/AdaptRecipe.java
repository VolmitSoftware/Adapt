/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.api.recipe;

import com.volmit.adapt.Adapt;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;

import java.util.List;

public interface AdaptRecipe {
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

    static Campfire.CampfireBuilder campfire() {
        return Campfire.builder();
    }

    ItemStack getResult();

    String getKey();

    default NamespacedKey getNSKey() {
        return new NamespacedKey(Adapt.instance, getKey());
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
        private float experience;
        private int cookTime;

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
        private float experience;
        private int cookTime;

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
        private float experience;
        private int cookTime;

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
        private float experience;
        private int cookTime;

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
            ingredients.forEach(i -> s.setIngredient(i.getCharacter(), i.getChoice()));
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
