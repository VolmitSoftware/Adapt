package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
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
    setDescription(Localizer.dLocalize("rift.visage.description"));
    setDisplayName(Localizer.dLocalize("rift.visage.name"));
    setIcon(Material.POPPED_CHORUS_FRUIT);
    setBaseCost(getConfig().baseCost);
    setCostFactor(getConfig().costFactor);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_visage_100")
            .title(Localizer.dLocalize("advancement.challenge_rift_visage_100.title"))
            .description(Localizer.dLocalize("advancement.challenge_rift_visage_100.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                    .icon(Material.DRAGON_HEAD)
                    .key("challenge_rift_visage_1k")
                    .title(Localizer.dLocalize("advancement.challenge_rift_visage_1k.title"))
                    .description(Localizer.dLocalize("advancement.challenge_rift_visage_1k.description"))
                    .frame(AdaptAdvancementFrame.CHALLENGE)
                    .visibility(AdvancementVisibility.PARENT_GRANTED)
                    .build())
            .build());
    registerStatTracker(AdaptStatTracker.builder().advancement("challenge_rift_visage_100").goal(100).stat("rift.visage.stares-survived").reward(300).build());
    registerStatTracker(AdaptStatTracker.builder().advancement("challenge_rift_visage_1k").goal(1000).stat("rift.visage.stares-survived").reward(1000).build());
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.visage.lore1"));
  }

  @EventHandler
  public void onEntityTarget(EntityTargetEvent event) {
    Entity entity = event.getEntity();
    if (entity instanceof Enderman) {
      if (event.getTarget() instanceof Player player) {
        if (hasAdaptation(player) && hasEnderPearl(player)) {
          event.setCancelled(true);
          getPlayer(player).getData().addStat("rift.visage.stares-survived", 1);
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
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Prevents Endermen from becoming aggressive when you carry Enderpearls.")
  protected static class Config {
    @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = true;
    @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 8;
    @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0;
    @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 1;
    @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 2;
  }
}
