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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import fr.skytasul.glowingentities.GlowingEntities;
import lombok.NoArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PickaxeQuarrySense extends SimpleAdaptation<PickaxeQuarrySense.Config> {
    private static final String MARKER_META = "adapt-quarry-sense-marker";

    public PickaxeQuarrySense() {
        super("pickaxe-quarry-sense");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("pickaxe.quarry_sense.description"));
        setDisplayName(Localizer.dLocalize("pickaxe.quarry_sense.name"));
        setIcon(Material.SPYGLASS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1200);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getScanRadius(level)) + C.GRAY + " " + Localizer.dLocalize("pickaxe.quarry_sense.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getDurabilityCostPercent(level), 2) + C.GRAY + " " + Localizer.dLocalize("pickaxe.quarry_sense.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("pickaxe.quarry_sense.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isEligiblePickaxe(hand) || p.hasCooldown(hand.getType())) {
            return;
        }

        int level = getLevel(p);
        p.spawnParticle(Particle.ENCHANT, p.getEyeLocation(), 14, 0.2, 0.25, 0.2, 0.15);
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.35f);

        int durabilityCost = getDurabilityCost(hand, level);
        if (!applyPickaxeCost(p, hand, durabilityCost)) {
            p.spawnParticle(Particle.SMOKE, p.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.03);
            SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 0.65f);
            return;
        }

        List<Block> ores = findNearbyOres(p.getLocation(), getScanRadius(level), getMaxHighlights(level));
        if (ores.isEmpty()) {
            p.spawnParticle(Particle.SMOKE, p.getEyeLocation(), 12, 0.22, 0.22, 0.22, 0.02);
            SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.75f);
            p.setCooldown(hand.getType(), getCooldownTicks(level));
            e.setCancelled(true);
            return;
        }

        for (Block ore : ores) {
            showOreMarker(p, ore, getHighlightTicks(level));
        }

        p.setCooldown(hand.getType(), getCooldownTicks(level));
        p.spawnParticle(Particle.GLOW, p.getEyeLocation(), 8, 0.15, 0.15, 0.15, 0.01);
        SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.6f);
        xp(p, ores.size() * getConfig().xpPerFoundOre);
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (e.getEntity() instanceof Slime slime && slime.hasMetadata(MARKER_META)) {
            e.setCancelled(true);
        }
    }

    private List<Block> findNearbyOres(Location origin, int radius, int maxResults) {
        List<Block> ores = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = origin.getWorld().getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                    if (!isOre(b.getBlockData())) {
                        continue;
                    }

                    ores.add(b);
                }
            }
        }

        ores.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(origin)));
        if (ores.size() > maxResults) {
            return new ArrayList<>(ores.subList(0, maxResults));
        }

        return ores;
    }

    private void showOreMarker(Player p, Block ore, int durationTicks) {
        GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
        if (glowingEntities == null) {
            showFallbackMarker(p, ore, durationTicks);
            return;
        }

        Slime slime = ore.getWorld().spawn(ore.getLocation().add(0.5, 0.5, 0.5), Slime.class, s -> {
            s.setInvulnerable(true);
            s.setCollidable(false);
            s.setGravity(false);
            s.setSilent(true);
            s.setAI(false);
            s.setSize(2);
            s.setRotation(0, 0);
            s.setMetadata(MARKER_META, new FixedMetadataValue(Adapt.instance, true));
            s.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        });

        try {
            glowingEntities.setGlowing(slime, p, ChatColor.AQUA);
        } catch (ReflectiveOperationException ignored) {
            slime.remove();
            showFallbackMarker(p, ore, durationTicks);
            return;
        }

        p.spawnParticle(Particle.GLOW, ore.getLocation().add(0.5, 0.5, 0.5), 20, 0.25, 0.25, 0.25, 0.001);
        p.spawnParticle(Particle.END_ROD, ore.getLocation().add(0.5, 0.5, 0.5), 8, 0.15, 0.15, 0.15, 0.003);

        J.s(() -> {
            try {
                glowingEntities.unsetGlowing(slime, p);
            } catch (ReflectiveOperationException ignored) {
            }
            slime.remove();
        }, durationTicks);
    }

    private void showFallbackMarker(Player p, Block ore, int durationTicks) {
        Location loc = ore.getLocation().add(0.5, 0.5, 0.5);
        for (int t = 0; t <= durationTicks; t += 8) {
            J.s(() -> {
                if (!p.isOnline()) {
                    return;
                }

                p.spawnParticle(Particle.GLOW, loc, 14, 0.22, 0.22, 0.22, 0.001);
                p.spawnParticle(Particle.END_ROD, loc, 4, 0.12, 0.12, 0.12, 0.001);
            }, t);
        }
    }

    private boolean isEligiblePickaxe(ItemStack hand) {
        if (!isItem(hand)) {
            return false;
        }

        return switch (hand.getType()) {
            case IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
            default -> false;
        };
    }

    private boolean applyPickaxeCost(Player p, ItemStack hand, int durabilityCost) {
        if (getConfig().costsReduceMaxDurability) {
            return tryReduceMaxDurability(p, hand, durabilityCost);
        }

        return tryDamagePickaxe(p, hand, durabilityCost);
    }

    private boolean tryDamagePickaxe(Player p, ItemStack hand, int durabilityCost) {
        if (!(hand.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }

        int maxDurability = hand.getType().getMaxDurability();
        int currentDamage = damageable.getDamage();
        if (currentDamage + durabilityCost >= maxDurability) {
            return false;
        }

        damageable.setDamage(currentDamage + durabilityCost);
        hand.setItemMeta(damageable);
        p.getInventory().setItemInMainHand(hand);
        return true;
    }

    private boolean tryReduceMaxDurability(Player p, ItemStack hand, int durabilityCost) {
        if (!(hand.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }

        int fallbackMax = Math.max(1, hand.getType().getMaxDurability());
        int currentMax = damageable.hasMaxDamage() ? Math.max(1, damageable.getMaxDamage()) : fallbackMax;
        int currentDamage = Math.max(0, damageable.getDamage());
        int newMax = currentMax - durabilityCost;
        if (newMax <= currentDamage + 1) {
            return false;
        }

        damageable.setMaxDamage(Math.max(1, newMax));
        hand.setItemMeta(damageable);
        p.getInventory().setItemInMainHand(hand);
        return true;
    }

    private int getScanRadius(int level) {
        return Math.max(4, (int) Math.round(getConfig().scanRadiusBase + (getLevelPercent(level) * getConfig().scanRadiusFactor)));
    }

    private int getMaxHighlights(int level) {
        return Math.max(1, (int) Math.round(getConfig().maxHighlightsBase + (getLevelPercent(level) * getConfig().maxHighlightsFactor)));
    }

    private int getHighlightTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().highlightTicksBase + (getLevelPercent(level) * getConfig().highlightTicksFactor)));
    }

    private int getCooldownTicks(int level) {
        return Math.max(10, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
    }

    private int getDurabilityCost(ItemStack hand, int level) {
        int maxDurability = Math.max(1, hand.getType().getMaxDurability());
        return Math.max(1, (int) Math.round(maxDurability * getDurabilityCostPercent(level)));
    }

    private double getDurabilityCostPercent(int level) {
        return Math.max(getConfig().minDurabilityCostPercent,
                getConfig().durabilityCostPercentBase - (getLevelPercent(level) * getConfig().durabilityCostPercentFactor));
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Sneak-right-click a block with an iron+ pickaxe to highlight nearby ores.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Costs Reduce Max Durability for the Pickaxe Quarry Sense adaptation.", impact = "True reduces max durability instead of adding normal damage.")
        boolean costsReduceMaxDurability = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Scan Radius Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double scanRadiusBase = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Scan Radius Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double scanRadiusFactor = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Highlights Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHighlightsBase = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Highlights Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHighlightsFactor = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Highlight Ticks Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double highlightTicksBase = 90;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Highlight Ticks Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double highlightTicksFactor = 90;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 60;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 40;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Percent Base for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durabilityCostPercentBase = 0.006;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Durability Cost Percent Factor for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double durabilityCostPercentFactor = 0.0045;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Durability Cost Percent for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minDurabilityCostPercent = 0.001;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Found Ore for the Pickaxe Quarry Sense adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerFoundOre = 6;
    }
}
