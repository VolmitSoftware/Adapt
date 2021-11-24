package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.naming.Name;
import java.util.UUID;

public class RangedArrowRecovery extends SimpleAdaptation<RangedArrowRecovery.Config> {
    private final KMap<UUID, Integer> arrows = new KMap<>();

    public RangedArrowRecovery() {
        super("arrow-recovery");
        setDescription("Recover Arrows after you have killed an enemy.");
        setIcon(Material.TIPPED_ARROW);
        setBaseCost(3);
        setMaxLevel(3);
        setInterval(5000);
        setInitialCost(6);
        setCostFactor(0.725);
    }

    private double getChance(double factor) {
        return factor;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getChance(getLevelPercent(level)), 0) + C.GRAY + " Chance to Recover");
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Projectile && ((Projectile) e.getDamager()).getShooter() instanceof Player) {
            Player p = ((Player) ((Projectile) e.getDamager()).getShooter());

            if(getLevel(p) > 0) {
                if(e.getDamager() instanceof Arrow a && Math.random() < getChance(getLevelPercent(p))) {
                    int hits = 0;

                    if(a.getPierceLevel() > 0)
                    {
                        NamespacedKey k = new NamespacedKey(Adapt.instance, "arrow-hits");
                        hits = a.getPersistentDataContainer().getOrDefault(k, PersistentDataType.INTEGER, 0);
                        a.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, hits + 1);
                    }

                    if(hits + 1 >= a.getPierceLevel())
                    {
                        arrows.compute(e.getEntity().getUniqueId(), (k, v) -> {
                            if(v == null) {
                                return 1;
                            }

                            return v + 1;
                        });

                        if(hits > 1)
                        {
                            a.remove();
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        Integer c = arrows.remove(e.getEntity().getUniqueId());

        if(c != null) {
            e.getDrops().add(new ItemStack(Material.ARROW, c));
        }
    }

    @Override
    public void onTick() {

    }

    protected static class Config{}
}
