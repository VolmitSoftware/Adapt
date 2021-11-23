package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.AgilitySuperJump;
import com.volmit.adapt.content.adaptation.AgilityWallJump;
import com.volmit.adapt.content.adaptation.AgilityWindUp;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;

public class SkillAgility extends SimpleSkill {
    public SkillAgility() {
        super("agility", "\u21C9");
        setDescription("Movement is futile, overcome obstacles");
        setColor(C.GREEN);
        setInterval(1100);
        setIcon(Material.FEATHER);
        registerAdaptation(new AgilityWindUp());
        registerAdaptation(new AgilityWallJump());
        registerAdaptation(new AgilitySuperJump());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking()) {
                xpSilent(i, 11.9);
            }
        }
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {

    }
}
