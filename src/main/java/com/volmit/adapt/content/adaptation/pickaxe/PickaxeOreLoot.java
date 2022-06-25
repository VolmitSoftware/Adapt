package com.volmit.adapt.content.adaptation.pickaxe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;

public class PickaxeOreLoot extends SimpleAdaptation<PickaxeOreLoot.Config> {
    public PickaxeOreLoot() {
        super("pickaxes-ore-loot");
        registerConfiguration(PickaxeOreLoot.Config.class);
        setDescription("Allows you to break blocks in a Vein/Cluster of ores");
        setIcon(Material.IRON_PICKAXE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private int getRadius(int lvl) {
        return lvl + getConfig().baseRange;
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if(!hasAdaptation(p)){
            return;
        }
        Block block = e.getBlock();
        Map<Location, Block> blockMap  = new HashMap<>();
        blockMap.put( block.getLocation(), block);

        for (int i = 0; i < getRadius(getLevel(p)); i++) {
            for (int x = -i; x <= i; x++) {
                for (int y = -i; y <= i; y++) {
                    for (int z = -i; z <= i; z++) {
                        Block b = block.getRelative(x, y, z);
                        if(b.getType() == block.getType()){
                            blockMap.put(b.getLocation(), b);
                        }
                    }
                }
            }
        }
        J.s(() -> {
            for (Location l : blockMap.keySet()) {
                Block b = e.getBlock().getWorld().getBlockAt(l);
                    b.breakNaturally();
                    e.getBlock().getWorld().playSound(e.getBlock().getLocation(), Sound.BLOCK_FUNGUS_BREAK, 0.4f, 0.25f);
                    e.getBlock().getWorld().spawnParticle(Particle.ASH, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.1);
            }
        });




    }




    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {

    }

    @Override
    public void onTick() {
    }


    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 4;
        int initialCost = 4;
        double costFactor = 2.325;
        int baseRange = 2;
    }
}
