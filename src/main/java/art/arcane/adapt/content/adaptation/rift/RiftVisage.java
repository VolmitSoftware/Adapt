package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.mutation.runtime.MutationUtilityTag;
import art.arcane.adapt.service.MutationRuntimeSVC;
import art.arcane.adapt.util.common.format.C;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RiftVisage extends SimpleAdaptation<RiftVisage.Config> {
  private static final long STARE_STAT_WINDOW_MILLIS = 10_000L;
  private final Cooldowns aversionThrottle = cooldowns();
  private final Map<UUID, Long> stareCredits = new ConcurrentHashMap<>();

  public RiftVisage() {
    super("rift-visage");
    registerConfiguration(Config.class);
    setIcon(Material.POPPED_CHORUS_FRUIT);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_rift_visage_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DRAGON_HEAD)
            .key("challenge_rift_visage_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_visage_100", "rift.visage.stares-survived", 100, 300);
    registerMilestone("challenge_rift_visage_1k", "rift.visage.stares-survived", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.VISAGE_LORE1));
  }

  @EventHandler
  public void onEntityTarget(EntityTargetEvent event) {
    Entity entity = event.getEntity();
    if (!(entity instanceof Enderman enderman)) {
      return;
    }
    if (!(event.getTarget() instanceof Player player)) {
      return;
    }
    if (!hasActiveAdaptation(player) || !hasEnderPearl(player)) {
      return;
    }

    event.setCancelled(true);
    emitFormulaUtility(player, enderman);
    long now = System.currentTimeMillis();
    Long lastCredit = stareCredits.get(enderman.getUniqueId());
    if (lastCredit == null || now - lastCredit >= STARE_STAT_WINDOW_MILLIS) {
      stareCredits.put(enderman.getUniqueId(), now);
      addStat(player, "rift.visage.stares-survived", 1);
    }
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

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    stareCredits.values().removeIf(at -> now - at >= STARE_STAT_WINDOW_MILLIS);
  }

  private void emitFormulaUtility(Player player, Enderman enderman) {
    MutationRuntimeSVC runtime = MutationRuntimeSVC.get();
    if (runtime != null) {
      runtime.emitAnomalyUtility(player, enderman, MutationUtilityTag.INTERRUPTION, 0.5D, false);
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
