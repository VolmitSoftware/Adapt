package com.volmit.adapt;

import com.google.gson.Gson;
import com.volmit.adapt.api.xp.Curves;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.JSONObject;
import lombok.Getter;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ALL")
@Getter
public class AdaptConfig {
    private static AdaptConfig config = null;
    private ValueConfig value = new ValueConfig();
    private boolean verbose = false;
    private boolean metrics = true;
    private Curves xpCurve = Curves.XL3L7;
    private double playerXpPerSkillLevelUpBase = 400;
    private double playerXpPerSkillLevelUpLevelMultiplier = 32;
    private double powerPerLevel = 0.62;

    @Getter
    public static class ValueConfig {
        private double baseValue = 1;
        private Map<String, Double> valueMutlipliers = defaultValueMultipliers();

        private Map<String, Double> defaultValueMultipliers() {
            Map<String, Double> f = new HashMap<>();
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
            File l = Adapt.instance.getDataFile("adapt", "adapt.json");


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
                IO.writeAll(l, new JSONObject(new Gson().toJson(config)).toString(4));
            } catch(IOException e) {
                e.printStackTrace();
                config = new AdaptConfig();
            }
        }

        return config;
    }
}
