package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.events.api.ReflectiveHandler;
import art.arcane.adapt.util.reflect.events.api.entity.EndermanAttackPlayerEvent;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

public class StealthEnderVeil extends SimpleAdaptation<StealthEnderVeil.Config> {
  private final Cooldowns aggroPuffCooldown = cooldowns();

  public StealthEnderVeil() {
    super("stealth-enderveil");
    registerConfiguration(Config.class);
    setLocalizationKey("stealth.ender_veil");
    setIcon(Material.CARVED_PUMPKIN);
    setInterval(9182);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_EYE)
        .key("challenge_stealth_ender_veil_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_stealth_ender_veil_200", "stealth.ender-veil.stares-survived", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("stealth.ender_veil.lore" + (level < 2 ? 1 : 2)));
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      if (getActiveLevel(p) < 2 || !hasNearbyEnderman(p)) {
        continue;
      }

      double phase = ((System.currentTimeMillis() % 4000L) / 4000.0D) * Math.PI * 2.0D;
      Location orbit = p.getEyeLocation().add(Math.cos(phase) * 0.6D, 0.35D, Math.sin(phase) * 0.6D);
      fx(orbit, FxPriority.AMBIENT).particle(Particle.PORTAL, 1, 0, 0, 0, 0, 0);
    }
  }

  private boolean hasNearbyEnderman(Player p) {
    for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), 16, 16, 16)) {
      if (entity.getType() == EntityType.ENDERMAN) {
        return true;
      }
    }
    return false;
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onTarget(EntityTargetLivingEntityEvent event) {
    LivingEntity target = event.getTarget();
    if (target == null
        || target.getType() != EntityType.PLAYER
        || event.getEntityType() != EntityType.ENDERMAN
        || !(event.getTarget() instanceof Player player)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    if (level > 1 || player.isSneaking()) {
      event.setCancelled(true);
      addStat(player, "stealth.ender-veil.stares-survived", 1);
      suppressAggroFx(player, event.getEntity());
    }
  }

  @ReflectiveHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onTarget(EndermanAttackPlayerEvent event) {
    Player player = event.getPlayer();
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    if (level > 1 || player.isSneaking()) {
      event.setCancelled(true);
      addStat(player, "stealth.ender-veil.stares-survived", 1);
      fx(player.getEyeLocation(), FxPriority.COMBAT)
          .column(Particle.PORTAL, 12, 1.2D)
          .burst(Particle.REVERSE_PORTAL, 3, 0.2D)
          .chord(Sound.ENTITY_ENDERMAN_STARE, 0.35F, 1.5F, Sound.BLOCK_GLASS_BREAK, 0.2F, 2.0F);
    }
  }

  private void suppressAggroFx(Player player, Entity enderman) {
    if (!aggroPuffCooldown.isReady(player.getUniqueId(), 2000L)) {
      return;
    }

    aggroPuffCooldown.mark(player.getUniqueId());
    fx(enderman.getLocation().add(0, 2.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.2D)
        .sound(Sound.ENTITY_ENDERMAN_AMBIENT, 0.3F, 0.8F);
  }

  @ConfigDescription("Prevent Enderman aggression without wearing a pumpkin.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 6;
      costFactor = 1.0;
      maxLevel = 2;
      initialCost = 4;
    }
  }
}
