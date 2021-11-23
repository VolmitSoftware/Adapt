package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.AgilitySuperJump;
import com.volmit.adapt.content.adaptation.AgilityWallJump;
import com.volmit.adapt.content.adaptation.AgilityWindUp;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

public class SkillAgility extends SimpleSkill {
    public SkillAgility() {
        super("agility", "\u21C9");
        setDescription("Movement is futile, overcome obstacles");
        setColor(C.GREEN);
        setInterval(975);
        setIcon(Material.FEATHER);
        registerAdaptation(new AgilityWindUp());
        registerAdaptation(new AgilityWallJump());
        registerAdaptation(new AgilitySuperJump());
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.LEATHER_BOOTS)
            .key("challenge_walk_1k")
            .title("Walk a walk!")
            .description("Walk over 1 Kilometer (1,000 blocks)")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build());
    }

    @EventHandler
    public void on(PlayerMoveEvent e)
    {
        if(e.getFrom().getWorld().equals(e.getTo().getWorld()))
        {
            double d = e.getFrom().distance(e.getTo());
            getPlayer(e.getPlayer()).getData().addStat("move", d);
            if(e.getPlayer().isSneaking())
            {
                getPlayer(e.getPlayer()).getData().addStat("move.sneak", d);
            }

            else if(e.getPlayer().isFlying())
            {
                getPlayer(e.getPlayer()).getData().addStat("move.fly", d);
            }

            else if(e.getPlayer().isSwimming())
            {
                getPlayer(e.getPlayer()).getData().addStat("move.swim", d);
            }

            else if(e.getPlayer().isSprinting())
            {
                getPlayer(e.getPlayer()).getData().addStat("move.sprint", d);
            }
        }
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            if(i.isSprinting() && !i.isFlying() && !i.isSwimming() && !i.isSneaking()) {
                xpSilent(i, 11.9);
            }
        }
    }
}
