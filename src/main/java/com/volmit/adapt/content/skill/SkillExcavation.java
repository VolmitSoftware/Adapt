package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.excavation.ExcavationDropToInventory;
import com.volmit.adapt.content.adaptation.excavation.ExcavationHaste;
import com.volmit.adapt.content.adaptation.excavation.ExcavationOmniTool;
import com.volmit.adapt.content.adaptation.herbalism.HerbalismDropToInventory;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillExcavation extends SimpleSkill<SkillExcavation.Config> {
    public SkillExcavation() {
        super("excavation", Adapt.dLocalize("Skill", "Excavation", "Icon"));
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Skill", "Excavation", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Excavation", "Name"));
        setColor(C.YELLOW);
        setInterval(5251);
        setIcon(Material.DIAMOND_SHOVEL);
        registerAdaptation(new ExcavationHaste());
        registerAdaptation(new ExcavationOmniTool());
        registerAdaptation(new ExcavationDropToInventory());

    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (!e.isCancelled()) {
            if (e.getDamager() instanceof Player p) {
                AdaptPlayer a = getPlayer((Player) e.getDamager());
                ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();
                if (isShovel(hand)) {
                    getPlayer(p).getData().addStat("excavation.swings", 1);
                    getPlayer(p).getData().addStat("excavation.damage", e.getDamage());
                    xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().axeDamageXPMultiplier * e.getDamage());
                }
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (!e.isCancelled()) {

            if (isShovel(e.getPlayer().getInventory().getItemInMainHand())) {
                double v = getValue(e.getBlock().getType());
                getPlayer(e.getPlayer()).getData().addStat("excavation.blocks.broken", 1);
                getPlayer(e.getPlayer()).getData().addStat("excavation.blocks.value", getValue(e.getBlock().getBlockData()));
                J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), v)));
            }
        }
    }

    public double getValue(Material type) {
        double value = super.getValue(type) * getConfig().valueXPMultiplier;
        value += Math.min(getConfig().maxHardnessBonus, type.getHardness());
        value += Math.min(getConfig().maxBlastResistanceBonus, type.getBlastResistance());
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
        double valueXPMultiplier = 0.825;
        double axeDamageXPMultiplier = 13.26;
    }
}
