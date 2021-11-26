package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Cuboid;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class HerbalismReplant extends SimpleAdaptation<HerbalismReplant.Config> {
    private final KList<Integer> holds = new KList<>();

    public HerbalismReplant() {
        super("replant");
        registerConfiguration(Config.class);
        setDescription("Right click a crop with a hoe to harvast & replant it.");
        setIcon(Material.PUMPKIN_SEEDS);
        setBaseCost(6);
        setMaxLevel(3);
        setInterval(6000);
        setInitialCost(4);
        setCostFactor(2.325);
    }

    private int getCooldown(double factor, int level) {
        if(level == 1) {
            return 2;
        }

        return (int) (30 - (30D * factor)) + 20;
    }

    private float getRadius(int lvl) {
        return lvl - 1;
    }

    @EventHandler
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

                for(Block i : new KList<>(c.iterator())) {
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

            b.breakNaturally();

            aa.setAge(0);
            J.s(() -> b.setBlockData(aa, true));

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
    }
}
