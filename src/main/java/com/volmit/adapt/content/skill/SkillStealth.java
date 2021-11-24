package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.StealthSnatch;
import com.volmit.adapt.content.adaptation.StealthSpeed;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

public class SkillStealth extends SimpleSkill<SkillStealth.Config> {
    public SkillStealth() {
        super("stealth", "\u2720");
        setColor(C.DARK_GRAY);
        setInterval(1412);
        registerConfiguration(Config.class);
        setIcon(Material.WITHER_ROSE);
        setDescription("The art of the unseen. Walk in the shadows.");
        registerAdaptation(new StealthSpeed());
        registerAdaptation(new StealthSnatch());
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.LEATHER_LEGGINGS)
            .key("challenge_sneak_1k")
            .title("Knee Pain")
            .description("Sneak over a kilometer (1,000 blocks)")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sneak_1k").goal(1000).stat("move.sneak").reward(750).build());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSneaking() && !i.isSwimming() && !i.isSprinting() && !i.isFlying() && !i.isGliding()) {
                xpSilent(i, 15.48);
            }
        }
    }

    protected static class Config{}
}
