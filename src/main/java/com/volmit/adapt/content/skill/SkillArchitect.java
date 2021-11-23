package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.event.EventHandler;
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
        J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), 3 + getValue(e.getBlock()))));
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {

    }
}
