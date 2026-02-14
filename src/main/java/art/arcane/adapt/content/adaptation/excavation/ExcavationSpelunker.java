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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.reflect.registries.Particles;
import fr.skytasul.glowingentities.GlowingEntities;
import art.arcane.adapt.util.config.ConfigDescription;
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

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;

public class ExcavationSpelunker extends SimpleAdaptation<ExcavationSpelunker.Config> {
    private final Map<Player, Long> cooldowns;

    public ExcavationSpelunker() {
        super("excavation-spelunker");
        registerConfiguration(ExcavationSpelunker.Config.class);
        setDisplayName(Localizer.dLocalize("excavation.spelunker.name"));
        setDescription(Localizer.dLocalize("excavation.spelunker.description"));
        setIcon(Material.GOLDEN_HELMET);
        setInterval(20388);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPYGLASS)
                .key("challenge_excavation_spelunker_1k")
                .title(Localizer.dLocalize("advancement.challenge_excavation_spelunker_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_excavation_spelunker_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_ORE)
                        .key("challenge_excavation_spelunker_25k")
                        .title(Localizer.dLocalize("advancement.challenge_excavation_spelunker_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_excavation_spelunker_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_excavation_spelunker_1k", "excavation.spelunker.ores-revealed", 1000, 400);
        registerMilestone("challenge_excavation_spelunker_25k", "excavation.spelunker.ores-revealed", 25000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("excavation.spelunker.lore1"));
        v.addLore(C.YELLOW + Localizer.dLocalize("excavation.spelunker.lore2") + getConfig().rangeMultiplier * level);
        v.addLore(C.YELLOW + Localizer.dLocalize("excavation.spelunker.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        // Check if player is sneaking, has Glowberries in main hand, and an ore in offhand
        if (p.isSneaking() && hasGlowberries(p) && hasOreInOffhand(p) && hasAdaptation(p)) {
            // Check if player is on cooldown
            Long cooldown = cooldowns.get(p);
            if (cooldown != null && cooldown > System.currentTimeMillis()) {
                sp.play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                return;
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
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        Location blockLocation = playerLocation.clone().add(x, y, z);
                        Block block = world.getBlockAt(blockLocation);
                        GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();

                        if (block.getType() == targetOre) {
                            getPlayer(p).getData().addStat("excavation.spelunker.ores-revealed", 1);
                            // Raytrace particles from player to the found ore
                            Vector vector = blockLocation.clone().subtract(playerLocation).toVector().normalize().multiply(0.5);
                            Location particleLocation = playerLocation.clone();

                            while (particleLocation.distance(blockLocation) > 0.5) {
                                particleLocation.add(vector);
                                if (areParticlesEnabled()) {
                                    p.spawnParticle(Particles.REDSTONE, particleLocation, 1, dustOptions);
                                }
                            }

                            SoundPlayer spw = SoundPlayer.of(world);
                            spw.play(block.getLocation().add(0.5, 0, 0.5), Sound.BLOCK_BEACON_ACTIVATE, 1, 1);
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
                        }
                    }
                }
            }
        }
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
    @ConfigDescription("See ores through the ground using Glowberries in your main hand.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldown = 6.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Multiplier for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rangeMultiplier = 5;
    }
}
