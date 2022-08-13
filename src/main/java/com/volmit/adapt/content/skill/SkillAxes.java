package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.axe.AxeChop;
import com.volmit.adapt.content.adaptation.axe.AxeGroundSmash;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillAxes extends SimpleSkill<SkillAxes.Config> {
    public SkillAxes() {
        super("axes", Adapt.dLocalize("Skill", "Axes", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.YELLOW);
        setDescription(Adapt.dLocalize("Skill", "Axes", "Description1") + C.ITALIC + Adapt.dLocalize("Skill", "Axes", "Description2") + C.GRAY + Adapt.dLocalize("Skill", "Axes", "Description3"));
        setDisplayName(Adapt.dLocalize("Skill", "Axes", "Name"));
        setInterval(5251);
        setIcon(Material.GOLDEN_AXE);
        registerAdaptation(new AxeGroundSmash());
        registerAdaptation(new AxeChop());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
        if (!e.isCancelled()) {
            if (e.getDamager() instanceof Player p) {
                AdaptPlayer a = getPlayer((Player) e.getDamager());
                ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();

                if (isAxe(hand)) {
                    getPlayer(p).getData().addStat("axes.swings", 1);
                    getPlayer(p).getData().addStat("axes.damage", e.getDamage());
                    xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().axeDamageXPMultiplier * e.getDamage());
                }
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (isAxe(e.getPlayer().getInventory().getItemInMainHand())) {
            double v = getValue(e.getBlock().getType());
            getPlayer(e.getPlayer()).getData().addStat("axes.blocks.broken", 1);
            getPlayer(e.getPlayer()).getData().addStat("axes.blocks.value", getValue(e.getBlock().getBlockData()));
            J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), v)));
        }
    }

    public double getValue(Material type) {
        double value = super.getValue(type) * getConfig().valueXPMultiplier;
        value += Math.min(getConfig().maxHardnessBonus, type.getHardness());
        value += Math.min(getConfig().maxBlastResistanceBonus, type.getBlastResistance());

        if (type.name().endsWith("_LOG") || type.name().endsWith("_WOOD")) {
            value += getConfig().logOrWoodXPMultiplier;
        }
        if (type.name().endsWith("_LEAVES")) {
            value += getConfig().leavesMultiplier;
        }

        return value;
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
        double maxHardnessBonus = 9;
        double maxBlastResistanceBonus = 10;
        double logOrWoodXPMultiplier = 9.67;
        double leavesMultiplier = 3.11;
        double valueXPMultiplier = 0.225;
        double axeDamageXPMultiplier = 13.26;
    }
}
