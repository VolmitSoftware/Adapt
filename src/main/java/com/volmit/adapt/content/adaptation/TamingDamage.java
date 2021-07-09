package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.UUID;

public class TamingDamage extends SimpleAdaptation {
    private final UUID attUUID = UUID.nameUUIDFromBytes("tame-damage-boost".getBytes());
    private final String attid = "att-tame-damage-boost";


    public TamingDamage() {
        super("tame-damage-boost");
        setDescription("Increase your tamed animal health.");
        setIcon(Material.FLINT);
        setBaseCost(6);
        setMaxLevel(5);
        setInitialCost(5);
        setInterval(4750);
        setCostFactor(0.4);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDamageBoost(level), 0) + C.GRAY + " Increased Damage");
    }

    private double getDamageBoost(int level) {
        return ((getLevelPercent(level) * 0.65) + 0.08);
    }

    @Override
    public void onTick() {
        for(World i : Bukkit.getServer().getWorlds())
        {
            for(Tameable j : i.getEntitiesByClass(Tameable.class))
            {
                if(j.isTamed() && j.getOwner() instanceof Player) {
                    Player p = (Player) j.getOwner();
                    update(j, getLevel(p));
                }
            }
        }
    }

    private void update(Tameable j, int level) {
        AttributeModifier mod = new AttributeModifier(attUUID, attid, getDamageBoost(level), AttributeModifier.Operation.ADD_SCALAR);
        j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).removeModifier(mod);

        if(level > 0)
        {
            j.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).addModifier(mod);
        }
    }
}
