package com.volmit.adapt.content.skill;

import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;

public class SkillBrewing extends SimpleSkill<SkillBrewing.Config> {
    public SkillBrewing() {
        super("brewing", "\u2725");
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setInterval(5251);
        setIcon(Material.LINGERING_POTION);
    }

    @EventHandler
    public void on(PlayerItemConsumeEvent e) {
        if(e.getItem().getItemMeta() instanceof PotionMeta o)
        {
            xp(e.getPlayer(), e.getPlayer().getLocation(),
                getConfig().splashXP
                    + (getConfig().splashMultiplier * o.getCustomEffects().stream().mapToDouble(i -> (i.getAmplifier() + 1) * (i.getDuration() / 20D)).sum())
            + (getConfig().splashMultiplier * (o.getBasePotionData().isUpgraded() ? 50 : 25)));
        }
    }

    @EventHandler
    public void on(PotionSplashEvent e) {
        if(e.getPotion().getShooter() instanceof Player p) {
            AdaptPlayer a = getPlayer(p);
            getPlayer(p).getData().addStat("brewing.splashes", 1);
            xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().splashXP + (getConfig().splashMultiplier * e.getPotion().getEffects().stream().mapToDouble(i -> (i.getAmplifier() + 1) * (i.getDuration() / 20D)).sum()));
        }
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
        double splashXP = 115;
        double splashMultiplier = 8;;
    }
}
