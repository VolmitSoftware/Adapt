package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EndermanAttackPlayerEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthEnderVeil extends SimpleAdaptation<StealthEnderVeil.Config> {
  public static final Id ID = Id.adapt("stealth/enderveil");
  private final Cooldowns aggroPuffCooldown = cooldowns();
  private final Map<UUID, VeilSession> ambientSessions = new ConcurrentHashMap<>();
  private static final Id PLAYER = Id.minecraft("player");
  private static final Id ENDERMAN = Id.minecraft("enderman");

  public StealthEnderVeil() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.stealth.ender_veil");
    setIcon(Id.minecraft("carved_pumpkin"));
    setInterval(9182);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_eye"))
        .key("challenge_stealth_ender_veil_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_stealth_ender_veil_200", "stealth.ender-veil.stares-survived", 200, 300);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    stopAmbientSession(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GRAY + (level < 2 ? Text.key("adapt.stealth.ender_veil.lore1") : Text.key("adapt.stealth.ender_veil.lore2")).resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    stopAmbientSession(event.player());
  }

  @Override
  public void unregister() {
    super.unregister();
    for (VeilSession session : ambientSessions.values()) {
      session.refreshScheduled.set(false);
    }
    ambientSessions.clear();
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onTarget(EntityTargetEvent event) {
    LivingRef target = event.target();
    if (target == null
        || !target.type().equals(PLAYER)
        || !event.entity().type().equals(ENDERMAN)
        || !(target instanceof PlayerRef player)) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    if (level > 1 || player.isSneaking()) {
      event.setCancelled(true);
      suppressAggroFx(player, event.entity());
      AdaptApi.scheduler().runEntity(player, () -> recordSuppressedAggro(player));
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onTarget(EndermanAttackPlayerEvent event) {
    PlayerRef player = event.player();
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    if (level > 1 || player.isSneaking()) {
      event.setCancelled(true);
      AdaptApi.scheduler().runEntity(player, () -> {
        recordSuppressedAggro(player);
        fx(player.eyeLocation(), FxPriority.COMBAT)
            .column(Particles.PORTAL, 12, 1.2D)
            .burst(Particles.REVERSE_PORTAL, 3, 0.2D)
            .chord(Sounds.ENTITY_ENDERMAN_STARE, 0.35F, 1.5F, Sounds.BLOCK_GLASS_BREAK, 0.2F, 2.0F);
      });
    }
  }

  private void recordSuppressedAggro(PlayerRef player) {
    addStat(player, "stealth.ender-veil.stares-survived", 1);
    if (getActiveLevel(player) >= 2) {
      startAmbientSession(player);
    }
  }

  private void startAmbientSession(PlayerRef player) {
    UUID playerId = player.uuid();
    VeilSession session = ambientSessions.computeIfAbsent(playerId, key -> new VeilSession(player));
    if (session.refreshScheduled.compareAndSet(false, true)) {
      refreshAmbient(session);
    }
  }

  private void refreshAmbient(VeilSession session) {
    PlayerRef player = session.owner;
    if (ambientSessions.get(player.uuid()) != session
        || !player.isOnline()
        || getActiveLevel(player) < 2
        || !hasNearbyEnderman(player)) {
      stopAmbientSession(player);
      return;
    }

    double phase = ((System.currentTimeMillis() % 4000L) / 4000.0D) * Math.PI * 2.0D;
    Loc orbit = player.eyeLocation().add(Math.cos(phase) * 0.6D, 0.35D, Math.sin(phase) * 0.6D);
    fx(orbit, FxPriority.AMBIENT).particle(Particles.PORTAL, 1, 0, 0, 0, 0, 0);

    int delayTicks = Math.max(1, (int) Math.ceil(Math.max(50L, getInterval()) / 50.0D));
    if (!AdaptApi.scheduler().runEntity(player, () -> refreshAmbient(session), delayTicks)) {
      stopAmbientSession(player);
    }
  }

  private boolean hasNearbyEnderman(PlayerRef player) {
    for (EntityRef entity : player.nearbyEntities(16, 16, 16)) {
      if (entity.type().equals(ENDERMAN)) {
        return true;
      }
    }
    return false;
  }

  private void stopAmbientSession(PlayerRef player) {
    VeilSession session = ambientSessions.remove(player.uuid());
    if (session != null) {
      session.refreshScheduled.set(false);
    }
  }

  private void suppressAggroFx(PlayerRef player, EntityRef enderman) {
    if (!aggroPuffCooldown.isReady(player.uuid(), 2000L)) {
      return;
    }

    aggroPuffCooldown.mark(player.uuid());
    fx(enderman.location().add(0, 2.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.2D)
        .sound(Sounds.ENTITY_ENDERMAN_AMBIENT, 0.3F, 0.8F);
  }

  private static class VeilSession {
    private final PlayerRef owner;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();

    private VeilSession(PlayerRef owner) {
      this.owner = owner;
    }
  }

  @ConfigDescription("Prevent Enderman aggression without wearing a pumpkin.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 2;
      initialCost = 1;
    }
  }
}
