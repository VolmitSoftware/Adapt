/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.content.adaptation.pickaxe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.reflect.registries.Enchantments;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Random;

public class PickaxeAutosmelt extends SimpleAdaptation<PickaxeAutosmelt.Config> {
    private static final Random RANDOM = new Random();

    public PickaxeAutosmelt() {
        super("pickaxe-autosmelt");
        registerConfiguration(PickaxeAutosmelt.Config.class);
        setDescription(Localizer.dLocalize("pickaxe", "autosmelt", "description"));
        setDisplayName(Localizer.dLocalize("pickaxe", "autosmelt", "name"));
        setIcon(Material.RAW_GOLD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(7444);
    }

    static void autosmeltBlockDTI(Block b, Player p) {
        int fortune = getFortuneOreMultiplier(p.getInventory().getItemInMainHand()
                .getEnchantments().get(Enchantments.LOOT_BONUS_BLOCKS));
        SoundPlayer spw = SoundPlayer.of(b.getWorld());
        switch (b.getType()) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> {
                if (b.getLocation().getWorld() == null) {
                    return;
                }

                b.setType(Material.AIR);
                HashMap<Integer, ItemStack> excessItems = p.getInventory().addItem(new ItemStack(Material.IRON_INGOT, fortune));
                excessItems.values().forEach(itemStack -> b.getLocation().getWorld().dropItemNaturally(b.getLocation(), itemStack));
                spw.play(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> {
                if (b.getLocation().getWorld() == null) {
                    return;
                }

                b.setType(Material.AIR);
                HashMap<Integer, ItemStack> excessItems = p.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, fortune));
                excessItems.values().forEach(itemStack -> b.getLocation().getWorld().dropItemNaturally(b.getLocation(), itemStack));
                spw.play(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> {
                if (b.getLocation().getWorld() == null) {
                    return;
                }
                b.setType(Material.AIR);
                HashMap<Integer, ItemStack> excessItems = p.getInventory().addItem(new ItemStack(Material.COPPER_INGOT, fortune));
                excessItems.values().forEach(itemStack -> b.getLocation().getWorld().dropItemNaturally(b.getLocation(), itemStack));
                spw.play(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }

        }
    }

    static void autosmeltBlock(Block b, Player p) {
        int fortune = getFortuneOreMultiplier(p.getInventory().getItemInMainHand()
                .getEnchantments().get(Enchantments.LOOT_BONUS_BLOCKS));
        SoundPlayer spw = SoundPlayer.of(b.getWorld());
        switch (b.getType()) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> {

                if (b.getLocation().getWorld() == null) {
                    return;
                }

                b.setType(Material.AIR);
                b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.IRON_INGOT, fortune));
                spw.play(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> {
                if (b.getLocation().getWorld() == null) {
                    return;
                }

                b.setType(Material.AIR);
                b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.GOLD_INGOT, fortune));
                spw.play(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> {
                if (b.getLocation().getWorld() == null) {
                    return;
                }
                b.setType(Material.AIR);
                b.getLocation().getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.COPPER_INGOT, fortune));
                spw.play(b.getLocation(), Sound.BLOCK_LAVA_POP, 1, 1);
                b.getWorld().spawnParticle(Particle.LAVA, b.getLocation(), 3, 0.5, 0.5, 0.5);
            }

        }
    }

    // https://minecraft.fandom.com/wiki/Fortune?oldid=2359015#Ore
    private static int getFortuneOreMultiplier(Integer fortuneLevel) {
        if (fortuneLevel == null || fortuneLevel < 1) return 1;

        double averageBonusMultiplier = (1.0/(fortuneLevel+2) + (fortuneLevel+1)/2.0) - 1;
        int sumOfBonusMultipliers = (fortuneLevel*(fortuneLevel+1))/2;
        double chancePerMultiplier = averageBonusMultiplier/sumOfBonusMultipliers;

        int bonusMultiplier = ((int) (RANDOM.nextDouble()/chancePerMultiplier)) + 1;

        return bonusMultiplier <= fortuneLevel ? bonusMultiplier+1 : 1;
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("pickaxe", "autosmelt", "lore1"));
        v.addLore(C.GREEN + "" + (level * 1.25) + C.GRAY + Localizer.dLocalize("pickaxe", "autosmelt", "lore2"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (e.isCancelled()) {
            return;
        }
        if (!hasAdaptation(p)) {
            return;
        }
        if (!e.getBlock().getBlockData().getMaterial().name().endsWith("_ORE") && !ItemListings.getSmeltOre().contains(e.getBlock().getType())) {
            return;
        }
        if (!canBlockBreak(p, e.getBlock().getLocation())) {
            return;
        }

        PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("pickaxe");
        PlayerAdaptation adaptation = line != null ? line.getAdaptation("pickaxe-drop-to-inventory") : null;
        if (adaptation != null && adaptation.getLevel() > 0) {
            PickaxeAutosmelt.autosmeltBlockDTI(e.getBlock(), p);
        } else {
            autosmeltBlock(e.getBlock(), p);
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 4;
        int initialCost = 4;
        double costFactor = 2.325;
    }
}
