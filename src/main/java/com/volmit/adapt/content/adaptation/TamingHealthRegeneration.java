package com.volmit.adapt.content.adaptation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import xyz.xenondevs.particle.ParticleEffect;

import java.util.Collection;
import java.util.UUID;

public class TamingHealthRegeneration extends SimpleAdaptation {
    private final UUID attUUID = UUID.nameUUIDFromBytes("health-boost".getBytes());
    private final String attid = "att-health-boost";
    private final KMap<UUID, Long> lastDamage = new KMap<>();

    public TamingHealthRegeneration() {
        super("tame-health-regeneration");
        setDescription("Increase your tamed animal health.");
        setIcon(Material.GOLDEN_APPLE);
        setBaseCost(7);
        setMaxLevel(3);
        setInitialCost(8);
        setInterval(1000);
        setCostFactor(0.4);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if(e.getEntity() instanceof Tameable) {
            lastDamage.put(e.getEntity().getUniqueId(), M.ms());
        }

        if(e.getEntity() instanceof Tameable) {
            lastDamage.put(e.getDamager().getUniqueId(), M.ms());
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        lastDamage.remove(e.getEntity().getUniqueId());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRegenSpeed(level), 0) + C.GRAY + " HP/s");
    }

    private double getRegenSpeed(int level) {
        return ((getLevelPercent(level) * (getLevelPercent(level)) * 5) + 1);
    }

    @Override
    public void onTick() {
        for(UUID i : lastDamage.k()) {
            if(M.ms() - lastDamage.get(i) > 10000) {
                lastDamage.remove(i);
            }
        }

        for(World i : Bukkit.getServer().getWorlds()) {
            J.s(() -> {
                Collection<Tameable> gl = i.getEntitiesByClass(Tameable.class);

                J.a(() -> {
                    for(Tameable j : gl) {
                        if(lastDamage.containsKey(j.getUniqueId())) {
                            continue;
                        }

                        double mh = j.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        if(j.isTamed() && j.getOwner() instanceof Player && j.getHealth() < mh) {
                            Player p = (Player) j.getOwner();
                            int level = getLevel(p);

                            if(level > 0) {
                                J.s(() -> j.setHealth(Math.min(j.getHealth() + getRegenSpeed(level), mh)));
                                ParticleEffect.HEART.display(j.getLocation().clone().add(0, 1, 0), 0.55f, 0.37f, 0.55f, 0.3f, level, null);
                            }
                        }
                    }
                });
            });
        }
    }
}
