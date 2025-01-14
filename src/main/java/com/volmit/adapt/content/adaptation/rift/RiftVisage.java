package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;

public class RiftVisage extends SimpleAdaptation<RiftVisage.Config> {
  public RiftVisage() {
    super("rift-visage");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("rift", "visage", "description"));
    setDisplayName(Localizer.dLocalize("rift", "visage", "name"));
    setIcon(Material.POPPED_CHORUS_FRUIT);
    setBaseCost(getConfig().baseCost);
    setCostFactor(getConfig().costFactor);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setInterval(1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + Localizer.dLocalize("rift", "visage", "lore1"));
  }

  @EventHandler
  public void onEntityTarget(EntityTargetEvent event) {
    Entity entity = event.getEntity();
    if (entity instanceof Enderman) {
      if (event.getTarget() instanceof Player player) {
        if (hasAdaptation(player) && hasEnderPearl(player)) {
          event.setCancelled(true);
        }
      }
    }
  }

  private boolean hasEnderPearl(Player player) {
    for (ItemStack item : player.getInventory().getContents()) {
      if (item != null && item.getType() == Material.ENDER_PEARL) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isEnabled() {
    return !getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  protected static class Config {
    final boolean permanent = true;
    final boolean enabled = true;
    final int baseCost = 8;
    final double costFactor = 0;
    final int maxLevel = 1;
    final int initialCost = 2;
  }
}
