package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

public class SkillAirborne extends SimpleSkill {
    public SkillAirborne() {
        super("airborne", "\u2708");
        setDescription("The wind and sky are malleable illusions");
        setColor(C.BLUE);
        setBarColor(BarColor.BLUE);
        setBarStyle(BarStyle.SEGMENTED_6);
        setIcon(Material.ELYTRA);
        setInterval(1280);
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            if(i.isFlying() || i.isGliding())
            {
                xpSilent(i, 27);
            }
        }
    }
}
