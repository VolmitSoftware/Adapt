package com.volmit.adapt.content.adaptation.taming;

import com.volmit.adapt.Adapt;
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

public class TamingDamage extends SimpleAdaptation<TamingDamage.Config> {
    private final UUID attUUID = UUID.nameUUIDFromBytes("tame-damage-boost".getBytes());
    private final String attid = "att-tame-damage-boost";


    public TamingDamage() {
        super("tame-damage");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Taming","TameDamage", "Description"));
        setDisplayName(Adapt.dLocalize("Taming","TameDamage", "Name"));
        setIcon(Material.FLINT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(4750);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageBoost(level), 0) + C.GRAY + " " +Adapt.dLocalize("Taming","TameDamage", "Lore1"));
    }

    private double getDamageBoost(int level) {
        return ((getLevelPercent(level) * getConfig().damageFactor) + getConfig().baseDamage);
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
        AttributeModifier mod = new AttributeModifier(attUUID, attid, getDamageBoost(level), AttributeModifier.Operation.ADD_SCALAR);
        j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).removeModifier(mod);

        if(level > 0) {
            j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).addModifier(mod);
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
        int initialCost = 5;
        double costFactor = 0.4;
        double baseDamage = 0.08;
        double damageFactor = 0.65;
    }
}
