package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.HerbalismGrowthAura;
import com.volmit.adapt.content.adaptation.HerbalismHungryShield;
import com.volmit.adapt.content.adaptation.HerbalismReplant;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Levelled;
import org.bukkit.boss.BarColor;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

public class SkillHerbalism extends SimpleSkill {
    public SkillHerbalism() {
        super("herbalism", "\u2698");
        setColor(C.GREEN);
        setInterval(3700);
        setDescription("Sticks and stones don't break your bones");
        setIcon(Material.WHEAT);
        registerAdaptation(new HerbalismGrowthAura());
        registerAdaptation(new HerbalismReplant());
        registerAdaptation(new HerbalismHungryShield());
    }

    @EventHandler
    public void on(PlayerItemConsumeEvent e) {
        xp(e.getPlayer(), 125);
    }

    @EventHandler
    public void on(PlayerShearEntityEvent e) {
        xp(e.getPlayer(), e.getEntity().getLocation(), 95);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerHarvestBlockEvent e) {
        if(e.isCancelled()) {
            return;
        }

        if(e.getHarvestedBlock().getBlockData() instanceof Ageable) {
            xp(e.getPlayer(),e.getHarvestedBlock().getLocation().clone().add(0.5, 0.5, 0.5), 32 * (((Ageable) e.getHarvestedBlock().getBlockData()).getAge()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPlaceEvent e) {
        if(e.getBlock().getBlockData() instanceof Ageable) {
            xp(e.getPlayer(),e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), 3);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        if(e.useItemInHand().equals(Event.Result.DENY)) {
            return;
        }

        if(e.getClickedBlock() == null) {
            return;
        }

        if(e.getClickedBlock().getType().equals(Material.COMPOSTER)) {
            Levelled c = ((Levelled) e.getClickedBlock().getBlockData());
            int ol = c.getLevel();

            J.s(() -> {
                int nl = ((Levelled) e.getClickedBlock().getBlockData()).getLevel();
                if(nl > ol || (ol > 0 && nl == 0)) {
                    xp(e.getPlayer(),e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5), 51 + (nl * 3) + (nl == 0 ? 250 : 5));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent e) {
        if(e.getBlock().getType().equals(Material.CACTUS)) {
            return;
        }

        if(e.getBlock().getBlockData() instanceof Ageable) {
            xp(e.getPlayer(),e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), 32 * (((Ageable) e.getBlock().getBlockData()).getAge()));
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {

    }
}
