package com.volmit.adapt.content.skill;


import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.stealth.StealthSnatch;
import com.volmit.adapt.content.adaptation.stealth.StealthSpeed;
import com.volmit.adapt.content.adaptation.stealth.StealthVision;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;

public class SkillStealth extends SimpleSkill<SkillStealth.Config> {
    public SkillStealth() {
        super("stealth", "\u2720");
        registerConfiguration(Config.class);
        setColor(C.DARK_GRAY);
        setInterval(1412);
        setIcon(Material.WITHER_ROSE);
        setDescription("The art of the unseen. Walk in the shadows.");
        registerAdaptation(new StealthSpeed());
        registerAdaptation(new StealthSnatch());
        registerAdaptation(new StealthVision());

//        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sneak_1k").goal(1000).stat("move.sneak").reward(getConfig().challengeSneak1kReward).build());
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSneaking() && !i.isSwimming() && !i.isSprinting() && !i.isFlying() && !i.isGliding()) {
                xpSilent(i, getConfig().sneakXP);
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
//        double challengeSneak1kReward = 750;
        double sneakXP = 15.48;
    }
}
