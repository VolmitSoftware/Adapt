package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.seaborrne.SeaborneOxygen;
import com.volmit.adapt.content.adaptation.seaborrne.SeaborneSpeed;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
    public SkillSeaborne() {
        super("seaborne", Adapt.dLocalize("Skill", "Seaborne", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.BLUE);
        setDescription(Adapt.dLocalize("Skill", "Seaborne", "Description"));
        setInterval(2120);
        setIcon(Material.TRIDENT);
        registerAdaptation(new SeaborneOxygen());
        registerAdaptation(new SeaborneSpeed());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.TURTLE_HELMET)
                .key("challenge_swim_1nm")
                .title("Human Submarine!")
                .description("Swim 1 Nautical Mile (1,852 blocks)")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_swim_1nm").goal(1852).stat("move.swim").reward(getConfig().challengeSwim1nmReward).build());
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (i.isSwimming() || i.getRemainingAir() < i.getMaximumAir()) {
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
