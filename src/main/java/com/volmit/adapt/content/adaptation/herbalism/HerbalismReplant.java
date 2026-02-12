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

package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.skill.SkillHerbalism;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public class HerbalismReplant extends SimpleAdaptation<HerbalismReplant.Config> {

    public HerbalismReplant() {
        super("herbalism-replant");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism", "replant", "description"));
        setDisplayName(Localizer.dLocalize("herbalism", "replant", "name"));
        setIcon(Material.PUMPKIN_SEEDS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(6090);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getRadius(level) + C.GRAY + Localizer.dLocalize("herbalism", "replant", "lore1"));
    }


    private int getCooldown(double factor, int level) {
        if (level == 1) {
            return (int) getConfig().cooldownLvl1;
        }

        return (int) ((getConfig().baseCooldown - (getConfig().cooldownFactor * factor)) + getConfig().bonusCooldown);
    }

    private float getRadius(int lvl) {
        return lvl - getConfig().radiusSub;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        SoundPlayer spw = SoundPlayer.of(p.getWorld());
        if (e.getClickedBlock() == null) {
            return;
        }
        if (!e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) { // you need to right-click to harvest!
            return;
        }

        if (!(e.getClickedBlock().getBlockData() instanceof Ageable)) {
            return;
        }

        int lvl = getLevel(p);

        if (lvl > 0) {
            ItemStack right = p.getInventory().getItemInMainHand();
            ItemStack left = p.getInventory().getItemInOffHand();

            if (isTool(left) && isHoe(left) && !p.hasCooldown(left.getType())) {
                damageOffHand(p, 1 + ((lvl - 1) * 7));
                p.setCooldown(left.getType(), getCooldown(getLevelPercent(p), getLevel(p)));
            } else if (isTool(right) && isHoe(right) && !p.hasCooldown(right.getType())) {
                damageHand(p, 1 + ((lvl - 1) * 7));
                p.setCooldown(right.getType(), getCooldown(getLevelPercent(p), getLevel(p)));
            } else {
                return;
            }

            if (lvl > 1) {
                Cuboid c = new Cuboid(e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5));
                c = c.expand(Cuboid.CuboidDirection.Up, (int) Math.floor(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.Down, (int) Math.floor(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.North, Math.round(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.South, Math.round(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.East, Math.round(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.West, Math.round(getRadius(lvl)));

                for (Block i : c) {
                    J.s(() -> hit(p, i), M.irand(1, 6));
                }
                spw.play(p.getLocation(), Sound.ITEM_SHOVEL_FLATTEN, 1f, 0.66f);
                spw.play(p.getLocation(), Sound.BLOCK_BAMBOO_SAPLING_BREAK, 1f, 0.66f);
                if (getConfig().showParticles) {
                    p.spawnParticle(Particles.VILLAGER_HAPPY, p.getLocation().clone().add(0.5, 0.5, 0.5), getLevel(p) * 3, 0.3 * getLevel(p), 0.3 * getLevel(p), 0.3 * getLevel(p), 0.9);
                }
            } else {
                hit(p, e.getClickedBlock());
            }
        }
    }

    private void hit(Player p, Block b) {
        if (b != null && b.getBlockData() instanceof Ageable aa && hasAdaptation(p)) {
            if (aa.getAge() != aa.getMaximumAge()) {
                return;
            }

            xp(p, b.getLocation().clone().add(0.5, 0.5, 0.5), ((SkillHerbalism.Config) getSkill().getConfig()).harvestPerAgeXP * aa.getAge());
            xp(p, b.getLocation().clone().add(0.5, 0.5, 0.5), ((SkillHerbalism.Config) getSkill().getConfig()).plantCropSeedsXP);
            PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("herbalism");
            PlayerAdaptation adaptation = line != null ? line.getAdaptation("herbalism-drop-to-inventory") : null;
            if (adaptation != null && adaptation.getLevel() > 0) {
                Collection<ItemStack> items = b.getDrops();
                SoundPlayer sp = SoundPlayer.of(p);
                for (ItemStack i : items) {
                    sp.play(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.05f, 0.01f);
                    i.setAmount(1);
                    if (!p.getInventory().addItem(i).isEmpty()) {
                        p.getWorld().dropItem(p.getLocation(), i);
                    }
                }
                aa.setAge(0);
                J.s(() -> b.setBlockData(aa, true));

            } else {
                p.breakBlock(b);
            }

            aa.setAge(0);
            J.s(() -> b.setBlockData(aa, true));

            getPlayer(p).getData().addStat("harvest.blocks", 1);
            getPlayer(p).getData().addStat("harvest.planted", 1);

            if (M.r(1D / (double) getLevel(p))) {
                SoundPlayer spw = SoundPlayer.of(p.getWorld());
                spw.play(b.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 0.7f);
            }
        }
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
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Herbalism Replant adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 2.325;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Lvl1 for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownLvl1 = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseCooldown = 30;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Factor for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownFactor = 30;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Cooldown for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusCooldown = 20;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Sub for the Herbalism Replant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int radiusSub = 1;
    }
}
