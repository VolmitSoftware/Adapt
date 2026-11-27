/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthGhostArmor extends SimpleAdaptation<StealthGhostArmor.Config> {
  public static final Id ID = Id.adapt("stealth/ghost_armor");
  private static final long ELIGIBILITY_RECHECK_MILLIS = 5_000L;

  private final Map<UUID, ArmorSession> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Long> eligibilityChecks = new ConcurrentHashMap<>();

  public StealthGhostArmor() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chainmail_helmet"));
    setInterval(5353);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_chestplate"))
        .key("challenge_stealth_ghost_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chainmail_chestplate"))
            .key("challenge_stealth_ghost_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_ghost_100", "stealth.ghost-armor.armor-consumed", 100, 300);
    registerMilestone("challenge_stealth_ghost_500", "stealth.ghost-armor.armor-consumed", 500, 1000);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    stopSession(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getMaxArmorPoints(getLevelPercent(level)), 0), 1);
    statLore(v, Format.f(getMaxArmorPerTick(getLevelPercent(level)), 1), 2);
  }

  public double getMaxArmorPoints(double factor) {
    return scaledArmorValue(getConfig().minArmor, getConfig().maxArmor, factor);
  }

  public double getMaxArmorPerTick(double factor) {
    return scaledArmorValue(getConfig().minArmorPerTick, getConfig().maxArmorPerTick, factor);
  }

  private static double scaledArmorValue(double first, double second, double factor) {
    double safeFirst = StealthArmorLayer.clampArmor(first);
    double safeSecond = StealthArmorLayer.clampArmor(second);
    double minimum = Math.min(safeFirst, safeSecond);
    double maximum = Math.max(safeFirst, safeSecond);
    double progress = Double.isFinite(factor) ? Math.max(0D, Math.min(1D, factor)) : 0D;
    return (minimum + progress * (maximum - minimum));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef player = e.player();
    AdaptApi.scheduler().runEntity(player, () -> startSessionIfEligible(player), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    PlayerRef player = e.player();
    AdaptApi.scheduler().runEntity(player, () -> startSessionIfEligible(player), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.to() == null
        || (e.from().blockX() == e.to().blockX()
        && e.from().blockY() == e.to().blockY()
        && e.from().blockZ() == e.to().blockZ())) {
      return;
    }

    PlayerRef player = e.player();
    UUID playerId = player.uuid();
    if (sessions.containsKey(playerId)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (now < eligibilityChecks.getOrDefault(playerId, 0L)) {
      return;
    }

    eligibilityChecks.put(playerId, now + ELIGIBILITY_RECHECK_MILLIS);
    startSessionIfEligible(player);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    stopSession(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    stopSession(e.player());
  }

  @Override
  public void onTick() {
    List<PlayerProfile> candidates = learnedCandidates(System.currentTimeMillis());
    for (PlayerProfile adaptPlayer : candidates) {
      PlayerRef player = adaptPlayer.player();
      if (player == null || !player.isOnline() || sessions.containsKey(player.uuid())) {
        continue;
      }
      AdaptApi.scheduler().runEntity(player, () -> startSessionIfEligible(player));
    }
  }

  @Override
  public void unregister() {
    for (ArmorSession session : sessions.values()) {
      session.refreshScheduled.set(false);
    }
    sessions.clear();
    eligibilityChecks.clear();
    super.unregister();
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)
        || e.damage() <= 0D
        || e.finalDamage() <= 0D) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      if (sessions.containsKey(p.uuid())) {
        stopSession(p);
      }
      return;
    }

    if (StealthArmorLayer.bypassesArmor(e)) {
      return;
    }

    ArmorSession session = sessions.computeIfAbsent(p.uuid(), key -> new ArmorSession(p));
    startScheduledRefresh(session, 1);
    double stored = StealthArmorLayer.clampArmor(session.armorAmount);
    if (!StealthArmorLayer.isConsumableCharge(stored)) {
      return;
    }

    StealthArmorLayer.clear(this, p);
    session.armorAmount = 0.0D;
    session.charged = false;
    HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
    double incomingDamage = e.damage();
    int damageXP = (int) Math.min(10, 2.5D * incomingDamage);
    xp(p, damageXP);
    addStat(p, "stealth.ghost-armor.armor-consumed", 1);
    double radius = Math.min(1.6D, 0.8D + (stored * 0.05D));
    fx(p.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .ring(Particles.CRIT, radius, 12, 0)
        .burst(Particles.CRIT, 4, 0.25D)
        .dustBurst(4, 0.4D, 1.0F)
        .chord(Sounds.ITEM_SHIELD_BREAK, 0.7F, 1.2F, Sounds.BLOCK_GLASS_BREAK, 0.5F, 0.8F, Sounds.BLOCK_ANVIL_LAND, 0.2F, 1.8F);
  }

  private void startSessionIfEligible(PlayerRef player) {
    if (!player.isOnline() || !hasActiveAdaptation(player)) {
      return;
    }

    ArmorSession session = sessions.computeIfAbsent(player.uuid(), key -> new ArmorSession(player));
    if (session.refreshScheduled.compareAndSet(false, true)) {
      refreshArmor(session);
    }
  }

  private void refreshArmor(ArmorSession session) {
    PlayerRef player = session.owner;
    UUID playerId = player.uuid();
    if (sessions.get(playerId) != session || !player.isOnline() || !hasActiveAdaptation(player)) {
      stopSession(player);
      return;
    }

    double levelPercent = getLevelPercent(player);
    double maximum = getMaxArmorPoints(levelPercent);
    double newAmount = StealthArmorLayer.charge(this, player, session.armorAmount, maximum,
        getMaxArmorPerTick(levelPercent));
    session.armorAmount = newAmount;
    updateChargeFeedback(player, session, maximum, newAmount);

    scheduleNextRefresh(session, refreshDelayTicks());
  }

  private void updateChargeFeedback(PlayerRef player, ArmorSession session, double armor, double newAmount) {
    boolean nowCharged = armor > 0 && newAmount >= armor - 1.0E-4D;
    boolean wasCharged = session.charged;
    session.charged = nowCharged;
    if (nowCharged && !wasCharged) {
      HudStatus.toggle(player.uuid(), this, true);
      timeline(player).duration(6).priority(FxPriority.TRANSITION).cullRadius(24)
          .frame((emit, tick, progress) -> {
            emit.ring(Particles.END_ROD, 0.6D, 8, 1.4D + (progress * 0.3D));
            if (tick == 0) {
              emit.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.8F);
            }
          }).start();
      return;
    }

    if (!nowCharged) {
      if (wasCharged) {
        HudStatus.clear(player.uuid(), this, HudGlyph.TOGGLE);
      }
      fx(player.location().add(0, 1.0D, 0), FxPriority.AMBIENT)
          .particle(Particles.WAX_ON, 1, 0, 0.3D, 0, 0.1D, 0.01D);
    }
  }

  private void startScheduledRefresh(ArmorSession session, int delayTicks) {
    if (!session.refreshScheduled.compareAndSet(false, true)) {
      return;
    }

    scheduleNextRefresh(session, delayTicks);
  }

  private void scheduleNextRefresh(ArmorSession session, int delayTicks) {
    if (!session.refreshScheduled.get() || sessions.get(session.owner.uuid()) != session) {
      return;
    }

    if (!AdaptApi.scheduler().runEntity(session.owner, () -> refreshArmor(session), Math.max(1, delayTicks))) {
      stopSession(session.owner);
    }
  }

  private int refreshDelayTicks() {
    return Math.max(1, (int) Math.ceil(Math.max(50L, getInterval()) / 50.0D));
  }

  private void stopSession(PlayerRef player) {
    UUID playerId = player.uuid();
    ArmorSession session = sessions.remove(playerId);
    eligibilityChecks.remove(playerId);
    if (session != null) {
      session.refreshScheduled.set(false);
      session.armorAmount = 0.0D;
      session.charged = false;
    }
    HudStatus.clear(playerId, this, HudGlyph.TOGGLE);
    AdaptAttributeService.get().removeAll(player, getId().toString());
  }

  private static class ArmorSession {
    private final PlayerRef owner;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private double armorAmount;
    private boolean charged;

    private ArmorSession(PlayerRef owner) {
      this.owner = owner;
    }
  }

  @ConfigDescription("Slowly build armor when not taking damage, consumed on the next hit.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Max Armor for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxArmor = 16;
    @ConfigDoc(value = "Controls Min Armor for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minArmor = 2;
    @ConfigDoc(value = "Controls Max Armor Per Tick for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxArmorPerTick = 3;
    @ConfigDoc(value = "Controls Min Armor Per Tick for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minArmorPerTick = 1;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 7;
      initialCost = 2;
    }
  }
}
