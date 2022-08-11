package com.volmit.adapt.content.adaptation.pickaxe;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class PickaxeAutosmelt extends SimpleAdaptation<PickaxeAutosmelt.Config> {
    public PickaxeAutosmelt() {
        super("pickaxe-autosmelt");
        registerConfiguration(PickaxeAutosmelt.Config.class);
        setDescription(Adapt.dLocalize("Autosmelt.Description"));
        setDisplayName(Adapt.dLocalize("Autosmelt.Name"));
        setIcon(Material.RAW_GOLD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Adapt.dLocalize("Autosmelt.Lore1"));
        v.addLore(C.GREEN + "" + (level * 1.25) + C.GRAY + Adapt.dLocalize("Autosmelt.Lore2"));
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (!e.getBlock().getBlockData().getMaterial().name().endsWith("_ORE") && !ItemListings.getSmeltOre().contains(e.getBlock().getType())) {
            return;
        }
        xp(e.getPlayer(), 15);
        autosmeltBlock(e.getBlock(), p);
    }

    static void autosmeltBlock(Block b, Player p) {
        int fortune = 1;
        Random random = new Random();
        if (p.getInventory().getItemInMainHand().getEnchantments().get(Enchantment.LOOT_BONUS_BLOCKS) != null) {
            fortune = p.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS) + (random.nextInt(3));

        }
        switch (b.getType()) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> {

                if (b.getLocation().getWorld() == null)
                    return;
                b.setType(Material.AIR);
                b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT, fortune));

                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> {
                if (b.getLocation().getWorld() == null)
                    return;

                b.setType(Material.AIR);
                b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT, fortune));

                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> {
                if (b.getLocation().getWorld() == null)
                    return;
                b.setType(Material.AIR);
                b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.COPPER_INGOT, fortune));

                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }

        }
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
    }
}
