package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class StealthSpeed extends SimpleAdaptation<StealthSpeed.Config> {
    public StealthSpeed() {
        super("sneak-speed");
        setDescription("Move faster while sneaking");
        setIcon(Material.MUSHROOM_STEW);
        setBaseCost(2);
        setInterval(2000);
        setInitialCost(5);
        setCostFactor(0.6);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSpeed(getLevelPercent(level)), 0) + C.GRAY + " Sneak Speed");
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        double factor = getLevelPercent(e.getPlayer());

        if(factor == 0) {
            return;
        }
        AttributeModifier mod = new AttributeModifier("adapt-sneak-speed", getSpeed(factor), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        if(e.isSneaking()) {
            e.getPlayer().getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(mod);
        } else {
            for(AttributeModifier i : e.getPlayer().getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers()) {
                if(i.getName().equals("adapt-sneak-speed")) {
                    e.getPlayer().getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(i);
                }
            }
        }
    }

    private double getSpeed(double factor) {
        return factor * 1.25;
    }

    @Override
    public void onTick() {

    }

    protected static class Config{}
}
