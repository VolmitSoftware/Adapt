package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.inventory.ItemStack;


public class ArchitectGlass extends SimpleAdaptation<ArchitectGlass.Config> {
    public ArchitectGlass() {
        super("architect-glass");
        registerConfiguration(ArchitectGlass.Config.class);
        setDescription(Adapt.dLocalize("Glass.Description"));
        setDisplayName(Adapt.dLocalize("Glass.Name"));
        setIcon(Material.GLASS);
        setInterval(9119);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Glass.Lore1"));
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockBreakEvent e) {
        if (hasAdaptation(e.getPlayer()) && (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.AIR) && !e.isCancelled()) {
            BlockCanBuildEvent can = new BlockCanBuildEvent(e.getBlock(), e.getPlayer(), e.getBlock().getBlockData(), true);
            Bukkit.getServer().getPluginManager().callEvent(can);

            if (!can.isBuildable()) {
                return;
            }

            if (e.getBlock().getType().toString().contains("GLASS") && !e.getBlock().getType().toString().contains("TINTED_GLASS")) {
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(e.getBlock().getType(), 1));
                e.getBlock().getWorld().playSound(e.getBlock().getLocation(), Sound.BLOCK_LARGE_AMETHYST_BUD_BREAK, 1.0f, 1.0f);
                e.getBlock().getWorld().spawnParticle(Particle.SCRAPE, e.getBlock().getLocation(), 1);
                J.a(() -> vfxSingleCubeOutline(e.getBlock(), Particle.REVERSE_PORTAL));
                e.getBlock().breakNaturally();
                xp(e.getPlayer(), 1);
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }


    @Override
    public void onTick() {
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 1;
        int initialCost = 0;
        double costFactor = 5;
    }
}
