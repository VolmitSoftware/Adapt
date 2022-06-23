package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.seaborrne.SeaborneOxygen;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
    public SkillSeaborne() {
        super("seaborne", "\uD83C\uDF0A");
        registerConfiguration(Config.class);
        setColor(C.BLUE);
        setDescription("Will the wonders of the water");
        setInterval(2120);
        setIcon(Material.TRIDENT);
        registerAdaptation(new SeaborneOxygen());

        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_1nm").goal(1852).stat("move.swim").reward(getConfig().challengeSwim1nmReward).build());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSwimming() || i.getRemainingAir() < i.getMaximumAir()) {
                checkStatTrackers(getPlayer(i));
                xpSilent(i, getConfig().swimXP);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double challengeSwim1nmReward = 750;
        double swimXP = 19.7;
    }
}
