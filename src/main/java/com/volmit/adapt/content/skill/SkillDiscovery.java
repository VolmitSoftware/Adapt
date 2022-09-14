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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.Discovery;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryArmor;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryUnity;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryVillagerAtt;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryXpResist;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.potion.PotionEffect;

import java.util.Map;

public class SkillDiscovery extends SimpleSkill<SkillDiscovery.Config> {
    public SkillDiscovery() {
        super("discovery", Adapt.dLocalize("Skill", "Discovery", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Adapt.dLocalize("Skill", "Discovery", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Discovery", "Name"));
        setInterval(500);
        setIcon(Material.FILLED_MAP);
        registerAdaptation(new DiscoveryUnity());
        registerAdaptation(new DiscoveryArmor());
        registerAdaptation(new DiscoveryXpResist());
        registerAdaptation(new DiscoveryVillagerAtt());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        seeWorld(p, p.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractAtEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        seeEntity(p, e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityPickupItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (AdaptConfig.get().blacklistedWorlds.contains(e.getEntity().getWorld().getName())) {
            return;
        }
        if (e.getEntity() instanceof Player p) {
            if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
                return;
            }
            seeItem(p, e.getItem().getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(CraftItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getWhoClicked() instanceof Player p) {
            if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                return;
            }
            if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
                return;
            }
            try {
                NamespacedKey key = (NamespacedKey) Recipe.class.getDeclaredMethod("getKey()").invoke(e.getRecipe());

                if (key != null) {
                    seeRecipe(p, key.toString());
                }
            } catch (Throwable ignored) {

            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerItemConsumeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        seeItem(p, e.getItem());
        seeFood(p, e.getItem().getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        if (e.getClickedBlock() != null) {
            seeBlock(p, e.getClickedBlock().getBlockData(), e.getClickedBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && p.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }
        if (e.getAmount() > 0 && getLevel(p) > 0) {
            xp(p, e.getAmount());
        }
    }

    public void seeBlock(Player p, BlockData bd, Location l) {
        Discovery<String> d = getPlayer(p).getData().getSeenBlocks();
        if (d.isNewDiscovery(bd.getAsString())) {
            xp(p, getConfig().discoverBlockBaseXP + (getValue(bd) * getConfig().discoverBlockValueXPMultiplier));
            if (getConfig().showParticles) {
                p.spawnParticle(Particle.TOTEM, l.clone().add(0.5, 0.5, 0.5), 9, 0, 0, 0, 0.3);
            }
        }

        seeItem(p, bd.getMaterial());
    }

    public void seeItem(Player p, Material bd) {
        Discovery<Material> d = getPlayer(p).getData().getSeenItems();
        if (d.isNewDiscovery(bd)) {
            xp(p, getConfig().discoverItemBaseXP + (getValue(bd) * getConfig().discoverItemValueXPMultiplier));
        }
    }

    public void seeItem(Player p, ItemStack bd) {
        seeItem(p, bd.getType());
        Map<Enchantment, Integer> m = bd.getEnchantments();

        for (Enchantment i : m.keySet()) {
            seeEnchant(p, i, m.get(i));
        }
    }

    public void seeRecipe(Player p, String key) {
        Discovery<String> d = getPlayer(p).getData().getSeenRecipes();
        if (d.isNewDiscovery(key)) {
            xp(p, getConfig().discoverRecipeBaseXP);
        }
    }

    public void seeFood(Player p, Material bd) {
        Discovery<Material> d = getPlayer(p).getData().getSeenFoods();
        if (d.isNewDiscovery(bd)) {
            xp(p, getConfig().discoverFoodTypeXP);
        }
    }

    public void seeEntity(Player p, Entity bd) {
        Discovery<EntityType> d = getPlayer(p).getData().getSeenMobs();
        if (d.isNewDiscovery(bd.getType())) {
            xp(p, getConfig().discoverEntityTypeXP);
        }

        if (bd instanceof Player) {
            seePlayer(p, (Player) bd);
        }

        if (bd instanceof LivingEntity) {
            for (PotionEffect i : ((LivingEntity) bd).getActivePotionEffects()) {
                seePotionEffect(p, i);
            }
        }
    }

    public void seePlayer(Player p, Player bd) {
        Discovery<String> d = getPlayer(p).getData().getSeenPeople();
        if (d.isNewDiscovery(bd.getUniqueId().toString())) {
            xp(p, getConfig().discoverPlayerXP);
        }
    }

    public void seeEnchant(Player p, Enchantment bd, int level) {
        Discovery<String> d = getPlayer(p).getData().getSeenEnchants();
        if (d.isNewDiscovery(bd.getName() + " " + Form.toRoman(level))) {
            xp(p, getConfig().discoverEnchantBaseXP + Math.min(getConfig().discoverEnchantMaxXP, level * getConfig().discoverEnchantLevelXPMultiplier));
        }
    }

    public void seeWorld(Player p, World world) {
        Discovery<String> d = getPlayer(p).getData().getSeenWorlds();
        if (d.isNewDiscovery(world.getName() + "-" + world.getSeed())) {
            xp(p, getConfig().discoverWorldXP);
        }

        seeEnvironment(p, world.getEnvironment());
    }

    public void seeEnvironment(Player p, World.Environment world) {
        Discovery<World.Environment> d = getPlayer(p).getData().getSeenEnvironments();
        if (d.isNewDiscovery(world)) {
            xp(p, getConfig().discoverEnvironmentXP);
        }
    }

    public void seePotionEffect(Player p, PotionEffect e) {
        Discovery<String> d = getPlayer(p).getData().getSeenPotionEffects();
        if (d.isNewDiscovery(e.getType().getName() + " " + Form.toRoman(e.getAmplifier()).trim())) {
            xp(p, getConfig().discoverPotionXP);
        }
    }

    public void seeBiome(Player p, Biome e) {
        Discovery<Biome> d = getPlayer(p).getData().getSeenBiomes();
        if (d.isNewDiscovery(e)) {
            xp(p, getConfig().discoverBiomeXP);
        }
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (AdaptConfig.get().blacklistedWorlds.contains(i.getWorld().getName())) {
                return;
            }
            try {
                Block b = i.getTargetBlockExact(5, FluidCollisionMode.NEVER);
                if (b != null) {
                    seeBlock(i, b.getBlockData(), b.getLocation());
                    seeBiome(i, b.getBiome());
                }

            } catch (Throwable ignored) {

            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        boolean showParticles = true;
        double discoverBiomeXP = 15;
        double discoverPotionXP = 36;
        double discoverEntityTypeXP = 125;
        double discoverFoodTypeXP = 75;
        double discoverPlayerXP = 125;
        double discoverEnvironmentXP = 750;
        double discoverWorldXP = 750;
        double discoverEnchantMaxXP = 250;
        double discoverEnchantLevelXPMultiplier = 52;
        double discoverEnchantBaseXP = 5;
        double discoverItemBaseXP = 10;
        double discoverRecipeBaseXP = 15;
        double discoverItemValueXPMultiplier = 1;
        double discoverBlockBaseXP = 3;
        double discoverBlockValueXPMultiplier = 0.333;
    }
}
