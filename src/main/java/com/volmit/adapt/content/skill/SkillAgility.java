package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.AgilitySuperJump;
import com.volmit.adapt.content.adaptation.AgilityWallJump;
import com.volmit.adapt.content.adaptation.AgilityWindUp;
import com.volmit.adapt.util.C;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillAgility extends SimpleSkill<SkillAgility.Config> {
    public SkillAgility() {
        super("agility", "\u21C9");
        registerConfiguration(Config.class);
        setDescription("Movement is futile, overcome obstacles");
        setColor(C.GREEN);
        setInterval(975);
        setIcon(Material.FEATHER);
        registerAdaptation(new AgilityWindUp());
        registerAdaptation(new AgilityWallJump());
        registerAdaptation(new AgilitySuperJump());
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.LEATHER_BOOTS)
            .key("challenge_move_1k")
            .title("Gotta Move!")
            .description("Walk over 1 Kilometer (1,000 blocks)")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.IRON_BOOTS)
                .key("challenge_sprint_5k")
                .title("Sprint a 5K!")
                .description("Sprint over 5,000 Blocks!")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .child(AdaptAdvancement.builder()
                .icon(Material.GOLDEN_BOOTS)
                .key("challenge_sprint_marathon")
                .title("Sprint a Marathon!")
                .description("Sprint over 42,195 Blocks!")
                .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_move_1k").goal(1000).stat("move").reward(getConfig().challengeMove1kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_5k").goal(5000).stat("move").reward(getConfig().challengeSprint5kReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_marathon").goal(42195).stat("move").reward(getConfig().challengeSprintMarathonReward).build());
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if(e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            double d = e.getFrom().distance(e.getTo());
            getPlayer(e.getPlayer()).getData().addStat("move", d);
            if(e.getPlayer().isSneaking()) {
                getPlayer(e.getPlayer()).getData().addStat("move.sneak", d);
            } else if(e.getPlayer().isFlying()) {
                getPlayer(e.getPlayer()).getData().addStat("move.fly", d);
            } else if(e.getPlayer().isSwimming()) {
                getPlayer(e.getPlayer()).getData().addStat("move.swim", d);
            } else if(e.getPlayer().isSprinting()) {
                getPlayer(e.getPlayer()).getData().addStat("move.sprint", d);
            }
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
            if(i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking()) {
                xpSilent(i, getConfig().sprintXpPassive);
            }
        }
    }

    protected static class Config {
        double challengeMove1kReward = 500;
        double challengeSprint5kReward = 2000;
        double challengeSprintMarathonReward = 6500;
        double sprintXpPassive = 11.9;
    }
}
