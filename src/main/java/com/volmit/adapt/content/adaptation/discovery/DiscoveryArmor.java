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

package com.volmit.adapt.content.adaptation.discovery;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.collection.KMap;
import com.volmit.adapt.util.reflect.registries.Attributes;
import com.volmit.adapt.util.reflect.registries.Particles;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DiscoveryArmor extends SimpleAdaptation<DiscoveryArmor.Config> {
    private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-discovery-armor".getBytes());
    private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString( "adapt:discovery-armor");
    private static final long UPDATE_COOLDOWN = TimeUnit.SECONDS.toMillis(3);
    private static final Sphere SPHERE = new Sphere(5);

    private final KMap<UUID, Long> playerData = new KMap<>();

    public DiscoveryArmor() {
        super("discovery-world-armor");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.armor.description"));
        setDisplayName(Localizer.dLocalize("discovery.armor.name"));
        setIcon(Material.TURTLE_HELMET);
        setInterval(305);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_CHESTPLATE)
                .key("challenge_discovery_armor_1hr")
                .title(Localizer.dLocalize("advancement.challenge_discovery_armor_1hr.title"))
                .description(Localizer.dLocalize("advancement.challenge_discovery_armor_1hr.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_CHESTPLATE)
                        .key("challenge_discovery_armor_24hr")
                        .title(Localizer.dLocalize("advancement.challenge_discovery_armor_24hr.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discovery_armor_24hr.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_discovery_armor_1hr", "discovery.armor.ticks-with-bonus", 72000, 400);
        registerMilestone("challenge_discovery_armor_24hr", "discovery.armor.ticks-with-bonus", 1728000, 2000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("discovery.armor.lore1") + C.GRAY + ", " + Localizer.dLocalize("discovery.armor.lore2"));
        v.addLore(C.YELLOW + "~ " + Localizer.dLocalize("discovery.armor.lore3") + C.BLUE + " +" + level * 0.25);
    }

    public double getArmorPoints(Material m) {
        return Math.log(Math.min(2000, m.getBlastResistance() * m.getBlastResistance())) + Math.log((m.getHardness() < 0 ? 50 : Math.min(50, m.getHardness() + 25)) * 0.33);
    }

    public double getArmor(Location l, int level) {
        Block center = l.getBlock();
        double armorValue = 0.0;
        double count = 0;

        var sphere = SPHERE.clone();

        while (sphere.hasNext()) {
            var r = sphere.next();
            Block b = center.getRelative(r.getX(), r.getY(), r.getZ());
            if (b.isEmpty() || b.isLiquid())
                continue;

            count++;
            double a = getArmorPoints(b.getType());
            if (Double.isNaN(a) || a < 0) {
                a = 0;
            }
            armorValue += a;

            if (a > 2 && M.r(0.005 * a)) {
                Vector v = VectorMath.directionNoNormal(l, b.getLocation().add(0.5, 0.5, 0.5));
                if (areParticlesEnabled()) {
                    l.getWorld().spawnParticle(Particles.ENCHANTMENT_TABLE, l.clone().add(0, 1, 0), 0, v.getX(), v.getY(), v.getZ());
                }
            }
        }

        return Math.min((armorValue / count) * (level / 2D) * 0.65, 10);
    }


    private double getRadius(double factor) {
        return factor * getConfig().radiusFactor;
    }

    private double getStrength(double factor) {
        return Math.pow(factor, getConfig().strengthExponent);
    }


    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            if (p == null || !p.isOnline()) continue;

            long now = M.ms();
            var nextUpdate = playerData.getOrDefault(p.getUniqueId(), now);
            if (nextUpdate > now) continue;
            playerData.put(p.getUniqueId(), now + UPDATE_COOLDOWN);

            var attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
            if (attribute == null) continue;

            if (!hasAdaptation(p)) {
                attribute.removeModifier(MODIFIER, MODIFIER_KEY);
            } else {
                double oldArmor = attribute.getModifier(MODIFIER, MODIFIER_KEY)
                        .stream()
                        .mapToDouble(IAttribute.Modifier::getAmount)
                        .max()
                        .orElse(0);

                double armor = getArmor(p.getLocation(), getLevel(p));
                armor = Double.isNaN(armor) ? 0 : armor;

                double lArmor = M.lerp(oldArmor, armor, 0.3);
                lArmor = Double.isNaN(lArmor) ? 0 : lArmor;
                attribute.setModifier(MODIFIER, MODIFIER_KEY, lArmor, AttributeModifier.Operation.ADD_NUMBER);
                if (lArmor > 0) {
                    adaptPlayer.getData().addStat("discovery.armor.ticks-with-bonus", 1);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerData.remove(event.getPlayer().getUniqueId());
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
    @ConfigDescription("Gain passive armor based on nearby block hardness.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Discovery Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public int radiusFactor = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Strength Exponent for the Discovery Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public double strengthExponent = 1.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Discovery Armor adaptation.", impact = "True enables this behavior and false disables it.")
        public boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
    }
}
