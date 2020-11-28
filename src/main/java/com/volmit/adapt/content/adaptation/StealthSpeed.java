package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class StealthSpeed extends SimpleAdaptation {
    public StealthSpeed() {
        super("sneak-speed");
        setDescription("Move faster while sneaking");
        setIcon(Material.MUSHROOM_STEW);
        setBaseCost(2);
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e)
    {
        double factor = getLevelPercent(e.getPlayer());

        if(factor == 0)
        {
            return;
        }
        AttributeModifier mod = new AttributeModifier("adapt-sneak-speed", factor*1.25, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        if(e.isSneaking())
        {
            e.getPlayer().getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(mod);
        }

        else
        {
            for(AttributeModifier i : e.getPlayer().getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getModifiers())
            {
                if(i.getName().equals("adapt-sneak-speed"))
                {
                    e.getPlayer().getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).removeModifier(i);
                }
            }
        }
    }

    @Override
    public void onTick() {

    }
}
