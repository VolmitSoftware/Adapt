package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.skill.SkillHerbalism;
import com.volmit.adapt.util.Cuboid;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HerbalismReplant extends SimpleAdaptation<HerbalismReplant.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public HerbalismReplant() {
        super("herbalism-replant");
        registerConfiguration(Config.class);
        setDescription("Right click a crop with a hoe to harvest & replant it.");
        setDisplayName("Replant");
        setIcon(Material.PUMPKIN_SEEDS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(6000);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private int getCooldown(double factor, int level) {
        if(level == 1) {
            return (int) getConfig().cooldownLvl1;
        }

        return (int) ((getConfig().baseCooldown - (getConfig().cooldownFactor * factor)) + getConfig().bonusCooldown);
    }

    private float getRadius(int lvl) {
        return lvl - getConfig().radiusSub;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if(e.getClickedBlock() == null) {
            return;
        }

        if(!(e.getClickedBlock().getBlockData() instanceof Ageable)) {
            return;
        }

        int lvl = getLevel(e.getPlayer());

        if(lvl > 0) {
            ItemStack right = e.getPlayer().getInventory().getItemInMainHand();
            ItemStack left = e.getPlayer().getInventory().getItemInOffHand();

            if(isTool(left) && isHoe(left) && !e.getPlayer().hasCooldown(left.getType())) {
                damageOffHand(e.getPlayer(), 1 + ((lvl - 1) * 7));
                e.getPlayer().setCooldown(left.getType(), getCooldown(getLevelPercent(e.getPlayer()), getLevel(e.getPlayer())));
            } else if(isTool(right) && isHoe(right) && !e.getPlayer().hasCooldown(right.getType())) {
                damageHand(e.getPlayer(), 1 + ((lvl - 1) * 7));
                e.getPlayer().setCooldown(right.getType(), getCooldown(getLevelPercent(e.getPlayer()), getLevel(e.getPlayer())));
            } else {
                return;
            }

            if(lvl > 1) {
                Cuboid c = new Cuboid(e.getClickedBlock().getLocation().clone().add(0.5, 0.5, 0.5));
                c = c.expand(Cuboid.CuboidDirection.Up, (int) Math.floor(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.Down, (int) Math.floor(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.North, Math.round(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.South, Math.round(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.East, Math.round(getRadius(lvl)));
                c = c.expand(Cuboid.CuboidDirection.West, Math.round(getRadius(lvl)));

                for (Block i : c) {
                    J.s(() -> hit(e.getPlayer(), i), M.irand(1, 6));
                }

                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_SHOVEL_FLATTEN, 1f, 0.66f);
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_BAMBOO_SAPLING_BREAK, 1f, 0.66f);
                e.getPlayer().spawnParticle(Particle.VILLAGER_HAPPY, e.getPlayer().getLocation().clone().add(0.5, 0.5, 0.5), getLevel(e.getPlayer()) * 3, 0.3 * getLevel(e.getPlayer()), 0.3 * getLevel(e.getPlayer()), 0.3 * getLevel(e.getPlayer()), 0.9);
            } else {
                hit(e.getPlayer(), e.getClickedBlock());
            }
        }
    }

    private void hit(Player p, Block b) {
        if(b != null && b.getBlockData() instanceof Ageable && getLevel(p) > 0) {
            Ageable aa = (Ageable) b.getBlockData();

            if(aa.getAge() == 0) {
                return;
            }

            xp(p,b.getLocation().clone().add(0.5, 0.5, 0.5), ((SkillHerbalism.Config) getSkill().getConfig()).harvestPerAgeXP * aa.getAge());
            xp(p, b.getLocation().clone().add(0.5, 0.5, 0.5), ((SkillHerbalism.Config) getSkill().getConfig()).plantCropSeedsXP);
            b.breakNaturally();

            aa.setAge(0);
            J.s(() -> {
                b.setBlockData(aa, true);
            });

            getPlayer(p).getData().addStat("harvest.blocks", 1);
            getPlayer(p).getData().addStat("harvest.planted", 1);

            if(M.r(1D / (double) getLevel(p))) {
                p.getWorld().playSound(b.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 0.7f);
            }
        }
    }

    @Override
    public void addStats(int level, Element v) {

    }

    @Override
    public void onTick() {

    }
    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 3;
        int initialCost = 4;
        double costFactor = 2.325;
        double cooldownLvl1 = 2;
        double baseCooldown = 30;
        double cooldownFactor = 30;
        double bonusCooldown = 20;
        int radiusSub = 1;
    }
}
