package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class SkillArchitect extends SimpleSkill {
    public SkillArchitect() {
        super("architect", "\u2B27");
        setColor(C.AQUA);
        setDescription("Structures of reality are yours to control");
        setInterval(3700);
        setIcon(Material.IRON_BARS);
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        double v = getValue(e.getBlock());
        J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), 3 + v)));
        getPlayer(e.getPlayer()).getData().addStat("blocks.placed", 1);
        getPlayer(e.getPlayer()).getData().addStat("blocks.placed.value", v);
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        getPlayer(e.getPlayer()).getData().addStat("blocks.broken", 1);
    }

    @Override
    public void onTick() {

    }
}
