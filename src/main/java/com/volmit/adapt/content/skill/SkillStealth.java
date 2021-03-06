package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.StealthSnatch;
import com.volmit.adapt.content.adaptation.StealthSpeed;
import com.volmit.adapt.util.C;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillStealth extends SimpleSkill {
    public SkillStealth() {
        super("stealth");
        setColor(C.DARK_GRAY);
        setBarColor(BarColor.WHITE);
        setBarStyle(BarStyle.SEGMENTED_20);
        setInterval(1400);
        setIcon(Material.WITHER_ROSE);
        setDescription("The art of the unseen. Walk in the shadows.");
        registerAdaptation(new StealthSpeed());
        registerAdaptation(new StealthSnatch());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers())
        {
            if(i.isSneaking() && !i.isSwimming() && !i.isSprinting() && !i.isFlying() && !i.isGliding())
            {
                xpSilent(i, 15.48);
            }
        }
    }
}
