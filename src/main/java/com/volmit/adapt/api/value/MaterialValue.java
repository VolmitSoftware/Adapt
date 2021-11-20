package com.volmit.adapt.api.value;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.value.MaterialCount;
import com.volmit.adapt.api.value.MaterialRecipe;
import com.volmit.adapt.util.ChronoLatch;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.JSONObject;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.KSet;
import com.volmit.adapt.util.PrecisionStopwatch;
import com.volmit.adapt.util.RollingSequence;
import lombok.Getter;
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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class MaterialValue {
    private static MaterialValue valueCache = null;
    private static int hc = -1;
    private KMap<Material, Double> value = new KMap<>();
    private static final KMap<Material, Double> valueMultipliers = new KMap<>();
    private static ChronoLatch saveLatch = new ChronoLatch(60000);

    public static void lazySave()
    {
        if(saveLatch.flip())
        {
            save();
        }
    }

    public static void save()
    {
        if(valueCache == null)
        {
            Adapt.warn("NULL?");
            return;
        }

        if(hc != valueCache.hashCode())
        {
            File l = Adapt.instance.getDataFile("data", "value-cache.json");
            try {
                IO.writeAll(l, new JSONObject(new Gson().toJson(valueCache)).toString(4));
                Adapt.info("Saved Value Cache");
                hc = valueCache.hashCode();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static MaterialValue get()
    {
        if(valueCache == null)
        {
            MaterialValue dummy = new MaterialValue();
            File l = Adapt.instance.getDataFile("data", "value-cache.json");

            if(!l.exists())
            {
                try {
                    IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                } catch(IOException e) {
                    e.printStackTrace();
                    valueCache = dummy;
                    return dummy;
                }
            }

            try {
                valueCache = new Gson().fromJson(IO.readAll(l), MaterialValue.class);
            } catch(IOException e) {
                e.printStackTrace();
                valueCache = new MaterialValue();
            }

            hc = valueCache.hashCode();
        }

        return valueCache;
    }

    public static void debugValue(Material m)
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
        if(get().value.containsKey(m))
        {
            return get().value.get(m);
        }

        double v = AdaptConfig.get().getValue().getBaseValue();

        KList<MaterialRecipe> recipes = getRecipes(m);

        if(recipes.isEmpty())
        {
            get().value.put(m, v * getMultiplier(m));
            lazySave();
        }

        else
        {
            KList<Double> d = new KList<>();
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

                d.add(vx / i.getOutput().getAmount());
            }

            if(d.size() > 0)
            {
                v += d.stream().mapToDouble(i -> i).average().getAsDouble();
            }

            v += AdaptConfig.get().getValue().getMarkupAddative();
            v *= AdaptConfig.get().getValue().getMarkupMultiplier();
            get().value.put(m, v);
            lazySave();
        }

        return get().value.get(m);
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

    static
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
}
