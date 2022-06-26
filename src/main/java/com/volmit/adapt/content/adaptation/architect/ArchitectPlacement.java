package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KMap;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class ArchitectPlacement extends SimpleAdaptation<ArchitectPlacement.Config> {
    public ArchitectPlacement() {
        super("architect-placement");
        registerConfiguration(ArchitectPlacement.Config.class);
        setDescription("allows for you to place multiple blocks at once");
        setIcon(Material.LEAD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }


    private final KMap<Block, BlockFace> blockMap = new KMap<>();
    private final KMap<Player, KMap<Block, BlockFace>> totalMap = new KMap<>();

    private BlockFace getBlockFace(Player player) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 5);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        totalMap.remove(e.getPlayer());
    }

    @EventHandler
    public void on(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p) && p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock() && totalMap.get(p) != null) {
            KMap<Block, BlockFace> map = totalMap.get(p);
            for (Block b : map.keySet()) {
                BlockFace face = map.get(b);
                p.playSound(p.getLocation(), Sound.BLOCK_AZALEA_BREAK, 0.4f, 0.25f);
                b.getWorld().setBlockData(b.getRelative(face).getLocation(), b.getBlockData());

            }

        }
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (hasAdaptation(p) && !p.isSneaking()) {
            totalMap.clear();
            blockMap.clear();
        }

        if (hasAdaptation(p) && p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock()) {
            Block block = p.getTargetBlock(null, 5); // 5 is the range of player
            Material handMaterial = p.getInventory().getItemInMainHand().getType();
            if (handMaterial.isAir()) {
                return;
            }
            BlockFace viewPortBlock = getBlockFace(p);

            if (viewPortBlock != null && (viewPortBlock.getDirection().equals(BlockFace.NORTH.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.SOUTH.getDirection()))) { // North & South = X
                for (int x = block.getX() - 1; x <= block.getX() + 1; x++) { // 1 is the radius of the blocks
                    for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
                        if (handMaterial == block.getWorld().getBlockAt(x, y, block.getZ()).getType()) {
                            if (blockMap.size() <= getConfig().maxBlocks) {
                                blockMap.put(block.getWorld().getBlockAt(x, y, block.getZ()), viewPortBlock);

                            }
                        }
                    }
                }
                totalMap.put(p, blockMap);
            } else if (viewPortBlock != null && (viewPortBlock.getDirection().equals(BlockFace.EAST.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.WEST.getDirection()))) { // East & West = Z
                for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
                    for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
                        if (handMaterial == block.getWorld().getBlockAt(block.getX(), y, z).getType()) {
                            if (blockMap.size() <= getConfig().maxBlocks) {
                                blockMap.put(block.getWorld().getBlockAt(block.getX(), y, z), viewPortBlock);
                            }
                        }
                    }
                }
                totalMap.put(p, blockMap);
            } else if (viewPortBlock != null && (viewPortBlock.getDirection().equals(BlockFace.UP.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.DOWN.getDirection()))) { // Up & Down = Y
                for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
                    for (int x = block.getX() - 1; x <= block.getX() + 1; x++) {
                        if (handMaterial == block.getWorld().getBlockAt(x, block.getY(), z).getType()) {
                            if (blockMap.size() <= getConfig().maxBlocks) {
                                blockMap.put(block.getWorld().getBlockAt(x, block.getY(), z), viewPortBlock);
                            }
                        }
                    }
                }
                totalMap.put(p, blockMap);
            }
        }
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
        if (!totalMap.isEmpty()) {
            J.a(() -> {
                for (Player p : totalMap.keySet()) { // Get every player that has a map
                    if (!hasAdaptation(p) || !p.isSneaking()) {
                        totalMap.clear();
                        return;
                    }
                    KMap<Block, BlockFace> blockRender = totalMap.get(p);
                    for (Block b : blockRender.keySet()) { // Get the blocks in that map that bind with a BlockFace
                        BlockFace bf = blockRender.get(b); // Get that blockface
                        Block transposedBlock = b.getRelative(bf);
                        Location point0 = transposedBlock.getLocation(); //bottom left corner of the bloc
                        Location point1 = new Location(point0.getWorld(), point0.getX() + 1, point0.getY(), point0.getZ());
                        Location point2 = new Location(point0.getWorld(), point0.getX(), point0.getY() + 1, point0.getZ());
                        Location point3 = new Location(point0.getWorld(), point0.getX(), point0.getY(), point0.getZ() + 1);
                        Location point4 = new Location(point0.getWorld(), point0.getX() + 1, point0.getY() + 1, point0.getZ());
                        Location point5 = new Location(point0.getWorld(), point0.getX() + 1, point0.getY(), point0.getZ() + 1);
                        Location point6 = new Location(point0.getWorld(), point0.getX(), point0.getY() + 1, point0.getZ() + 1);
                        Location point7 = new Location(point0.getWorld(), point0.getX() + 1, point0.getY() + 1, point0.getZ() + 1);

                        particleLine(point0, point1, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point0, point2, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point0, point3, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point7, point6, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point7, point5, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point7, point4, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point5, point1, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point5, point3, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point6, point2, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point6, point3, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point4, point2, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                        particleLine(point4, point1, Particle.REVERSE_PORTAL, 9, 2, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
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
        double costFactor = 2.;
        int baseRange = 2;
    }
}
