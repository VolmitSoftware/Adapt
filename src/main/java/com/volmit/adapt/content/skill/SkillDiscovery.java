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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.api.world.Discovery;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryArmor;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryUnity;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryVillagerAtt;
import com.volmit.adapt.content.adaptation.discovery.DiscoveryXpResist;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.registries.Particles;
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
        super("discovery", Localizer.dLocalize("skill.discovery.icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setDescription(Localizer.dLocalize("skill.discovery.description"));
        setDisplayName(Localizer.dLocalize("skill.discovery.name"));
        setInterval(500);
        setIcon(Material.FILLED_MAP);
        registerAdaptation(new DiscoveryUnity());
        registerAdaptation(new DiscoveryArmor());
        registerAdaptation(new DiscoveryXpResist());
        registerAdaptation(new DiscoveryVillagerAtt());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ITEM_FRAME).key("challenge_discover_items_50")
                .title(Localizer.dLocalize("advancement.challenge_discover_items_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_discover_items_50.description"))
                .model(CustomModel.get(Material.ITEM_FRAME, "advancement", "discovery", "challenge_discover_items_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CHEST)
                        .key("challenge_discover_items_250")
                        .title(Localizer.dLocalize("advancement.challenge_discover_items_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discover_items_250.description"))
                        .model(CustomModel.get(Material.CHEST, "advancement", "discovery", "challenge_discover_items_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_items_50").goal(50).stat("discovery.items").reward(500).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_items_250").goal(250).stat("discovery.items").reward(2500).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GRASS_BLOCK).key("challenge_discover_blocks_50")
                .title(Localizer.dLocalize("advancement.challenge_discover_blocks_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_discover_blocks_50.description"))
                .model(CustomModel.get(Material.GRASS_BLOCK, "advancement", "discovery", "challenge_discover_blocks_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.STONE_BRICKS)
                        .key("challenge_discover_blocks_250")
                        .title(Localizer.dLocalize("advancement.challenge_discover_blocks_250.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discover_blocks_250.description"))
                        .model(CustomModel.get(Material.STONE_BRICKS, "advancement", "discovery", "challenge_discover_blocks_250"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_blocks_50").goal(50).stat("discovery.blocks").reward(500).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_blocks_250").goal(250).stat("discovery.blocks").reward(2500).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.EGG).key("challenge_discover_mobs_25")
                .title(Localizer.dLocalize("advancement.challenge_discover_mobs_25.title"))
                .description(Localizer.dLocalize("advancement.challenge_discover_mobs_25.description"))
                .model(CustomModel.get(Material.EGG, "advancement", "discovery", "challenge_discover_mobs_25"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.SPAWNER)
                        .key("challenge_discover_mobs_75")
                        .title(Localizer.dLocalize("advancement.challenge_discover_mobs_75.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discover_mobs_75.description"))
                        .model(CustomModel.get(Material.SPAWNER, "advancement", "discovery", "challenge_discover_mobs_75"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_mobs_25").goal(25).stat("discovery.mobs").reward(500).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_mobs_75").goal(75).stat("discovery.mobs").reward(2500).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.MAP).key("challenge_discover_biomes_10")
                .title(Localizer.dLocalize("advancement.challenge_discover_biomes_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_discover_biomes_10.description"))
                .model(CustomModel.get(Material.MAP, "advancement", "discovery", "challenge_discover_biomes_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.FILLED_MAP)
                        .key("challenge_discover_biomes_40")
                        .title(Localizer.dLocalize("advancement.challenge_discover_biomes_40.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discover_biomes_40.description"))
                        .model(CustomModel.get(Material.FILLED_MAP, "advancement", "discovery", "challenge_discover_biomes_40"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_biomes_10").goal(10).stat("discovery.biomes").reward(500).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_biomes_40").goal(40).stat("discovery.biomes").reward(2500).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.APPLE).key("challenge_discover_foods_10")
                .title(Localizer.dLocalize("advancement.challenge_discover_foods_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_discover_foods_10.description"))
                .model(CustomModel.get(Material.APPLE, "advancement", "discovery", "challenge_discover_foods_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.GOLDEN_APPLE)
                        .key("challenge_discover_foods_30")
                        .title(Localizer.dLocalize("advancement.challenge_discover_foods_30.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discover_foods_30.description"))
                        .model(CustomModel.get(Material.GOLDEN_APPLE, "advancement", "discovery", "challenge_discover_foods_30"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_foods_10").goal(10).stat("discovery.foods").reward(500).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_discover_foods_30").goal(30).stat("discovery.foods").reward(2500).build());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerChangedWorldEvent e) {
        shouldReturnForPlayer(e.getPlayer(), () -> scheduleSeeWorld(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractAtEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        shouldReturnForPlayer(e.getPlayer(), e, () -> seeEntity(e.getPlayer(), e.getRightClicked()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityPickupItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Player p) {
            shouldReturnForPlayer(p, e, () -> seeItem(p, e.getItem().getItemStack()));
        }

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(CraftItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) return;
        shouldReturnForPlayer(p, e, () -> {
            try {
                NamespacedKey key = (NamespacedKey) Recipe.class.getDeclaredMethod("getKey()").invoke(e.getRecipe());
                if (key != null) {
                    seeCraftedRecipe(p, key.toString());
                }
            } catch (Throwable ignored) {
                Adapt.verbose("No recipe key found for " + e.getRecipe().getResult().getType().name());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerItemConsumeEvent e) {
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            seeItem(e.getPlayer(), e.getItem());
            seeFood(e.getPlayer(), e.getItem().getType());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        shouldReturnForPlayer(e.getPlayer(), e, () -> {
            if (e.getClickedBlock() != null) {
                seeBlock(e.getPlayer(), e.getClickedBlock().getBlockData(), e.getClickedBlock().getLocation());
            }
        });

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerExpChangeEvent e) {
        shouldReturnForPlayer(e.getPlayer(), () -> {
            if (e.getAmount() > 0 && getLevel(e.getPlayer()) > 0) {
                xp(e.getPlayer(), e.getAmount());
            }
        });
    }

    private void scheduleSeeWorld(Player p) {
        try {
            J.a(() -> seeWorld(p, p.getWorld()), 15);
        } catch (Exception e) {
            Adapt.error("Failed to discover world " + p.getWorld().getName());
        }
    }

    public void seeBlock(Player p, BlockData bd, Location l) {
        Discovery<String> d = getPlayer(p).getData().getSeenBlocks();
        if (d.isNewDiscovery(bd.getAsString())) {
            xp(p, getConfig().discoverBlockBaseXP + (getValue(bd) * getConfig().discoverBlockValueXPMultiplier));
            getPlayer(p).getData().addStat("discovery.blocks", 1);
            if (getConfig().showParticles) {
                p.spawnParticle(Particles.TOTEM, l.clone().add(0.5, 0.5, 0.5), 9, 0, 0, 0, 0.3);
            }
        }

        seeItem(p, bd.getMaterial());
    }

    public void seeItem(Player p, Material bd) {
        Discovery<Material> d = getPlayer(p).getData().getSeenItems();
        if (d.isNewDiscovery(bd)) {
            xp(p, getConfig().discoverItemBaseXP + (getValue(bd) * getConfig().discoverItemValueXPMultiplier));
            getPlayer(p).getData().addStat("discovery.items", 1);
        }
    }

    public void seeItem(Player p, ItemStack bd) {
        seeItem(p, bd.getType());
        Map<Enchantment, Integer> m = bd.getEnchantments();

        for (Enchantment i : m.keySet()) {
            seeEnchant(p, i, m.get(i));
        }
    }

    public void seeCraftedRecipe(Player p, String key) {
        Discovery<String> d = getPlayer(p).getData().getSeenRecipes();
        if (d.isNewDiscovery(key)) {
            xp(p, getConfig().discoverRecipeBaseXP);
        }
    }

    public void seeFood(Player p, Material bd) {
        Discovery<Material> d = getPlayer(p).getData().getSeenFoods();
        if (d.isNewDiscovery(bd)) {
            xp(p, getConfig().discoverFoodTypeXP);
            getPlayer(p).getData().addStat("discovery.foods", 1);
        }
    }

    public void seeEntity(Player p, Entity bd) {
        Discovery<EntityType> d = getPlayer(p).getData().getSeenMobs();
        if (d.isNewDiscovery(bd.getType())) {
            xp(p, getConfig().discoverEntityTypeXP);
            getPlayer(p).getData().addStat("discovery.mobs", 1);
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
        Discovery<String> d = getPlayer(p).getData().getSeenBiomes();
        if (d.isNewDiscovery(e.getKey().toString())) {
            xp(p, getConfig().discoverBiomeXP);
            getPlayer(p).getData().addStat("discovery.biomes", 1);
        }
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) return;
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (shouldReturnForPlayer(i)) continue;
            checkStatTrackers(getPlayer(i));
            seeTargetBlock(i);
        }
    }

    private void seeTargetBlock(Player i) {
        try {
            Block b = i.getTargetBlockExact(5, FluidCollisionMode.NEVER);
            if (b != null) {
                seeBlock(i, b.getBlockData(), b.getLocation());
                seeBiome(i, b.getBiome());
            }
        } catch (Throwable ignored) {
            Adapt.verbose("Failed to get target block for " + i.getName());
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Discovery skill.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Biome XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverBiomeXP = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Potion XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverPotionXP = 36;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Entity Type XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverEntityTypeXP = 125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Food Type XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverFoodTypeXP = 75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Player XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverPlayerXP = 125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Environment XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverEnvironmentXP = 750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover World XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverWorldXP = 750;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Enchant Max XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverEnchantMaxXP = 250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Enchant Level XPMultiplier for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverEnchantLevelXPMultiplier = 52;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Enchant Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverEnchantBaseXP = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Item Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverItemBaseXP = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Recipe Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverRecipeBaseXP = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Item Value XPMultiplier for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverItemValueXPMultiplier = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Block Base XP for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverBlockBaseXP = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Discover Block Value XPMultiplier for the Discovery skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double discoverBlockValueXPMultiplier = 0.333;
    }
}
