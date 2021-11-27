package com.volmit.adapt.content.adaptation.taming;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.Collection;
import java.util.UUID;

public class TamingHealthBoost extends SimpleAdaptation<TamingHealthBoost.Config> {
    private final UUID attUUID = UUID.nameUUIDFromBytes("health-boost".getBytes());
    private final String attid = "att-health-boost";


    public TamingHealthBoost() {
        super("tame-health-boost");
        registerConfiguration(Config.class);
        setDescription("Increase your tamed animal health.");
        setIcon(Material.COOKED_BEEF);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4750);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getHealthBoost(level), 0) + C.GRAY + " Max Health");
    }

    private double getHealthBoost(int level) {
        return ((getLevelPercent(level) *getConfig().healthBoostFactor) + getConfig().healthBoostBase);
    }

    @Override
    public void onTick() {
        for(World i : Bukkit.getServer().getWorlds()) {
            J.s(() -> {
                Collection<Tameable> gl = i.getEntitiesByClass(Tameable.class);

                J.a(() -> {
                    for(Tameable j : gl) {
                        if(j.isTamed() && j.getOwner() instanceof Player) {
                            Player p = (Player) j.getOwner();
                            update(j, getLevel(p));
                        }
                    }
                });
            });
        }
    }

    private void update(Tameable j, int level) {
        AttributeModifier mod = new AttributeModifier(attUUID, attid, getHealthBoost(level), AttributeModifier.Operation.ADD_SCALAR);
        j.getAttribute(Attribute.GENERIC_MAX_HEALTH).removeModifier(mod);

        if(level > 0) {
            j.getAttribute(Attribute.GENERIC_MAX_HEALTH).addModifier(mod);
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 5;
        int initialCost = 3;
        double costFactor = 0.4;
        double healthBoostFactor = 2.5;
        double healthBoostBase = 0.57;
    }
}
