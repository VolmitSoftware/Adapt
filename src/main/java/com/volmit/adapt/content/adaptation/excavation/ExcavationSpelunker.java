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

package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.nms.GlowingEntities;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class ExcavationSpelunker extends SimpleAdaptation<ExcavationSpelunker.Config> {
    private final Map<Player, Long> cooldowns;

    public ExcavationSpelunker() {
        super("excavation-spelunker");
        registerConfiguration(ExcavationSpelunker.Config.class);
        setDisplayName(Localizer.dLocalize("excavation", "spelunker", "name"));
        setDescription(Localizer.dLocalize("excavation", "spelunker", "description"));
        setIcon(Material.GOLDEN_HELMET);
        setInterval(20388);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        // Use Stone as base
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-iron-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .result(new ItemStack(Material.IRON_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-gold-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .result(new ItemStack(Material.GOLD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-copper-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .result(new ItemStack(Material.COPPER_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-lapis-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .result(new ItemStack(Material.LAPIS_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-redstone-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .result(new ItemStack(Material.REDSTONE_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-emerald-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .result(new ItemStack(Material.EMERALD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-diamond-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .result(new ItemStack(Material.DIAMOND_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-coal-ore")
                .ingredient(Material.STONE)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .result(new ItemStack(Material.COAL_ORE))
                .build());

        // Use Deepslate
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-iron-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .ingredient(Material.IRON_INGOT)
                .result(new ItemStack(Material.DEEPSLATE_IRON_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-gold-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .result(new ItemStack(Material.DEEPSLATE_GOLD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-copper-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .ingredient(Material.COPPER_INGOT)
                .result(new ItemStack(Material.DEEPSLATE_COPPER_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-lapis-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .ingredient(Material.LAPIS_LAZULI)
                .result(new ItemStack(Material.DEEPSLATE_LAPIS_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-redstone-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .ingredient(Material.REDSTONE)
                .result(new ItemStack(Material.DEEPSLATE_REDSTONE_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-emerald-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .ingredient(Material.EMERALD)
                .result(new ItemStack(Material.DEEPSLATE_EMERALD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-diamond-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .ingredient(Material.DIAMOND)
                .result(new ItemStack(Material.DEEPSLATE_DIAMOND_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-deepslate-coal-ore")
                .ingredient(Material.DEEPSLATE)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .ingredient(Material.COAL)
                .result(new ItemStack(Material.DEEPSLATE_COAL_ORE))
                .build());

// Use Nether Bricks
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-nether-gold-ore")
                .ingredient(Material.NETHER_BRICKS)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .ingredient(Material.GOLD_INGOT)
                .result(new ItemStack(Material.NETHER_GOLD_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-nether-quartz-ore")
                .ingredient(Material.NETHER_BRICKS)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .ingredient(Material.QUARTZ)
                .result(new ItemStack(Material.NETHER_QUARTZ_ORE))
                .build());
        registerRecipe(AdaptRecipe.shapeless()
                .key("spelunker-ancient-debris")
                .ingredient(Material.NETHER_BRICKS)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .ingredient(Material.NETHERITE_SCRAP)
                .result(new ItemStack(Material.ANCIENT_DEBRIS))
                .build());
        cooldowns = new HashMap<>();

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("excavation", "spelunker", "lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("excavation", "spelunker", "lore2") + getConfig().rangeMultiplier * level);
        v.addLore(C.YELLOW + Localizer.dLocalize("excavation", "spelunker", "lore3"));
        v.addLore(C.BOLD + Localizer.dLocalize("excavation", "spelunker", "lore4"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        // Check if player is sneaking, has Glowberries in main hand, and an ore in offhand
        if (p.isSneaking() && hasGlowberries(p) && hasOreInOffhand(p) && hasAdaptation(p)) {
            // Check if player is on cooldown
            if (cooldowns.containsKey(p)) {
                if (cooldowns.get(p) > System.currentTimeMillis()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                    return;
                }
            }
            int radius = getConfig().rangeMultiplier * getLevel(p);
            consumeGlowberry(p);
            searchForOres(p, radius);
            cooldowns.put(p, (long) (System.currentTimeMillis() + (1000 * getConfig().cooldown)));
        }
    }

    private boolean hasGlowberries(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.GLOW_BERRIES;
    }

    private void consumeGlowberry(Player player) {
        ItemStack berries = player.getInventory().getItemInMainHand();
        berries.setAmount(berries.getAmount() - 1);
        player.getInventory().setItemInMainHand(berries);
    }

    private boolean hasOreInOffhand(Player player) {
        Material offhandType = player.getInventory().getItemInOffHand().getType();
        return ItemListings.ores.contains(offhandType);
    }

    private void searchForOres(Player p, int radius) {
        Location playerLocation = p.getLocation();
        World world = p.getWorld();
        Material targetOre = p.getInventory().getItemInOffHand().getType();
        ChatColor c = ItemListings.oreColorsChatColor.get(targetOre);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.WHITE, 1);
        J.a(() -> {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z <= radius * radius) {
                            Location blockLocation = playerLocation.clone().add(x, y, z);
                            Block block = world.getBlockAt(blockLocation);
                            GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();


                            if (block.getType() == targetOre) {
                                // Raytrace particles from player to the found ore
                                Vector vector = blockLocation.clone().subtract(playerLocation).toVector().normalize().multiply(0.5);
                                Location particleLocation = playerLocation.clone();

                                while (particleLocation.distance(blockLocation) > 0.5) {
                                    particleLocation.add(vector);
                                    J.s(() -> {
                                        p.spawnParticle(Particle.REDSTONE, particleLocation, 1, dustOptions);
                                    });
                                }

                                J.s(() -> {

                                    world.playSound(block.getLocation().add(0.5, 0, 0.5), Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
                                    Slime slime = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Slime.class, (s) -> {
                                        s.setRotation(0, 0);
                                        s.setInvulnerable(true);
                                        s.setCollidable(false);
                                        s.setGravity(false);
                                        s.setSilent(true);
                                        s.setAI(false);
                                        s.setSize(2);
                                        s.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                                        s.setMetadata("preventSuffocation", new FixedMetadataValue(Adapt.instance, true));
                                    });

                                    try {
                                        glowingEntities.setGlowing(slime, p, c);
                                    } catch (ReflectiveOperationException e) {
                                        throw new RuntimeException(e);
                                    }

                                    J.s(() -> {
                                        try {
                                            glowingEntities.unsetGlowing(slime, p);
                                        } catch (ReflectiveOperationException e) {
                                            throw new RuntimeException(e);
                                        }

                                        slime.remove();
                                    }, 5 * 20);
                                });

                            }
                        }
                    }
                }
            }
        });
    }


    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Slime && e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            Slime slime = (Slime) e.getEntity();
            if (slime.hasMetadata("preventSuffocation")) {
                e.setCancelled(true);
            } else {
                e.setCancelled(true);
                slime.remove();
            }
        }
    }


    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
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
        double cooldown =6.0;
        int baseCost = 3;
        int initialCost = 3;
        double costFactor = 0.3;
        int maxLevel = 5;
        int rangeMultiplier = 5;
    }
}
