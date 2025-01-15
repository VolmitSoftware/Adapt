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

package com.volmit.adapt.api;

import com.francobm.magicosmetics.api.CosmeticType;
import com.francobm.magicosmetics.api.MagicAPI;
import com.google.common.collect.Lists;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.value.MaterialValue;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.reflect.enums.PotionTypes;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static com.volmit.adapt.util.reflect.enums.Particles.ENCHANTMENT_TABLE;
import static com.volmit.adapt.util.reflect.enums.Particles.REDSTONE;
import static org.bukkit.potion.PotionType.*;
import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;

public interface Component {

    /**
     * Attempts to "damage" an item.
     * 1. If the item is null, null is returned
     * 2. If the item doesn't have durability, (damage) amount will be consumed from the stack, null will be returned if
     * more consumed than amount
     * 3. If the item has durability, the damage will be consumed and return the item affected, OR null if it broke
     *
     * @param item the item (tool)
     * @return the damaged item or null if destroyed
     */
    default ItemStack damage(ItemStack item) {
        if (item == null) {
            return null;
        }

        if (item.getItemMeta() == null) {
            if (item.getAmount() == 1) {
                return null;
            }

            item = item.clone();
            item.setAmount(item.getAmount() - 1);
            return item;
        }

        if (item.getItemMeta() instanceof Damageable d) {
            if (d.getDamage() + 1 > item.getType().getMaxDurability()) {
                return null;
            }

            d.setDamage(d.getDamage() + 1);
            item = item.clone();
            item.setItemMeta(d);
        } else {
            if (item.getAmount() == 1) {
                return null;
            }

            item = item.clone();
            item.setAmount(item.getAmount() - 1);

        }
        return item;
    }

    default void decrementItemstack(ItemStack hand, Player p) {
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
    }

    default double getArmorValue(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack boots = inv.getBoots();
        ItemStack helmet = inv.getHelmet();
        ItemStack chest = inv.getChestplate();
        ItemStack pants = inv.getLeggings();
        double armorValue = 0.0;
        if (helmet == null) armorValue = armorValue + 0.0;
        else {
            if (Bukkit.getServer().getPluginManager().getPlugin("MagicCosmetics") != null) {
                MagicAPI.hasEquipCosmetic(player, CosmeticType.HAT);
            }
            if (helmet.getType() == Material.LEATHER_HELMET) armorValue = armorValue + 0.04;
            else if (helmet.getType() == Material.GOLDEN_HELMET) armorValue = armorValue + 0.08;
            else if (helmet.getType() == Material.TURTLE_HELMET) armorValue = armorValue + 0.08;
            else if (helmet.getType() == Material.CHAINMAIL_HELMET) armorValue = armorValue + 0.08;
            else if (helmet.getType() == Material.IRON_HELMET) armorValue = armorValue + 0.08;
            else if (helmet.getType() == Material.DIAMOND_HELMET) armorValue = armorValue + 0.12;
            else if (helmet.getType() == Material.NETHERITE_HELMET) armorValue = armorValue + 0.12;
        }
        //
        if (boots == null) armorValue = armorValue + 0.0;
        else if (boots.getType() == Material.LEATHER_BOOTS) armorValue = armorValue + 0.04;
        else if (boots.getType() == Material.GOLDEN_BOOTS) armorValue = armorValue + 0.04;
        else if (boots.getType() == Material.CHAINMAIL_BOOTS) armorValue = armorValue + 0.04;
        else if (boots.getType() == Material.IRON_BOOTS) armorValue = armorValue + 0.08;
        else if (boots.getType() == Material.DIAMOND_BOOTS) armorValue = armorValue + 0.12;
        else if (boots.getType() == Material.NETHERITE_BOOTS) armorValue = armorValue + 0.12;
        //
        if (pants == null) armorValue = armorValue + 0.0;
        else if (pants.getType() == Material.LEATHER_LEGGINGS) armorValue = armorValue + 0.08;
        else if (pants.getType() == Material.GOLDEN_LEGGINGS) armorValue = armorValue + 0.12;
        else if (pants.getType() == Material.CHAINMAIL_LEGGINGS) armorValue = armorValue + 0.16;
        else if (pants.getType() == Material.IRON_LEGGINGS) armorValue = armorValue + 0.20;
        else if (pants.getType() == Material.DIAMOND_LEGGINGS) armorValue = armorValue + 0.24;
        else if (pants.getType() == Material.NETHERITE_LEGGINGS) armorValue = armorValue + 0.24;
        //
        if (chest == null) armorValue = armorValue + 0.0;
        else {
            if (Bukkit.getServer().getPluginManager().getPlugin("MagicCosmetics") != null) {
                MagicAPI.hasEquipCosmetic(player, CosmeticType.BAG);
            }
            if (chest.getType() == Material.LEATHER_CHESTPLATE) armorValue = armorValue + 0.12;
            else if (chest.getType() == Material.GOLDEN_CHESTPLATE) armorValue = armorValue + 0.20;
            else if (chest.getType() == Material.CHAINMAIL_CHESTPLATE) armorValue = armorValue + 0.20;
            else if (chest.getType() == Material.IRON_CHESTPLATE) armorValue = armorValue + 0.24;
            else if (chest.getType() == Material.DIAMOND_CHESTPLATE) armorValue = armorValue + 0.32;
            else if (chest.getType() == Material.NETHERITE_CHESTPLATE) armorValue = armorValue + 0.32;
        }
        return armorValue;
    }

    default PotionEffect getRawPotionEffect(ItemStack is) {
        if (is != null && is.getItemMeta() != null && is.getItemMeta() instanceof PotionMeta p && p.getBasePotionData().getType().getEffectType() != null) {
            boolean l = is.getType().equals(Material.LINGERING_POTION);
            boolean x = p.getBasePotionData().isExtended();
            boolean u = p.getBasePotionData().isUpgraded();
            int e = x ? l ? 2400 : 9600 : l ? 900 : 3600;
            int g = u ? l ? 440 : 1800 : e;
            int t = x ? l ? 1200 : 4800 : l ? 440 : 1800;
            int h = u ? l ? 100 : 420 : x ? l ? 440 : 1800 : l ? 220 : 900;

            int amplifier = 0;
            var type = p.getBasePotionData().getType();
            if (List.of(NIGHT_VISION, INVISIBILITY, FIRE_RESISTANCE, WATER_BREATHING).contains(type)) amplifier = e;
            else if (List.of(PotionTypes.JUMP, PotionTypes.SPEED, STRENGTH).contains(type)) amplifier = g;
            else if (SLOWNESS == type) amplifier = u ? l ? 100 : 400 : t;
            else if (List.of(POISON, PotionTypes.REGEN).contains(type)) amplifier = h;
            else if (List.of(WEAKNESS, SLOW_FALLING).contains(type)) amplifier = t;
            else if (LUCK == type) amplifier = l ? 1500 : 6000;
            else if (TURTLE_MASTER == type) amplifier = u ? l ? 100 : 400 : x ? l ? 200 : 800 : l ? 100 : 400;

            return new PotionEffect(Objects.requireNonNull(p.getBasePotionData().getType().getEffectType()), amplifier, p.getBasePotionData().isUpgraded() ? 1 : 0);
        }

        return null;
    }

    default boolean isAdaptableDamageCause(EntityDamageEvent event) {
        Set<EntityDamageEvent.DamageCause> excludedCauses = Set.of(
                // These are not damage causes that can are going to trigger adaptability
                EntityDamageEvent.DamageCause.VOID,
                EntityDamageEvent.DamageCause.LAVA,
                EntityDamageEvent.DamageCause.HOT_FLOOR,
                EntityDamageEvent.DamageCause.CRAMMING,
                EntityDamageEvent.DamageCause.MELTING,
                EntityDamageEvent.DamageCause.SUFFOCATION,
                EntityDamageEvent.DamageCause.SUICIDE,
                EntityDamageEvent.DamageCause.WITHER,
                EntityDamageEvent.DamageCause.FLY_INTO_WALL,
                EntityDamageEvent.DamageCause.FALL,
                EntityDamageEvent.DamageCause.SONIC_BOOM,
                EntityDamageEvent.DamageCause.THORNS
        );
        return !excludedCauses.contains(event.getCause());
    }

    default void addPotionStacks(Player p, PotionEffectType potionEffect, int amplifier, int duration, boolean overlap) {
        List<PotionEffect> activeEffects = new ArrayList<>(p.getActivePotionEffects());
        SoundPlayer sp = SoundPlayer.of(p);
        for (PotionEffect activeEffect : activeEffects) {
            if (activeEffect.getType() == potionEffect) {
                if (!overlap) {
                    return; // don't modify the effect if overlap is false
                }
                // modify the effect if overlap is true
                int newDuration = activeEffect.getDuration() + duration;
                int newAmplifier = Math.max(activeEffect.getAmplifier(), amplifier);
                p.removePotionEffect(potionEffect);
                p.addPotionEffect(new PotionEffect(potionEffect, newDuration, newAmplifier));
                sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.25f);
                return;
            }
        }
        // if we didn't find an existing effect, add a new one
        J.a(() -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            J.s(() -> {
                p.addPotionEffect(new PotionEffect(potionEffect, duration, amplifier));
                sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.25f);
            });
        });

    }


    default void potion(Player p, PotionEffectType type, int power, int duration) {
        p.addPotionEffect(new PotionEffect(type, power, duration, true, false, false));
    }

    default double blockXP(Block block, double xp) {
        try {
            return Math.round(xp * getBlockMultiplier(block));
        } catch (Exception e) {
            Adapt.verbose("Error in blockXP: " + e.getMessage());
        }
        return xp;
    }

    default double getBlockMultiplier(Block block) {
        return WorldData.of(block.getWorld()).reportEarnings(block);
    }

    default double getValue(Material material) {
        return MaterialValue.getValue(material);
    }

    default double getValue(BlockData block) {
        return MaterialValue.getValue(block.getMaterial());
    }

    default double getValue(ItemStack f) {
        return MaterialValue.getValue(f.getType());
    }

    default double getValue(Block block) {
        return MaterialValue.getValue(block.getType());
    }

    default void vfxMovingSphere(Location startLocation, Location endLocation, int ticks, Color color, double size, double density) {
        World world = startLocation.getWorld();
        double startX = startLocation.getX();
        double startY = startLocation.getY();
        double startZ = startLocation.getZ();
        double endX = endLocation.getX();
        double endY = endLocation.getY();
        double endZ = endLocation.getZ();
        double deltaX = (endX - startX) / ticks;
        double deltaY = (endY - startY) / ticks;
        double deltaZ = (endZ - startZ) / ticks;
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, (float) size);

        new BukkitRunnable() {
            int tick = 0;

            public void run() {
                if (tick >= ticks) {
                    cancel();
                    return;
                }
                double x = startX + deltaX * tick;
                double y = startY + deltaY * tick;
                double z = startZ + deltaZ * tick;
                Location particleLocation = new Location(world, x, y, z);

                for (double i = 0; i < Math.PI; i += Math.PI / density) {
                    double radius = Math.sin(i) * size;
                    double yCoord = Math.cos(i) * size;
                    for (double j = 0; j < Math.PI * 2; j += Math.PI / density) {
                        double xCoord = Math.sin(j) * radius;
                        double zCoord = Math.cos(j) * radius;

                        Location loc = particleLocation.clone().add(xCoord, yCoord, zCoord);
                        Objects.requireNonNull(world).spawnParticle(REDSTONE, loc, 0, 0, 0, 0, dustOptions);
                    }
                }

                tick++;
            }
        }.runTaskTimer(Adapt.instance, 0, 1);
    }

    default void safeGiveItem(Player player, Entity itemEntity, ItemStack is) {
        EntityPickupItemEvent e = new EntityPickupItemEvent(player, (Item) itemEntity, 0);
        Bukkit.getPluginManager().callEvent(e);
        if (!e.isCancelled()) {
            itemEntity.remove();
            if (!player.getInventory().addItem(is).isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), is);
            }
        }
    }


    default void safeGiveItem(Player player, ItemStack item) {
        if (!player.getInventory().addItem(item).isEmpty()) {
            player.getWorld().dropItem(player.getLocation(), item);
        }
    }


    default void vfxParticleLine(Location start, Location end, Particle particle, int pointsPerLine, int particleCount, double offsetX, double offsetY, double offsetZ, double extra, @Nullable Double data, boolean forceDisplay,
                                 @Nullable Predicate<Location> operationPerPoint) {
        double d = start.distance(end) / pointsPerLine;
        for (int i = 0; i < pointsPerLine; i++) {
            Location l = start.clone();
            Vector direction = end.toVector().subtract(start.toVector()).normalize();
            Vector v = direction.multiply(i * d);
            l.add(v.getX(), v.getY(), v.getZ());
            if (operationPerPoint == null) {
                Objects.requireNonNull(start.getWorld()).spawnParticle(particle, l, particleCount, offsetX, offsetY, offsetZ, extra, data, forceDisplay);
                continue;
            }
            if (operationPerPoint.test(l)) {
                Objects.requireNonNull(start.getWorld()).spawnParticle(particle, l, particleCount, offsetX, offsetY, offsetZ, extra, data, forceDisplay);
            }
        }
    }

    default void vfxParticleLine(Location start, Location end, int particleCount, Particle particle) {
        World world = start.getWorld();
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize();
        double step = distance / (particleCount - 1);

        for (int i = 0; i < particleCount; i++) {
            Location particleLocation = start.clone().add(direction.clone().multiply(i * step));
            Objects.requireNonNull(world).spawnParticle(particle, particleLocation, 1);
        }
    }


    private List<Location> getHollowCuboid(Location loc) {
        List<Location> result = Lists.newArrayList();
        World world = loc.getWorld();
        double minX = loc.getBlockX();
        double minY = loc.getBlockY();
        double minZ = loc.getBlockZ();
        double maxX = loc.getBlockX() + 1;
        double maxY = loc.getBlockY() + 1;
        double maxZ = loc.getBlockZ() + 1;

        for (double x = minX; x <= maxX; x += 0.25) {
            for (double y = minY; y <= maxY; y += 0.25) {
                for (double z = minZ; z <= maxZ; z += 0.25) {
                    int components = 0;
                    if (x == minX || x == maxX) components++;
                    if (y == minY || y == maxY) components++;
                    if (z == minZ || z == maxZ) components++;
                    if (components >= 2) {
                        result.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private List<Location> getHollowCuboid(Location loc, Location loc2) {
        List<Location> result = Lists.newArrayList();
        World world = loc.getWorld();

        double minX = loc.getBlockX();
        double minY = loc.getBlockY();
        double minZ = loc.getBlockZ();
        double maxX = loc2.getBlockX() + 1;
        double maxY = loc2.getBlockY() + 1;
        double maxZ = loc2.getBlockZ() + 1;

        for (double x = minX; x <= maxX; x += 0.25) {
            for (double y = minY; y <= maxY; y += 0.25) {
                for (double z = minZ; z <= maxZ; z += 0.25) {
                    int components = 0;
                    if (x == minX || x == maxX) components++;
                    if (y == minY || y == maxY) components++;
                    if (z == minZ || z == maxZ) components++;
                    if (components >= 2) {
                        result.add(new Location(world, x, y, z));
                    }
                }
            }
        }
        return result;
    }

    default void vfxCuboidOutline(Block block, Particle particle) {
        List<Location> hollowCube = getHollowCuboid(block.getLocation());
        for (Location l : hollowCube) {
            block.getWorld().spawnParticle(particle, l, 1, 0F, 0F, 0F, 0.000);
        }
    }

    default void vfxCuboidOutline(Block blockStart, Block blockEnd, Particle particle) {
        List<Location> hollowCube = getHollowCuboid(blockStart.getLocation(), blockEnd.getLocation());
        for (Location l : hollowCube) {
            blockStart.getWorld().spawnParticle(particle, l, 2, 0F, 0F, 0F, 0.000);
        }
    }

    default void vfxCuboidOutline(Block blockStart, Block blockEnd, Color color, int size) {
        List<Location> hollowCube = getHollowCuboid(blockStart.getLocation(), blockEnd.getLocation());
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, size);
        for (Location l : hollowCube) {
            blockStart.getWorld().spawnParticle(REDSTONE, l, 2, 0F, 0F, 0F, 0.000, dustOptions);
        }
    }

    default void vfxFastSphere(Location center, double range, Color color, int particleCount) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1);
        World world = center.getWorld();

        for (int i = 0; i < particleCount; i++) {
            double x, y, z;
            do {
                x = RANDOM.nextDouble() * 2 - 1;
                y = RANDOM.nextDouble() * 2 - 1;
                z = RANDOM.nextDouble() * 2 - 1;
            } while (x * x + y * y + z * z > 1);

            double magnitude = Math.sqrt(x * x + y * y + z * z);
            x = x / magnitude * range;
            y = y / magnitude * range;
            z = z / magnitude * range;

            Location particleLocation = center.clone().add(x, y, z);
            Objects.requireNonNull(world).spawnParticle(REDSTONE, particleLocation, 0, 0, 0, 0, dustOptions);
        }
    }

    default void vfxLevelUp(Player p) {
        p.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().clone().add(0, 1.7, 0), 100, 0.1, 0.1, 0.1, 4.1);
    }

    default void vfxFastRing(Location location, double radius, Color color) {
        for (int d = 0; d <= 90; d += 1) {
            Location particleLoc = new Location(location.getWorld(), location.getX(), location.getY(), location.getZ());
            particleLoc.setX(location.getX() + Math.cos(d) * radius);
            particleLoc.setZ(location.getZ() + Math.sin(d) * radius);
            Objects.requireNonNull(location.getWorld()).spawnParticle(REDSTONE, particleLoc, 1, new Particle.DustOptions(color, 1));
        }
    }

    default void vfxXP(Player p, Location l, int amt) {
        if (AdaptConfig.get().isUseEnchantmentTableParticleForActiveEffects()) {
            p.spawnParticle(ENCHANTMENT_TABLE, l, Math.min(amt / 10, 20), 0.5, 0.5, 0.5, 1);
        }
    }

    default void vfxXP(Location l) {
        if (AdaptConfig.get().isUseEnchantmentTableParticleForActiveEffects()) {
            Objects.requireNonNull(l.getWorld()).spawnParticle(ENCHANTMENT_TABLE, l.add(0, 1.7, 0), 3, 0.1, 0.1, 0.1, 3);
        }
    }

    default void damageHand(Player p, int damage) {
        ItemStack is = p.getInventory().getItemInMainHand();
        ItemMeta im = is.getItemMeta();

        if (im == null) {
            return;
        }

        if (im.isUnbreakable()) {
            return;
        }

        Damageable dm = (Damageable) im;
        dm.setDamage(dm.getDamage() + damage);

        if (dm.getDamage() > is.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            SoundPlayer spw = SoundPlayer.of(p.getWorld());
            spw.play(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }

        is.setItemMeta(im);
        p.getInventory().setItemInMainHand(is);
    }

    default void damageOffHand(Player p, int damage) {
        ItemStack is = p.getInventory().getItemInOffHand();
        ItemMeta im = is.getItemMeta();

        if (im == null) {
            return;
        }

        if (im.isUnbreakable()) {
            return;
        }

        Damageable dm = (Damageable) im;
        dm.setDamage(dm.getDamage() + damage);

        if (dm.getDamage() > is.getType().getMaxDurability()) {
            p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            SoundPlayer spw = SoundPlayer.of(p.getWorld());
            spw.play(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }

        is.setItemMeta(im);
        p.getInventory().setItemInOffHand(is);
    }

    default int getExp(Player p) {
        return (int) (getExpToLevel(p) * p.getExp());
    }

    default int getLevel(Player p) {
        return p.getLevel();
    }

    default int getExpToLevel(Player p) {
        return p.getExpToLevel();
    }

    default int getExpToLevel(int level) {
        return level >= 30 ? 62 + (level - 30) * 7 : (level >= 15 ? 17 + (level - 15) * 3 : 17);
    }

    default void recalcTotalExp(Player p) {
        int total = getExp(p);
        for (int i = 0; i < p.getLevel(); i++) {
            total += getExpToLevel(i);
        }
        p.setTotalExperience(total);
    }

}
