package com.volmit.adapt.api.value;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.value.MaterialCount;
import com.volmit.adapt.api.value.MaterialRecipe;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.KSet;
import com.volmit.adapt.util.PrecisionStopwatch;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.stream.Collectors;

public class MaterialValue {
    private static final KMap<Material, Double> value = new KMap<>();
    private static final KMap<Material, Double> valueMultipliers = new KMap<>();

    public static void computeValue()
    {
        AdaptConfig.get().getValue().getValueMutlipliers().forEach((k,v) -> {
            try
            {
                Material m = Material.valueOf(k.toUpperCase());

                if(m != null)
                {
                    valueMultipliers.put(m, v);
                }
            }

            catch(Throwable e)
            {

            }
        });

        debugValue(Material.ANVIL);
    }

    private static void debugValue(Material m)
    {
        debugValue(m, 0, 1, new KSet<>());
    }

    private static void debugValue(Material m, int ind, int x, KSet<MaterialRecipe> ignore)
    {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        Adapt.info(Form.repeat("  ", ind) + m.name() + ": " + getValue(m) + (x == 1 ? "" : " (x" + x + ")"));

        int r = 0;
        for(MaterialRecipe i : getRecipes(m))
        {
            if(ignore.contains(i)) {
                continue;
            }

            ignore.add(i);

            int o = i.getOutput().getAmount();
            Adapt.info(Form.repeat("  ", ind) + "# Recipe [" + ind + "x" + r + (o == 1 ? "]" : "] (x" + o + ")"));

            for(MaterialCount j : i.getInput())
            {
                debugValue(j.getMaterial(), ind + 1, j.getAmount(), ignore);
            }

            r++;
        }
        Adapt.info(Form.repeat("  ", ind) + " took " + Form.duration(p.getMilliseconds(), 0));
    }

    private  static double getMultiplier(Material m)
    {
        Double d = AdaptConfig.get().getValue().getValueMutlipliers().get(m);

        return d == null ? 1 : d;
    }


    public static double getValue(Material m)
    {
        return getValue(m, new KSet<>());
    }

    public static double getValue(Material m, KSet<MaterialRecipe> ignore)
    {
        if(value.containsKey(m))
        {
            return value.get(m);
        }

        double v = AdaptConfig.get().getValue().getBaseValue();

        KList<MaterialRecipe> recipes = getRecipes(m);

        if(recipes.isEmpty())
        {
            value.put(m, v * getMultiplier(m));
        }

        else
        {
            for(MaterialRecipe i : recipes)
            {
                if(ignore.contains(i))
                {
                    continue;
                }

                ignore.add(i);

                double vx = v;

                for(MaterialCount j : i.getInput())
                {
                    vx += getValue(j.getMaterial(), ignore);
                }

                v = Math.max(vx / i.getOutput().getAmount(), v);
            }

            v += AdaptConfig.get().getValue().getMarkupAddative();
            v *= AdaptConfig.get().getValue().getMarkupMultiplier();
            value.put(m, v);
        }

        return value.get(m);
    }

    private static KList<MaterialRecipe> getRecipes(Material mat)
    {
        KList<MaterialRecipe> r = new KList<>();

        try
        {
            ItemStack is = new ItemStack(mat);

            try
            {
                is.setDurability((short) -1);
            }

            catch(Throwable e)
            {

            }

            Bukkit.getRecipesFor(is).forEach(i -> {
                MaterialRecipe rx = toMaterial(i);

                if(rx != null)
                {
                    r.add(rx);
                }
            });
        }

        catch(Throwable e)
        {

        }

        return r;
    }

    private static MaterialRecipe toMaterial(Recipe r)
    {
       try
       {
           if(r instanceof ShapelessRecipe recipe)
           {
               return MaterialRecipe.builder()
                   .input(new KList<>(recipe.getIngredientList().stream().map(i -> new MaterialCount(i.getType(), 1)).toList()))
                   .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                   .build();
           }

           else if(r instanceof ShapedRecipe recipe)
           {
               MaterialRecipe re = MaterialRecipe.builder()
                   .input(new KList<>())
                   .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                   .build();
               KMap<Material, Integer> f = new KMap<>();
               for(ItemStack i : recipe.getIngredientMap().values())
               {
                   if(i == null || i.getType() == null || i.getType().isAir())
                   {
                       continue;
                   }

                   f.compute(i.getType(), (k, v) -> v == null ? 1 : v+1);
               }

               f.forEach((k,v) -> re.getInput().add(new MaterialCount(k, v)));

               return re;
           }

           else if(r instanceof CookingRecipe recipe)
           {
               return MaterialRecipe.builder()
                   .input(new KList<>(new MaterialCount(recipe.getInput().getType(), 1)))
                   .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                   .build();
           }

           else if(r instanceof MerchantRecipe recipe)
           {
               return MaterialRecipe.builder()
                   .input(new KList<>(recipe.getIngredients().stream().map(i -> new MaterialCount(i.getType(), 1)).toList()))
                   .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                   .build();
           }

           else if(r instanceof StonecuttingRecipe recipe)
           {
               return MaterialRecipe.builder()
                   .input(new KList<>(new MaterialCount(recipe.getInput().getType(), 1)))
                   .output(new MaterialCount(recipe.getResult().getType(), recipe.getResult().getAmount()))
                   .build();
           }
       }

       catch(Throwable e)
       {
            e.printStackTrace();
       }

        return null;
    }
}
