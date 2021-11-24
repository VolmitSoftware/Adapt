package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class SkillArchitect extends SimpleSkill<SkillArchitect.Config> {
    public SkillArchitect() {
        super("architect", "\u2B27");
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription("Structures of reality are yours to control");
        setInterval(3700);
        setIcon(Material.IRON_BARS);
        registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.BRICK)
            .key("challenge_place_1k")
            .title("So much to build!")
            .description("Place over 1,000 blocks")
            .frame(AdvancementDisplay.AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_place_1k").goal(1000).stat("blocks.placed").reward(getConfig().challengePlace1k).build());
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        double v = getValue(e.getBlock()) * getConfig().xpValueMultiplier;
        J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), getConfig().xpBase + v)));
        getPlayer(e.getPlayer()).getData().addStat("blocks.placed", 1);
        getPlayer(e.getPlayer()).getData().addStat("blocks.placed.value", v);
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        getPlayer(e.getPlayer()).getData().addStat("blocks.broken", 1);
    }

    @Override
    public void onTick() {
        for(Player i : Bukkit.getOnlinePlayers()) {
            checkStatTrackers(getPlayer(i));
        }
    }

    protected static class Config { public Config(){}
        double challengePlace1k = 1750;
        double xpValueMultiplier = 1;
        double xpBase = 3;
    }
}
