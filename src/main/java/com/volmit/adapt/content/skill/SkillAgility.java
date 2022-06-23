package com.volmit.adapt.content.skill;


import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.agility.AgilitySuperJump;
import com.volmit.adapt.content.adaptation.agility.AgilityWallJump;
import com.volmit.adapt.content.adaptation.agility.AgilityWindUp;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
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
//        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_move_1k").goal(1000).stat("move").reward(getConfig().challengeMove1kReward).build());
//        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_5k").goal(5000).stat("move").reward(getConfig().challengeSprint5kReward).build());
//        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_sprint_marathon").goal(42195).stat("move").reward(getConfig().challengeSprintMarathonReward).build());
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            double d = e.getFrom().distance(e.getTo());
            getPlayer(e.getPlayer()).getData().addStat("move", d);
            if (e.getPlayer().isSneaking()) {
                getPlayer(e.getPlayer()).getData().addStat("move.sneak", d);
            } else if (e.getPlayer().isFlying()) {
                getPlayer(e.getPlayer()).getData().addStat("move.fly", d);
            } else if (e.getPlayer().isSwimming()) {
                getPlayer(e.getPlayer()).getData().addStat("move.swim", d);
            } else if (e.getPlayer().isSprinting()) {
                getPlayer(e.getPlayer()).getData().addStat("move.sprint", d);
            }
        }
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
            if (i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking()) {
                xpSilent(i, getConfig().sprintXpPassive);
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
//        double challengeMove1kReward = 500;
//        double challengeSprint5kReward = 2000;
//        double challengeSprintMarathonReward = 6500;
        double sprintXpPassive = 1;
    }
}
