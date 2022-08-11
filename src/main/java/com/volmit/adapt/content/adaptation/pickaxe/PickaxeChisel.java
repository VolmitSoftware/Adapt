package com.volmit.adapt.content.adaptation.pickaxe;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxeChisel extends SimpleAdaptation<PickaxeChisel.Config> {
    public PickaxeChisel() {
        super("pickaxes-chisel");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Chisel.Description"));
        setDisplayName(Adapt.dLocalize("Chisel.Name"));
        setIcon(Material.IRON_NUGGET);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(8276);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDropChance(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("Chisel.Lore1"));
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + Adapt.dLocalize("Chisel.Lore2"));
    }

    private int getCooldownTime(double levelPercent) {
        return getConfig().cooldownTime;
    }

    private double getDropChance(double levelPercent) {
        return ((levelPercent) * getConfig().dropChanceFactor) + getConfig().dropChanceBase;
    }

    private double getBreakChance(double levelPercent) {
        return getConfig().breakChance;
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (getConfig().damagePerBlockBase + (getConfig().damageFactorInverseMultiplier * ((1D - levelPercent))));
    }


    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isPickaxe(e.getPlayer().getInventory().getItemInMainHand()) && getLevel(e.getPlayer()) > 0) {
            if (e.getPlayer().getCooldown(e.getPlayer().getInventory().getItemInMainHand().getType()) > 0) {
                return;
            }

            BlockCanBuildEvent can = new BlockCanBuildEvent(e.getClickedBlock(), e.getPlayer(), e.getClickedBlock().getBlockData(), true);
            Bukkit.getServer().getPluginManager().callEvent(can);

            if (can.isBuildable()) {
                xp(e.getPlayer(), 3);
                BlockData b = e.getClickedBlock().getBlockData();
                if (isOre(b)) {
                    e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 1.4f);
                    e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_METAL_HIT, 1.25f, 1.7f);

                    getSkill().xp(e.getPlayer(), 37);
                    e.getPlayer().setCooldown(e.getPlayer().getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(e.getPlayer())));
                    damageHand(e.getPlayer(), getDamagePerBlock(getLevelPercent(e.getPlayer())));

                    Location c = e.getPlayer().rayTraceBlocks(8).getHitPosition().toLocation(e.getPlayer().getWorld());

                    ItemStack is = getDropFor(b);
                    if (M.r(getDropChance(getLevelPercent(e.getPlayer())))) {
                        e.getClickedBlock().getWorld().spawnParticle(Particle.ITEM_CRACK, c, 14, 0.10, 0.01, 0.01, 0.1, is);
                        e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.25f, 0.787f);
                        e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.55f, 1.89f);
                        e.getClickedBlock().getWorld().dropItemNaturally(c.clone().subtract(e.getPlayer().getLocation().getDirection().clone().multiply(0.1)), is);
                    } else {
                        e.getClickedBlock().getWorld().spawnParticle(Particle.ITEM_CRACK, c, 3, 0.01, 0.01, 0.01, 0.1, is);

                        e.getClickedBlock().getWorld().spawnParticle(Particle.BLOCK_CRACK, c, 9, 0.1, 0.1, 0.1, e.getClickedBlock().getBlockData());
                    }

                    if (M.r(getBreakChance(getLevelPercent(e.getPlayer())))) {
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
        return switch (b.getMaterial()) {
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

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 7;
        int initialCost = 5;
        double costFactor = 0.4;
        int cooldownTime = 5;
        double dropChanceBase = 0.07;
        double dropChanceFactor = 0.22;
        double breakChance = 0.25;
        double damagePerBlockBase = 1;
        double damageFactorInverseMultiplier = 2;
    }
}
