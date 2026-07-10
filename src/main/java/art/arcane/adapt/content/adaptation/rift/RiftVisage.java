package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;

public class RiftVisage extends SimpleAdaptation<RiftVisage.Config> {
  private final Cooldowns aversionThrottle = cooldowns();

  public RiftVisage() {
    super("rift-visage");
    registerConfiguration(Config.class);
    setIcon(Material.POPPED_CHORUS_FRUIT);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_rift_visage_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DRAGON_HEAD)
            .key("challenge_rift_visage_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_visage_100", "rift.visage.stares-survived", 100, 300);
    registerMilestone("challenge_rift_visage_1k", "rift.visage.stares-survived", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.visage.lore1"));
  }

  @EventHandler
  public void onEntityTarget(EntityTargetEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof Enderman)) {
      return;
    }
    if (!(event.getTarget() instanceof Player player)) {
      return;
    }
    if (!hasActiveAdaptation(player) || !hasEnderPearl(player)) {
      return;
    }

    event.setCancelled(true);
    addStat(player, "rift.visage.stares-survived", 1);
    if (aversionThrottle.isReady(player.getUniqueId(), 2000L)) {
      aversionThrottle.mark(player.getUniqueId());
      Location eye = entity.getLocation().add(0, 1.5, 0);
      fx(eye, FxPriority.AMBIENT)
          .burst(Particles.SMOKE, 4, 0.2)
          .sound(Sound.ENTITY_ENDERMAN_AMBIENT, 0.3f, 0.7f);
      fx(player, FxPriority.AMBIENT)
          .particle(Particle.REVERSE_PORTAL, 2, 0, 1.0, 0, 0.2, 0.02);
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


  @ConfigDescription("Prevents Endermen from becoming aggressive when you carry Enderpearls.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 8;
      costFactor = 0;
      maxLevel = 1;
    }
  }
}
