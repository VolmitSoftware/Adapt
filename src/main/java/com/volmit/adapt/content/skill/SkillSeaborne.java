package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.SeaborneOxygen;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
    public SkillSeaborne() {
        super("seaborne", "\uD83C\uDF0A");
        setColor(C.BLUE);
        setDescription("Will the wonders of the water");
        setInterval(2120);
        setIcon(Material.TRIDENT);
        registerAdaptation(new SeaborneOxygen());
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.TURTLE_HELMET)
            .key("challenge_swim_1nm")
            .title("Human Submarine!")
            .description("Swim 1 Nautical Mile (1,852 blocks)")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_1nm").goal(1852).stat("move.swim").reward(750).build());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSwimming() || i.getRemainingAir() < i.getMaximumAir()) {
                checkStatTrackers(getPlayer(i));
                xpSilent(i, 19.7);
            }
        }
    }

    protected static class Config{}
}
