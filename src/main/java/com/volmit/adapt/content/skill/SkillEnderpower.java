package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.*;
import com.volmit.adapt.util.C;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;

public class SkillEnderpower extends SimpleSkill {
    public SkillEnderpower() {
        super("enderpower", "\u21C9");
        setDescription("Reality is your playground");
        setColor(C.DARK_PURPLE);
        setBarColor(BarColor.PURPLE);
        setInterval(1100);
        setIcon(Material.ENDER_PEARL);
        registerAdaptation(new EnderAura());
        registerAdaptation(new EnderAccess());
        registerAdaptation(new EnderStorage());
        registerAdaptation(new EnderSphere());
        registerAdaptation(new EnderRing());
    }

    @Override
    public void onTick() {
        //TODO implement some kind of experience system
//        for(Player i : Bukkit.getOnlinePlayers())
//        {
//            if(i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking())
//            {
//                xpSilent(i, 11.9);
//            }
//        }
    }
}
