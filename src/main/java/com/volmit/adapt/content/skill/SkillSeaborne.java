package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.SeaborneOxygen;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

public class SkillSeaborne extends SimpleSkill {
    public SkillSeaborne() {
        super("seaborne", "\uD83C\uDF0A");
        setColor(C.BLUE);
        setDescription("Will the wonders of the water");
        setInterval(2120);
        setIcon(Material.TRIDENT);
        registerAdaptation(new SeaborneOxygen());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSwimming() || i.getRemainingAir() < i.getMaximumAir()) {
                xpSilent(i, 19.7);
            }
        }
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {

    }
}
