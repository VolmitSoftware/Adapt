package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Cuboid;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.RNG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.particle.ParticleEffect;

public class SwordsMachete extends SimpleAdaptation {
    private final KList<Integer> holds = new KList<>();

    public SwordsMachete() {
        super("machete");
        setDescription("Cut through foliage with ease!");
        setIcon(Material.IRON_SWORD);
        setBaseCost(4);
        setMaxLevel(3);
        setInterval(5234);
        setInitialCost(7);
        setCostFactor(0.225);
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {

    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getRadius(level) + C.GRAY + " Slash Radius");
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " Chop Cooldown");
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " Tool Wear");
    }

    public double getRadius(int level) {
        return (getLevelPercent(level) * 2.36) + 0.6;
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(e.getHand() != null && e.getHand().equals(EquipmentSlot.HAND) && e.getAction().equals(Action.LEFT_CLICK_AIR) || e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            int dmg = 0;
            ItemStack is = e.getItem();
            if(isSword(is)) {
                if(!e.getPlayer().hasCooldown(is.getType()) && getLevel(e.getPlayer()) > 0) {
                    Location ctr = e.getPlayer().getEyeLocation().clone().add(e.getPlayer().getLocation().getDirection().clone().multiply(2.25)).add(0, -0.5, 0);

                    int lvl = getLevel(e.getPlayer());
                    Cuboid c = new Cuboid(ctr);
                    c = c.expand(Cuboid.CuboidDirection.Up, (int) Math.floor(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.Down, (int) Math.floor(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.North, (int) Math.round(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.South, (int) Math.round(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.East, (int) Math.round(getRadius(lvl)));
                    c = c.expand(Cuboid.CuboidDirection.West, (int) Math.round(getRadius(lvl)));

                    if(dmg > 0) {
                        return;
                    }

                    for(Block i : new KList<>(c.iterator())) {
                        if(M.r((getLevelPercent(lvl) * 2.8) / (i.getLocation().distanceSquared(ctr)))) {
                            if(i.getType().equals(Material.TALL_GRASS)
                                || i.getType().equals(Material.CACTUS)
                                || i.getType().equals(Material.SUGAR_CANE)
                                || i.getType().equals(Material.CARROT)
                                || i.getType().equals(Material.POTATO)
                                || i.getType().equals(Material.NETHER_WART)
                                || i.getType().equals(Material.GRASS)
                                || i.getType().equals(Material.FERN)
                                || i.getType().equals(Material.LARGE_FERN)
                                || i.getType().equals(Material.VINE)
                                || i.getType().equals(Material.ROSE_BUSH)
                                || i.getType().equals(Material.WITHER_ROSE)
                                || i.getType().equals(Material.ACACIA_LEAVES)
                                || i.getType().equals(Material.BIRCH_LEAVES)
                                || i.getType().equals(Material.DARK_OAK_LEAVES)
                                || i.getType().equals(Material.JUNGLE_LEAVES)
                                || i.getType().equals(Material.OAK_LEAVES)
                                || i.getType().equals(Material.SPRUCE_LEAVES)
                                || i.getType().equals(Material.BROWN_MUSHROOM)
                                || i.getType().equals(Material.RED_MUSHROOM)
                                || i.getType().equals(Material.DEAD_BUSH)
                                || i.getType().equals(Material.DANDELION)
                                || i.getType().equals(Material.TALL_SEAGRASS)
                                || i.getType().equals(Material.SEAGRASS)
                                || i.getType().equals(Material.WHITE_TULIP)
                                || i.getType().equals(Material.RED_TULIP)
                                || i.getType().equals(Material.PINK_TULIP)
                                || i.getType().equals(Material.ORANGE_TULIP)
                                || i.getType().equals(Material.LILY_OF_THE_VALLEY)
                                || i.getType().equals(Material.ALLIUM)
                                || i.getType().equals(Material.AZURE_BLUET)
                                || i.getType().equals(Material.SUNFLOWER)
                                || i.getType().equals(Material.CORNFLOWER)
                                || i.getType().equals(Material.CHORUS_FLOWER)
                                || i.getType().equals(Material.BAMBOO)

                            ) {
                                BlockBreakEvent ee = new BlockBreakEvent(i, e.getPlayer());
                                Bukkit.getPluginManager().callEvent(ee);

                                if(!ee.isCancelled()) {
                                    dmg += 1;
                                    J.s(() -> {
                                        i.breakNaturally();
                                        e.getPlayer().getWorld().playSound(i.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.4f, (float) Math.random() * 1.85f);
                                    }, RNG.r.i(0, (getMaxLevel() - lvl * 2) + 1));
                                }
                            }
                        }
                    }

                    if(dmg > 0) {
                        e.getPlayer().setCooldown(is.getType(), getCooldownTime(getLevelPercent(lvl)));
                        ParticleEffect.SWEEP_ATTACK.display(e.getPlayer().getEyeLocation().clone().add(e.getPlayer().getLocation().getDirection().clone().multiply(1.25)).add(0, -0.5, 0), 0f, 0f, 0f, 0.1f, 1, null);
                        e.getPlayer().getWorld().playSound(e.getPlayer().getEyeLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, (float) (Math.random() / 2) + 0.65f);
                        damageHand(e.getPlayer(), dmg * getDamagePerBlock(getLevelPercent(lvl)));
                        getSkill().xp(e.getPlayer(), dmg * 11.25);
                    }
                }
            }
        }
    }

    private int getCooldownTime(double levelPercent) {
        return ((int) ((1D - levelPercent) * 35)) + 7;
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (1 + (5 * ((1D - levelPercent))));
    }

    @Override
    public void onTick() {

    }
}
