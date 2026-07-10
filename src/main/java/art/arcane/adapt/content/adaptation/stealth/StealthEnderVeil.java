package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
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
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthEnderVeil extends SimpleAdaptation<StealthEnderVeil.Config> {
  private final Cooldowns aggroPuffCooldown = cooldowns();
  private final Map<UUID, VeilSession> ambientSessions = new ConcurrentHashMap<>();

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

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    stopAmbientSession(event.getPlayer());
  }

  @Override
  public void unregister() {
    super.unregister();
    for (VeilSession session : ambientSessions.values()) {
      session.refreshScheduled.set(false);
    }
    ambientSessions.clear();
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
      suppressAggroFx(player, event.getEntity());
      J.runEntity(player, () -> recordSuppressedAggro(player));
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
      J.runEntity(player, () -> {
        recordSuppressedAggro(player);
        fx(player.getEyeLocation(), FxPriority.COMBAT)
            .column(Particle.PORTAL, 12, 1.2D)
            .burst(Particle.REVERSE_PORTAL, 3, 0.2D)
            .chord(Sound.ENTITY_ENDERMAN_STARE, 0.35F, 1.5F, Sound.BLOCK_GLASS_BREAK, 0.2F, 2.0F);
      });
    }
  }

  private void recordSuppressedAggro(Player player) {
    addStat(player, "stealth.ender-veil.stares-survived", 1);
    if (getActiveLevel(player) >= 2) {
      startAmbientSession(player);
    }
  }

  private void startAmbientSession(Player player) {
    UUID playerId = player.getUniqueId();
    VeilSession session = ambientSessions.computeIfAbsent(playerId, key -> new VeilSession(player));
    if (session.refreshScheduled.compareAndSet(false, true)) {
      refreshAmbient(session);
    }
  }

  private void refreshAmbient(VeilSession session) {
    Player player = session.owner;
    if (ambientSessions.get(player.getUniqueId()) != session
        || !player.isOnline()
        || getActiveLevel(player) < 2
        || !hasNearbyEnderman(player)) {
      stopAmbientSession(player);
      return;
    }

    double phase = ((System.currentTimeMillis() % 4000L) / 4000.0D) * Math.PI * 2.0D;
    Location orbit = player.getEyeLocation().add(Math.cos(phase) * 0.6D, 0.35D, Math.sin(phase) * 0.6D);
    fx(orbit, FxPriority.AMBIENT).particle(Particle.PORTAL, 1, 0, 0, 0, 0, 0);

    int delayTicks = Math.max(1, (int) Math.ceil(Math.max(50L, getInterval()) / 50.0D));
    if (!J.runEntity(player, () -> refreshAmbient(session), delayTicks)) {
      stopAmbientSession(player);
    }
  }

  private boolean hasNearbyEnderman(Player player) {
    for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
      if (entity.getType() == EntityType.ENDERMAN) {
        return true;
      }
    }
    return false;
  }

  private void stopAmbientSession(Player player) {
    VeilSession session = ambientSessions.remove(player.getUniqueId());
    if (session != null) {
      session.refreshScheduled.set(false);
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

  private static class VeilSession {
    private final Player owner;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();

    private VeilSession(Player owner) {
      this.owner = owner;
    }
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
