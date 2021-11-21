package com.volmit.adapt;

import com.google.gson.Gson;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.JSONObject;
import com.volmit.adapt.util.KMap;
import lombok.Getter;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;

@Getter
public class AdaptConfig {
    private static AdaptConfig config = null;

    private final ValueConfig value = new ValueConfig();
    private final boolean verbose = false;

    @Getter
    public static class ValueConfig {
        private final double baseValue = 1;
        private final double markupMultiplier = 1.25;
        private final double markupAddative = 3;
        private final KMap<String, Double> valueMutlipliers = defaultValueMultipliers();


        private KMap<String, Double> defaultValueMultipliers() {
            KMap<String, Double> f = new KMap<>();
            f.put(Material.BLAZE_ROD.name(), 50D);
            f.put(Material.ENDER_PEARL.name(), 75D);
            f.put(Material.GHAST_TEAR.name(), 100D);
            f.put(Material.LEATHER.name(), 1.5D);
            f.put(Material.BEEF.name(), 1.125D);
            f.put(Material.PORKCHOP.name(), 1.125D);
            f.put(Material.EGG.name(), 1.335D);
            f.put(Material.CHICKEN.name(), 1.13D);
            f.put(Material.MUTTON.name(), 1.125D);
            f.put(Material.WHEAT.name(), 1.25D);
            f.put(Material.BEETROOT.name(), 1.25D);
            f.put(Material.CARROT.name(), 1.25D);
            f.put(Material.FLINT.name(), 1.35D);
            f.put(Material.IRON_ORE.name(), 1.75D);
            f.put(Material.DIAMOND_ORE.name(), 10D);
            f.put(Material.GOLD_ORE.name(), 7D);
            f.put(Material.LAPIS_ORE.name(), 9D);
            f.put(Material.COAL_ORE.name(), 1.35D);
            f.put(Material.REDSTONE_ORE.name(), 5D);
            f.put(Material.NETHER_GOLD_ORE.name(), 8.5D);
            f.put(Material.NETHER_QUARTZ_ORE.name(), 1.11D);
            return f;
        }
    }

    public static AdaptConfig get() {
        if(config == null) {
            AdaptConfig dummy = new AdaptConfig();
            File l = Adapt.instance.getDataFile("config.json");

            if(!l.exists()) {
                try {
                    IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                } catch(IOException e) {
                    e.printStackTrace();
                    config = dummy;
                    return dummy;
                }
            }

            try {
                config = new Gson().fromJson(IO.readAll(l), AdaptConfig.class);
            } catch(IOException e) {
                e.printStackTrace();
                config = new AdaptConfig();
            }
        }

        return config;
    }
}
