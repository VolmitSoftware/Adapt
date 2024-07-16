package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.reflect.enums.Enchantments;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static xyz.xenondevs.particle.utils.MathUtils.RANDOM;

public class RangedArrowRecovery extends SimpleAdaptation<RangedArrowRecovery.Config> {
    private final Map<Arrow, Player> shotArrows;

    public RangedArrowRecovery() {
        super("ranged-recovery");
        registerConfiguration(RangedArrowRecovery.Config.class);
        setDescription(Localizer.dLocalize("ranged", "arrowrecovery", "description"));
        setDisplayName(Localizer.dLocalize("ranged", "arrowrecovery", "name"));
        setIcon(Material.ARROW);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        shotArrows = new HashMap<>();
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && hasAdaptation(player)) {
            if (!event.getBow().containsEnchantment(Enchantments.ARROW_INFINITE)) {
                if (event.getProjectile() instanceof Arrow arrow) {
                    shotArrows.put(arrow, player);
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            Player shooter = shotArrows.get(arrow);
            if (shooter != null && hasAdaptation(shooter)) {
                int level = getLevel(shooter);
                double chance = getConfig().hitChance[level - 1] / 100.0;
                if (RANDOM.nextDouble() < chance) {
                    ItemStack arrowStack = new ItemStack(Material.ARROW, 1);
                    shooter.getInventory().addItem(arrowStack);
                    Adapt.info("Arrow added to inventory.");
                }
            }
            shotArrows.remove(arrow);
        }
    }

    private double chancePerLevel(int level) {
        return (getConfig().hitChance[level - 1] / 100.0);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("ranged", "arrowrecovery", "lore1"));
        v.addLore(C.GREEN + Localizer.dLocalize("ranged", "arrowrecovery", "lore2") + chancePerLevel(level));
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 8;
        int initialCost = 5;
        double costFactor = 1.10;
        double[] hitChance = {10, 20, 30, 40, 50, 60, 70, 80};
    }
}