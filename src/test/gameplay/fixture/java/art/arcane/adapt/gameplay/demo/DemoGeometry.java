package art.arcane.adapt.gameplay.demo;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;

public final class DemoGeometry {
    private DemoGeometry() {
    }

    public static void fill(World world, Material material, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    public static void fenceRow(World world, Material material, int x1, int x2, int y, int z) {
        for (int x = x1; x <= x2; x++) {
            MultipleFacing data = (MultipleFacing) material.createBlockData();
            data.setFace(BlockFace.WEST, x > x1);
            data.setFace(BlockFace.EAST, x < x2);
            world.getBlockAt(x, y, z).setBlockData(data, false);
        }
    }

    public static void ladder(World world, int x, int y, int z, BlockFace facing) {
        BlockData data = Material.LADDER.createBlockData();
        ((Directional) data).setFacing(facing);
        world.getBlockAt(x, y, z).setBlockData(data, false);
    }
}
