package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxesChisel extends SimpleAdaptation {
    public PickaxesChisel() {
        super("chisel");
        setDescription("Right Click Ores to Chisel more ore out of them, at a severe durability cost.");
        setIcon(Material.IRON_NUGGET);
        setBaseCost(6);
        setMaxLevel(7);
        setInitialCost(5);
        setInterval(8276);
        setCostFactor(0.4);
    }

    private int getCooldownTime(double levelPercent) {
        return 5;
    }

    private double getDropChance(double levelPercent) {
        return ((levelPercent) * 0.22) + 0.07;
    }

    private double getBreakChance(double levelPercent) {
        return 0.25;
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (1 + (4 * ((1D - levelPercent)))) + 2;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDropChance(getLevelPercent(level)), 0) + C.GRAY + " Chance to Drop");
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " Tool Wear");
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(e.getClickedBlock() != null && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isPickaxe(e.getPlayer().getInventory().getItemInMainHand()) && getLevel(e.getPlayer()) > 0) {
            if(e.getPlayer().getCooldown(e.getPlayer().getInventory().getItemInMainHand().getType()) > 0) {
                return;
            }

            BlockCanBuildEvent can = new BlockCanBuildEvent(e.getClickedBlock(), e.getPlayer(), e.getClickedBlock().getBlockData(), true);
            Bukkit.getServer().getPluginManager().callEvent(can);

            if(can.isBuildable()) {
                BlockData b = e.getClickedBlock().getBlockData();
                if(isOre(b)) {
                    e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 1.4f);
                    e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_METAL_HIT, 1.25f, 1.7f);

                    getSkill().xp(e.getPlayer(), 37);
                    e.getPlayer().setCooldown(e.getPlayer().getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(e.getPlayer())));
                    damageHand(e.getPlayer(), getDamagePerBlock(getLevelPercent(e.getPlayer())));

                    Location c = e.getPlayer().rayTraceBlocks(8).getHitPosition().toLocation(e.getPlayer().getWorld());

                    ItemStack is = getDropFor(b);
                    if(M.r(getDropChance(getLevelPercent(e.getPlayer())))) {
                        e.getClickedBlock().getWorld().spawnParticle(Particle.ITEM_CRACK, c, 14, 0.10, 0.01, 0.01, 0.1, is);
                        e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.787f);
                        e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.55f, 1.89f);
                        e.getClickedBlock().getWorld().dropItemNaturally(c.clone().subtract(e.getPlayer().getLocation().getDirection().clone().multiply(0.1)), is);
                    } else {
                        e.getClickedBlock().getWorld().spawnParticle(Particle.ITEM_CRACK, c, 3, 0.01, 0.01, 0.01, 0.1, is);

                        e.getClickedBlock().getWorld().spawnParticle(Particle.BLOCK_CRACK, c, 9, 0.1, 0.1, 0.1, e.getClickedBlock().getBlockData());
                    }

                    if(M.r(getBreakChance(getLevelPercent(e.getPlayer())))) {
                        e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_BASALT_BREAK, 1.25f, 0.4f);
                        e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.887f);

                        e.getClickedBlock().breakNaturally(e.getPlayer().getInventory().getItemInMainHand());
                    }
                }
            } else {
                Adapt.verbose("Cancelled (Region?)");
            }
        }
    }

    private ItemStack getDropFor(BlockData b) {
        return switch(b.getMaterial()) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.RAW_COPPER);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.RAW_GOLD);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.RAW_IRON);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new ItemStack(Material.EMERALD);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ);
            case REDSTONE_ORE -> new ItemStack(Material.REDSTONE);

            default -> new ItemStack(Material.AIR);
        };
    }

    @Override
    public void onTick() {

    }
}
