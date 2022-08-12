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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchitectPlacement extends SimpleAdaptation<ArchitectPlacement.Config> {
    public ArchitectPlacement() {
        super("architect-placement");
        registerConfiguration(ArchitectPlacement.Config.class);
        setDescription(Adapt.dLocalize("Architect", "Placement", "Description"));
        setDisplayName(Adapt.dLocalize("Architect", "Placement", "Name"));
        setIcon(Material.SCAFFOLDING);
        setInterval(350);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Architect", "Placement", "Lore3"));
    }

    private final Map<Player, Map<Block, BlockFace>> totalMap = new HashMap<>();

    private BlockFace getBlockFace(Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 5);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void on(PlayerQuitEvent e) {
        totalMap.remove(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockPlaceEvent e) {
        Player p = e.getPlayer();

        if (hasAdaptation(p) && !totalMap.isEmpty() && totalMap.get(p) != null && totalMap.get(p).size() > 0) {
            BlockCanBuildEvent can = new BlockCanBuildEvent(e.getBlock(), e.getPlayer(), e.getBlock().getBlockData(), true);
            Bukkit.getServer().getPluginManager().callEvent(can);
            if (!can.isBuildable()) {
                return;
            }


            ItemStack is = p.getInventory().getItemInMainHand().clone();
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (p.isSneaking() && is.getType().isBlock()) {
                double v = getValue(e.getBlock());
                int handSizeAfter = is.getAmount() - totalMap.get(p).size();
                if (handSizeAfter >= 0) {
                    for (Block b : totalMap.get(p).keySet()) { // Block Placer
                        BlockFace face = totalMap.get(p).get(b);
                        if (b.getWorld().getBlockAt(b.getRelative(face).getLocation()).getType() == Material.AIR) {
                            if (b.getRelative(face).getLocation() != e.getBlock().getLocation()) {
                                b.getWorld().setBlockData(b.getRelative(face).getLocation(), b.getBlockData());
                                getPlayer(e.getPlayer()).getData().addStat("blocks.placed", 1);
                                getPlayer(e.getPlayer()).getData().addStat("blocks.placed.value", v);
                                p.playSound(b.getLocation(), Sound.BLOCK_AZALEA_BREAK, 0.4f, 0.25f);
                                xp(e.getPlayer(), 2);
                            }
                        }
                        is.setAmount(is.getAmount() - 1);
                        hand.setAmount(is.getAmount());
                    }
                    totalMap.remove(p);
                    if (hand.getAmount() > 0) {
                        runPlayerViewport(getBlockFace(p), p.getTargetBlock(null, 5), p.getInventory().getItemInMainHand().getType(), p);
                    }
                } else {
                    p.sendMessage(C.RED + Adapt.dLocalize("Adaptations", "Placement", "Lore2") + C.GREEN + totalMap.get(p).size() + C.RED + Adapt.dLocalize("Architect", "Placement", "Lore2"));
                }
            }
        }
    }


    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p) && p.isSneaking()) {
            totalMap.remove(p);
        }

        if (hasAdaptation(p) && !p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock()) {
            Block block = p.getTargetBlock(null, 5); // 5 is the range of player
            Material handMaterial = p.getInventory().getItemInMainHand().getType();
            if (handMaterial.isAir()) {
                return;
            }
            BlockFace viewPortBlock = getBlockFace(p);
            runPlayerViewport(viewPortBlock, block, handMaterial, p);
        }
    }


    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p) && !p.isSneaking()) {
            totalMap.remove(p);
        }

        if (hasAdaptation(p) && p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock()) {
            Block block = p.getTargetBlock(null, 5); // 5 is the range of player
            Material handMaterial = p.getInventory().getItemInMainHand().getType();
            if (handMaterial.isAir()) {
                return;
            }
            BlockFace viewPortBlock = getBlockFace(p);
            runPlayerViewport(viewPortBlock, block, handMaterial, p);

        }
    }

    public void runPlayerViewport(BlockFace viewPortBlock, Block block, Material handMaterial, Player p) {
        if (viewPortBlock != null && (viewPortBlock.getDirection().equals(BlockFace.NORTH.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.SOUTH.getDirection()))) { // North & South = X
            for (int x = block.getX() - 1; x <= block.getX() + 1; x++) { // 1 is the radius of the blocks
                for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
                    if (handMaterial == block.getWorld().getBlockAt(x, y, block.getZ()).getType()) {
                        if (totalMap.get(p) == null) {
                            Map<Block, BlockFace> map = new HashMap<>();
                            map.put(block.getWorld().getBlockAt(x, y, block.getZ()), viewPortBlock);
                            totalMap.put(p, map);
                        } else if (totalMap.get(p).size() <= getConfig().maxBlocks) {
                            totalMap.get(p).put(block.getWorld().getBlockAt(x, y, block.getZ()), viewPortBlock);
                        }
                    }
                }
            }
        } else if (viewPortBlock != null && (viewPortBlock.getDirection().equals(BlockFace.EAST.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.WEST.getDirection()))) { // East & West = Z
            for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
                for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
                    if (handMaterial == block.getWorld().getBlockAt(block.getX(), y, z).getType()) {
                        if (totalMap.get(p) == null) {
                            Map<Block, BlockFace> map = new HashMap<>();
                            map.put(block.getWorld().getBlockAt(block.getX(), y, z), viewPortBlock);
                            totalMap.put(p, map);
                        } else if (totalMap.get(p).size() <= getConfig().maxBlocks) {
                            totalMap.get(p).put(block.getWorld().getBlockAt(block.getX(), y, z), viewPortBlock);
                        }
                    }
                }
            }
        } else if (viewPortBlock != null && (viewPortBlock.getDirection().equals(BlockFace.UP.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.DOWN.getDirection()))) { // Up & Down = Y
            for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
                for (int x = block.getX() - 1; x <= block.getX() + 1; x++) {
                    if (handMaterial == block.getWorld().getBlockAt(x, block.getY(), z).getType()) {
                        if (totalMap.get(p) == null) {
                            Map<Block, BlockFace> map = new HashMap<>();
                            map.put(block.getWorld().getBlockAt(x, block.getY(), z), viewPortBlock);
                            totalMap.put(p, map);
                        } else if (totalMap.get(p).size() <= getConfig().maxBlocks) {
                            totalMap.get(p).put(block.getWorld().getBlockAt(x, block.getY(), z), viewPortBlock);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }


    @Override
    public void onTick() {
        if (!totalMap.isEmpty()) {
            J.a(() -> {
                for (Player p : totalMap.keySet()) { // Get every player that has a map
                    if (!hasAdaptation(p) || !p.isSneaking()) {
                        totalMap.clear();
                        return;
                    }
                    Map<Block, BlockFace> blockRender = totalMap.get(p);
                    for (Block b : blockRender.keySet()) { // Get the blocks in that map that bind with a BlockFace
                        BlockFace bf = blockRender.get(b); // Get that blockface
                        Block transposedBlock = b.getRelative(bf);
                        vfxSingleCubeOutline(transposedBlock, Particle.REVERSE_PORTAL);
                    }
                }
            });
        }
    }

    @NoArgsConstructor
    protected static class Config {
        public int maxBlocks = 20;
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 1;
        int initialCost = 4;
        double costFactor = 2;
    }
}
