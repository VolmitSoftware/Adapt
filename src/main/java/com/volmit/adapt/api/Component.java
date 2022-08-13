package com.volmit.adapt.api;

import com.google.common.collect.Lists;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.value.MaterialValue;
import com.volmit.adapt.api.xp.XP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public interface Component {
    default void wisdom(Player p, long w) {
        XP.wisdom(p, w);
    }

    /**
     * Attempts to "damage" an item.
     * 1. If the item is null, null is returned
     * 2. If the item doesnt have durability, (damage) amount will be consumed from the stack, null will be returned if
     * more consumed than amount
     * 3. If the item has durability, the damage will be consuemd and return the item affected, OR null if it broke
     *
     * @param item   the item (tool)
     * @param damage the damage to cause
     * @return the damaged item or null if destroyed
     */
    default ItemStack damage(ItemStack item, int damage) {
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
            return item;
        } else {
            if (item.getAmount() == 1) {
                return null;
            }

            item = item.clone();
            item.setAmount(item.getAmount() - 1);

            return item;
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
        else if (helmet.getType() == Material.LEATHER_HELMET) armorValue = armorValue + 0.04;
        else if (helmet.getType() == Material.GOLDEN_HELMET) armorValue = armorValue + 0.08;
        else if (helmet.getType() == Material.TURTLE_HELMET) armorValue = armorValue + 0.08;
        else if (helmet.getType() == Material.CHAINMAIL_HELMET) armorValue = armorValue + 0.08;
        else if (helmet.getType() == Material.IRON_HELMET) armorValue = armorValue + 0.08;
        else if (helmet.getType() == Material.DIAMOND_HELMET) armorValue = armorValue + 0.12;
        else if (helmet.getType() == Material.NETHERITE_HELMET) armorValue = armorValue + 0.12;
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
        else if (chest.getType() == Material.LEATHER_CHESTPLATE) armorValue = armorValue + 0.12;
        else if (chest.getType() == Material.GOLDEN_CHESTPLATE) armorValue = armorValue + 0.20;
        else if (chest.getType() == Material.CHAINMAIL_CHESTPLATE) armorValue = armorValue + 0.20;
        else if (chest.getType() == Material.IRON_CHESTPLATE) armorValue = armorValue + 0.24;
        else if (chest.getType() == Material.DIAMOND_CHESTPLATE) armorValue = armorValue + 0.32;
        else if (chest.getType() == Material.NETHERITE_CHESTPLATE) armorValue = armorValue + 0.32;
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
            return new PotionEffect(p.getBasePotionData().getType().getEffectType(), switch (p.getBasePotionData().getType()) {
                case NIGHT_VISION, INVISIBILITY, FIRE_RESISTANCE, WATER_BREATHING -> e;
                case JUMP, SPEED, STRENGTH -> g;
                case SLOWNESS -> u ? l ? 100 : 400 : t;
                case POISON, REGEN -> h;
                case WEAKNESS, SLOW_FALLING -> t;
                case LUCK -> l ? 1500 : 6000;
                case TURTLE_MASTER -> u ? l ? 100 : 400 : x ? l ? 200 : 800 : l ? 100 : 400;
                default -> 0;
            }, p.getBasePotionData().isUpgraded() ? 1 : 0);
        }

        return null;
    }

    default void addPotionStacks(Player p, PotionEffectType potionEffect, int amplifier, int duration, Boolean overlap) {
        List<PotionEffectType> activeList = p.getActivePotionEffects().stream().map(PotionEffect::getType).toList();
        if (activeList.size() > 0) {
            for (PotionEffectType type : activeList) {
                if (type.equals(potionEffect)) {
                    if (overlap) {
                        p.sendMessage(ChatColor.RED + "You have gained a stack of " + potionEffect.getName() + "!");
                        int newAmplifier = Objects.requireNonNull(p.getPotionEffect(type)).getAmplifier();
                        int newDuration = Objects.requireNonNull(p.getPotionEffect(type)).getDuration();
                        p.removePotionEffect(type);
                        p.addPotionEffect(new PotionEffect(potionEffect, newDuration + duration, newAmplifier + amplifier, false, false));
                    }
                    int newAmplifier = Objects.requireNonNull(p.getPotionEffect(type)).getAmplifier();
                    int newDuration = Objects.requireNonNull(p.getPotionEffect(type)).getDuration();
                    p.removePotionEffect(type);
                    p.addPotionEffect(new PotionEffect(potionEffect, newDuration, newAmplifier + 1, false, false));
                }
            }

        }
        if (!activeList.contains(potionEffect)) {
            p.sendMessage(ChatColor.RED + "You have gained a stack of " + potionEffect.getName() + "!");
            p.addPotionEffect(new PotionEffect(potionEffect, duration, amplifier));
        }
    }

    default void potion(Player p, PotionEffectType type, int power, int duration) {
        p.addPotionEffect(new PotionEffect(type, power, duration, true, false, false));
    }

    default double blockXP(Block block, double xp) {
        return Math.round(xp * getBlockMultiplier(block));
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

    default void vfxSphereV1(Player p, Location l, double radius, Particle particle, int verticalDensity, int radialDensity) {
        for (double phi = 0; phi <= Math.PI; phi += Math.PI / verticalDensity) {
            for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI / radialDensity) {
                double x = radius * Math.cos(theta) * Math.sin(phi);
                double y = radius * Math.cos(phi) + 1.5;
                double z = radius * Math.sin(theta) * Math.sin(phi);

                l.add(x, y, z);
                p.getWorld().spawnParticle(particle, l, 1, 0F, 0F, 0F, 0.001);
                l.subtract(x, y, z);
            }
        }
    }


    default void vfxZuck(Location from, Location to) {
        Vector v = from.clone().subtract(to).toVector();
        double l = v.length();
        v.normalize();
        from.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, to, 1, 6, 6, 6, 0.6);
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
                start.getWorld().spawnParticle(particle, l, particleCount, offsetX, offsetY, offsetZ, extra, data, forceDisplay);
                continue;
            }
            if (operationPerPoint.test(l)) {
                start.getWorld().spawnParticle(particle, l, particleCount, offsetX, offsetY, offsetZ, extra, data, forceDisplay);
            }
        }
    }

    private List<Location> getHollowCube(Location loc, double particleDistance) {
        List<Location> result = Lists.newArrayList();
        World world = loc.getWorld();
        double minX = loc.getBlockX();
        double minY = loc.getBlockY();
        double minZ = loc.getBlockZ();
        double maxX = loc.getBlockX() + 1;
        double maxY = loc.getBlockY() + 1;
        double maxZ = loc.getBlockZ() + 1;

        for (double x = minX; x <= maxX; x += particleDistance) {
            for (double y = minY; y <= maxY; y += particleDistance) {
                for (double z = minZ; z <= maxZ; z += particleDistance) {
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

    private List<Location> getHollowCuboid(Location loc, Location loc2, double particleDistance) {
        List<Location> result = Lists.newArrayList();
        World world = loc.getWorld();

        double minX = loc.getBlockX();
        double minY = loc.getBlockY();
        double minZ = loc.getBlockZ();
        double maxX = loc2.getBlockX() + 1;
        double maxY = loc2.getBlockY() + 1;
        double maxZ = loc2.getBlockZ() + 1;

        for (double x = minX; x <= maxX; x += particleDistance) {
            for (double y = minY; y <= maxY; y += particleDistance) {
                for (double z = minZ; z <= maxZ; z += particleDistance) {
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

    default void vfxSingleCubeOutline(Block block, Particle particle) {
        List<Location> hollowCube = getHollowCube(block.getLocation(), 0.25);
        for (Location l : hollowCube) {
            block.getWorld().spawnParticle(particle, l, 1, 0F, 0F, 0F, 0.000);
        }
    }

    default void vfxSingleCuboidOutline(Block blockStart, Block blockEnd, Particle particle) {
        List<Location> hollowCube = getHollowCuboid(blockStart.getLocation(), blockEnd.getLocation(), 0.25);
        for (Location l : hollowCube) {
            blockStart.getWorld().spawnParticle(particle, l, 2, 0F, 0F, 0F, 0.000);
        }
    }

    default void vfxPrismOutline(Location placer, double outset, Particle particle, int particleCount) {

        Location top = new Location(placer.getWorld(), placer.getX(), placer.getY() + outset, placer.getZ());
        Location baseCorner1 = new Location(placer.getWorld(), placer.getX() - outset, placer.getY(), placer.getZ() - outset);
        Location baseCorner2 = new Location(placer.getWorld(), placer.getX() + outset, placer.getY(), placer.getZ() - outset);
        Location baseCorner3 = new Location(placer.getWorld(), placer.getX() + outset, placer.getY(), placer.getZ() + outset);
        Location baseCorner4 = new Location(placer.getWorld(), placer.getX() - outset, placer.getY(), placer.getZ() + outset);

        vfxParticleLine(baseCorner1, baseCorner2, particle, particleCount, 1, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
        vfxParticleLine(baseCorner2, baseCorner3, particle, particleCount, 1, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
        vfxParticleLine(baseCorner3, baseCorner4, particle, particleCount, 1, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
        vfxParticleLine(baseCorner4, baseCorner1, particle, particleCount, 1, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());

        for (Location location : Arrays.asList(baseCorner1, baseCorner2, baseCorner3, baseCorner4)) {
            vfxParticleLine(location, top, particle, particleCount, 1, 0.0D, 0D, 0.0D, 0D, null, true, l -> l.getBlock().isPassable());
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
            location.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, new Particle.DustOptions(color, 1));
        }
    }

    default void vfxFastRing(Location location, double radius, Particle particle) {
        for (int d = 0; d <= 90; d += 1) {
            Location particleLoc = new Location(location.getWorld(), location.getX(), location.getY(), location.getZ());
            particleLoc.setX(location.getX() + Math.cos(d) * radius);
            particleLoc.setZ(location.getZ() + Math.sin(d) * radius);
            location.getWorld().spawnParticle(particle, particleLoc, 1);
        }
    }

    default void vfxFastRing(Location location, double radius, Particle particle, int angle) {
        for (int d = 0; d <= 90; d += angle) {
            Location particleLoc = new Location(location.getWorld(), location.getX(), location.getY(), location.getZ());
            particleLoc.setX(location.getX() + Math.cos(d) * radius);
            particleLoc.setZ(location.getZ() + Math.sin(d) * radius);
            location.getWorld().spawnParticle(particle, particleLoc, 1);
        }
    }

    default void vfxShootParticle(Player player, Particle particle, double velocity, int count) {
        Location location = player.getEyeLocation();
        Vector direction = location.getDirection();
        for (int i = 0; i < count; i++) {
            player.getWorld().spawnParticle(particle, location.getX(), location.getY(), location.getZ(), 0, (float) direction.getX(), (float) direction.getY(), (float) direction.getZ(), velocity, null);
        }
    }

    default void vfxParticleRing(Location location, int radius, int height, Particle particle, double angleIncrement) {
        for (double y = 0; y <= height; y += 0.05) {
            for (double angle = 0; angle < 360; angle += angleIncrement) {
                double x = location.getX() + (radius * Math.cos(Math.toRadians(angle)));
                double z = location.getZ() + (radius * Math.sin(Math.toRadians(angle)));
                location.getWorld().spawnParticle(particle, x, y + location.getY(), z, 1, 0, 0, 0, 0);
            }
        }
    }

    default void vfxParticleSpiral(Location center, int radius, int height, Particle type) {
        double angle = 0;
        for (int i = 0; i <= height; i++) {
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            center.getWorld().spawnParticle(type, x, +center.getY(), z, 1, 0, 0, 0, 0);
            angle += 0.1;
        }
    }


    default List<Location> vfxSelectionCube(Location corner1, Location corner2, double particleDistance) {
        List<Location> result = new ArrayList<Location>();
        World world = corner1.getWorld();
        double minX = Math.min(corner1.getX(), corner2.getX());
        double minY = Math.min(corner1.getY(), corner2.getY());
        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxX = Math.max(corner1.getX(), corner2.getX());
        double maxY = Math.max(corner1.getY(), corner2.getY());
        double maxZ = Math.max(corner1.getZ(), corner2.getZ());

        for (double x = minX; x <= maxX; x += particleDistance) {
            for (double y = minY; y <= maxY; y += particleDistance) {
                for (double z = minZ; z <= maxZ; z += particleDistance) {
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


    default void riftResistCheckAndTrigger(Player p, int duration, int amplifier) {
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.24f);
        p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 1000f, 0.01f);
        p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1000f, 0.01f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, duration, amplifier, true, false, false));
    }

    default void vfxXP(Player p, Location l, int amt) {
        p.spawnParticle(Particle.ENCHANTMENT_TABLE, l, Math.min(amt / 10, 20), 0.5, 0.5, 0.5, 1);
    }

    default void vfxXP(Location l) {
        l.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, l.add(0, 1.7, 0), 3, 0.1, 0.1, 0.1, 3);
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
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
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
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }

        is.setItemMeta(im);
        p.getInventory().setItemInOffHand(is);
    }

    default void setExp(Player p, int exp) {
        p.setExp(0);
        p.setLevel(0);
        p.setTotalExperience(0);

        if (exp <= 0) {
            return;
        }

        giveExp(p, exp);
    }

    default void giveExp(Player p, int exp) {
        while (exp > 0) {
            int xp = getExpToLevel(p) - getExp(p);
            if (xp > exp) {
                xp = exp;
            }
            p.giveExp(xp);
            exp -= xp;
        }
    }

    default void takeExp(Player p, int exp) {
        takeExp(p, exp, true);
    }

    default void takeExp(Player p, int exp, boolean fromTotal) {
        int xp = getTotalExp(p);

        if (fromTotal) {
            xp -= exp;
        } else {
            int m = getExp(p) - exp;
            if (m < 0) {
                m = 0;
            }
            xp -= getExp(p) + m;
        }

        setExp(p, xp);
    }

    default int getExp(Player p) {
        return (int) (getExpToLevel(p) * p.getExp());
    }

    default int getTotalExp(Player p) {
        return getTotalExp(p, false);
    }

    default int getTotalExp(Player p, boolean recalc) {
        if (recalc) {
            recalcTotalExp(p);
        }
        return p.getTotalExperience();
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

    /**
     * Takes a custom amount of the item stack exact type (Ignores the item amount)
     *
     * @param inv    the inv
     * @param is     the item ignore the amount
     * @param amount the amount to use
     * @return true if taken, false if not (missing)
     */
    default boolean takeAll(Inventory inv, ItemStack is, int amount) {
        ItemStack isf = is.clone();
        isf.setAmount(amount);
        return takeAll(inv, is);
    }

    /**
     * Take one of an exact type ignoring the item stack amount
     *
     * @param inv the inv
     * @param is  the item ignoring the amount
     * @return true if taken, false if diddnt
     */
    default boolean takeOne(Inventory inv, ItemStack is, int amount) {
        return takeAll(inv, is, 1);
    }

    /**
     * Take a specific amount of an EXACT META TYPE from an inventory
     *
     * @param inv the inv
     * @param is  uses the amount
     * @return returns false if it couldnt get enough (and none was taken)
     */
    default boolean takeAll(Inventory inv, ItemStack is) {
        ItemStack[] items = inv.getStorageContents();

        int take = is.getAmount();

        for (int ii = 0; ii < items.length; ii++) {
            ItemStack i = items[ii];

            if (i == null) {
                continue;
            }

            if (i.isSimilar(is)) {
                if (take > i.getAmount()) {
                    i.setAmount(i.getAmount() - take);
                    items[ii] = i;
                    take = 0;
                    break;
                } else {
                    items[ii] = null;
                    take -= i.getAmount();
                }
            }
        }

        if (take > 0) {
            return false;
        }

        inv.setStorageContents(items);
        return true;
    }
}
