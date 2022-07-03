package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.block.ScaffoldMatter;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private final KMap<Player, Integer> blockCount = new KMap<>(); //TODO: remove
    private final KList<Block> blockMap = new KList<>();

    private final KList<Player> cooldown = new KList<>();

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "Magically create " + 10 + (level * getConfig().blocksPerLevel) + C.GRAY + " Total Blocks beneath you!");

    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        clearBlocklist(e.getPlayer(), false);
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
            if (blockCount.get(p) != null && blockCount.get(p) >= (getConfig().startingBlocks + (getLevel(p) * getConfig().blocksPerLevel))) {
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
            map.put(b, M.ms());
            blockList.put(p, map);
        } else {
            blockList.get(p).put(b, M.ms());
        }
        if (blockCount.get(p) == null) {
            blockCount.put(p, 0);
        } else {
            blockCount.put(p, blockCount.get(p) + 1);
        }
//        J.a(() -> getMantle().set(b, ScaffoldMatter.ScaffoldData.builder()
//                .time(M.ms())
//                .uuid(p.getUniqueId())
//                .build()));
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
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

    private void clearBlocklist(Player p, Boolean timeCheck) {
        for (Block b : blockList.get(p).keySet()) {
            if (timeCheck) {
                if (M.ms() - blockList.get(p).get(b) > getConfig().duration) {
                    b.getWorld().playSound(b.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 1.0f);
                    vfxSingleCubeOutline(b, Particle.ENCHANTMENT_TABLE);

                    J.s(() -> {
                        b.setType(Material.AIR);
                        blockList.get(p).remove(b);
                        blockMap.remove(b);
                    });
                }
            } else {
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 1.0f);
                vfxSingleCubeOutline(b, Particle.ENCHANTMENT_TABLE);
                J.s(() -> {
                    b.setType(Material.AIR);
                    blockList.get(p).remove(b);
                    blockMap.remove(b);
                });
            }
        }
    }

    private void removeFakeBlock(Block b) {

    }

    @Override
    public void onTick() {
        for (Player p : blockList.keySet()) {
            clearBlocklist(p, true);
        }
    }


//    Block b = e.getBlock();
//
//
//    ScaffoldMatter.ScaffoldData read = getServer().getMantle().get(b, ScaffoldMatter.ScaffoldData.class);
//
//    getServer().getMantle().iterate(e.getBlock().getChunk(), ScaffoldMatter.ScaffoldData.class, (x,y,z,t) -> {
//        Block block = e.getBlock().getWorld().getBlockAt(x,y,z);
//
//        if(block.getType().equals(Material.GLASS)) {
//            if(M.ms() - t.getTime() > 3000) {
//                getServer().getMantle().set(b, (ScaffoldMatter.ScaffoldData) null);
//                /// DO YOUR CODE THAT REMOVES THE BLOCK
//            }
//        }
//
//        else {
//            getServer().getMantle().set(b, (ScaffoldMatter.ScaffoldData) null);
//        }
//    });
//


    @NoArgsConstructor
    protected static class Config {
        public long duration = 3000;
        public int startingBlocks = 10;
        public int blocksPerLevel = 5;
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 5;
        int initialCost = 4;
        double costFactor = 2;
    }
}
