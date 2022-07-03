package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ArchitectFoundation extends SimpleAdaptation<ArchitectFoundation.Config> {
    public ArchitectFoundation() {
        super("architect-foundation");
        registerConfiguration(ArchitectFoundation.Config.class);
        setDescription("This allows for you to sneak near the edge of a block and place a temporary foundation");
        setDisplayName("Architect's Magic Foundation");
        setIcon(Material.TINTED_GLASS);
        setInterval(500);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private final KMap<Player, KMap<Block, Long>> blockList = new KMap<>();
    private final KMap<Player, Integer> blockCount = new KMap<>();
    private final KList<Block> blockMap = new KList<>();

    private final KList<Player> cooldown = new KList<>();


    @EventHandler
    public void on(PlayerQuitEvent e) {
        blockList.remove(e.getPlayer());
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        for (Block b : blockMap) {
            if (e.getBlock().getLocation().equals(b.getLocation())) {
                e.setCancelled(true);
                e.getBlock().getWorld().playSound(e.getBlock().getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 1, 1);
            }
        }
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p)) {
            if (cooldown.contains(p)) {
                return;
            }
            if (blockCount.get(p) != null && blockCount.get(p) >= getConfig().maxBlocks) {
                startCooldown(p);
                return;
            }
            if (!p.isSneaking() && blockCount.get(p) != null) {
                startCooldown(p);
                return;
            }
            if (e.getPlayer().isSneaking()) {
                KSet<Block> locs = new KSet<>();
                locs.add(p.getLocation().getWorld().getBlockAt(p.getLocation().clone().add(0.3, -1, -0.3)));
                locs.add(p.getLocation().getWorld().getBlockAt(p.getLocation().clone().add(-0.3, -1, -0.3)));
                locs.add(p.getLocation().getWorld().getBlockAt(p.getLocation().clone().add(0.3, -1, 0.3)));
                locs.add(p.getLocation().getWorld().getBlockAt(p.getLocation().clone().add(-0.3, -1, +0.3)));
                for (Block b : locs) {
                    if (b.getType() == Material.AIR) {
                        createFakeGlass(b, e.getPlayer());
                    }
                }
            }
        }
    }

    private void createFakeGlass(Block b, Player p) {
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.0f, 1.0f);
        vfxSingleCubeOutline(b, Particle.REVERSE_PORTAL);
        vfxSingleCubeOutline(b, Particle.ASH);
        b.setType(Material.TINTED_GLASS);
        blockMap.add(b);
        if (blockList.get(p) == null) {
            KMap<Block, Long> map = new KMap<>();
            map.put(b, System.currentTimeMillis());
            blockList.put(p, map);
        } else {
            blockList.get(p).put(b, System.currentTimeMillis());
        }
        if (blockCount.get(p) == null) {
            blockCount.put(p, 0);
        } else {
            blockCount.put(p, blockCount.get(p) + 1);
        }
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {

    }

    private void startCooldown(Player p) {
        if (!cooldown.contains(p)) {
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 100.0f, 10.0f);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SCULK_CATALYST_BREAK, 100.0f, 0.81f);


            cooldown.add(p);
            blockCount.remove(p);
            J.a(() -> {
                try {
                    Thread.sleep(getConfig().duration + 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cooldown.remove(p);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 100.0f, 10.0f);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 100.0f, 0.81f);

            });
        }
    }

    @Override
    public void onTick() {
        for (Player p : blockList.keySet()) {
            for (Block b : blockList.get(p).keySet()) {
                if (System.currentTimeMillis() - blockList.get(p).get(b) > getConfig().duration) {
                    J.a(() -> {
                        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 1.0f);
                        vfxSingleCubeOutline(b, Particle.ENCHANTMENT_TABLE);

                    });
                    J.s(() -> {
                        b.setType(Material.AIR);
                        blockList.get(p).remove(b);
                        blockMap.remove(b);
                    });
                }
            }
        }
    }

    @NoArgsConstructor
    protected static class Config {
        public long duration = 3000;
        public int maxBlocks = 10;
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 1;
        int initialCost = 4;
        double costFactor = 2;
    }
}
