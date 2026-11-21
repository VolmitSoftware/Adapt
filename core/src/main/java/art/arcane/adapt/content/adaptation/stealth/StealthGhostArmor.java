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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.AttributeOperation;
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
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthGhostArmor extends SimpleAdaptation<StealthGhostArmor.Config> {
  private static final String SLOT_ARMOR = "armor";
  private static final long ELIGIBILITY_RECHECK_MILLIS = 5_000L;
  private static final Id BYPASSES_ARMOR = Id.minecraft("bypasses_armor");

  private final Map<UUID, ArmorSession> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Long> eligibilityChecks = new ConcurrentHashMap<>();

  public StealthGhostArmor() {
    super("stealth-ghost-armor");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chainmail_helmet"));
    setInterval(5353);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_chestplate"))
        .key("challenge_stealth_ghost_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chainmail_chestplate"))
            .key("challenge_stealth_ghost_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_ghost_100", "stealth.ghost-armor.armor-consumed", 100, 300);
    registerMilestone("challenge_stealth_ghost_500", "stealth.ghost-armor.armor-consumed", 500, 1000);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    stopSession(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getMaxArmorPoints(getLevelPercent(level)), 0), 1);
    statLore(v, Form.f(getMaxArmorPerTick(getLevelPercent(level)), 1), 2);
  }

  public double getMaxArmorPoints(double factor) {
    return scaledArmorValue(getConfig().minArmor, getConfig().maxArmor, factor);
  }

  public double getMaxArmorPerTick(double factor) {
    return scaledArmorValue(getConfig().minArmorPerTick, getConfig().maxArmorPerTick, factor);
  }

  static double nextArmorAmount(double current, double maximum, double increment) {
    double safeMaximum = clampArmor(maximum);
    double safeCurrent = Math.min(clampArmor(current), safeMaximum);
    double safeIncrement = Double.isFinite(increment) ? Math.max(0D, increment) : 0D;
    return Math.min(safeMaximum, safeCurrent + safeIncrement);
  }

  static boolean isConsumableCharge(double storedArmor) {
    return storedArmor > 0.1D;
  }

  private static double scaledArmorValue(double first, double second, double factor) {
    double safeFirst = clampArmor(first);
    double safeSecond = clampArmor(second);
    double minimum = Math.min(safeFirst, safeSecond);
    double maximum = Math.max(safeFirst, safeSecond);
    double progress = Double.isFinite(factor) ? Math.max(0D, Math.min(1D, factor)) : 0D;
    return M.lerp(minimum, maximum, progress);
  }

  private static double clampArmor(double value) {
    return Double.isFinite(value) ? Math.max(0D, Math.min(20D, value)) : 0D;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef player = e.player();
    Tasks.runEntity(player, () -> startSessionIfEligible(player), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    PlayerRef player = e.player();
    Tasks.runEntity(player, () -> startSessionIfEligible(player), 1);
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
    List<AdaptPlayer> candidates = learnedCandidates(System.currentTimeMillis());
    for (AdaptPlayer adaptPlayer : candidates) {
      PlayerRef player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline() || sessions.containsKey(player.uuid())) {
        continue;
      }
      Tasks.runEntity(player, () -> startSessionIfEligible(player));
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

    if (bypassesArmor(e)) {
      return;
    }

    ArmorSession session = sessions.computeIfAbsent(p.uuid(), key -> new ArmorSession(p));
    startScheduledRefresh(session, 1);
    double stored = clampArmor(session.armorAmount);
    if (!isConsumableCharge(stored)) {
      return;
    }

    AdaptAttributeService.get().removeAll(p, getName());
    session.armorAmount = 0.0D;
    session.charged = false;
    AbilityGlyphs.clear(p.uuid(), this, HudGlyph.TOGGLE);
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

  private boolean bypassesArmor(EntityDamageEvent event) {
    return AdaptCore.platform().host().registries().isTagged(RegistryKind.DAMAGE_TYPE, BYPASSES_ARMOR, event.damageType());
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
    double newAmount = nextArmorAmount(session.armorAmount, maximum, getMaxArmorPerTick(levelPercent));
    session.armorAmount = newAmount;
    if (newAmount > 0D) {
      AdaptAttributeService.get().apply(player, getName(), SLOT_ARMOR, Attributes.ARMOR, newAmount, AttributeOperation.ADD_VALUE);
    }
    updateChargeFeedback(player, session, maximum, newAmount);

    scheduleNextRefresh(session, refreshDelayTicks());
  }

  private void updateChargeFeedback(PlayerRef player, ArmorSession session, double armor, double newAmount) {
    boolean nowCharged = armor > 0 && newAmount >= armor - 1.0E-4D;
    boolean wasCharged = session.charged;
    session.charged = nowCharged;
    if (nowCharged && !wasCharged) {
      AbilityGlyphs.toggle(player.uuid(), this, true);
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
        AbilityGlyphs.clear(player.uuid(), this, HudGlyph.TOGGLE);
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

    if (!Tasks.runEntity(session.owner, () -> refreshArmor(session), Math.max(1, delayTicks))) {
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
    AbilityGlyphs.clear(playerId, this, HudGlyph.TOGGLE);
    AdaptAttributeService.get().removeAll(player, getName());
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Armor for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxArmor = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Armor for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minArmor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Armor Per Tick for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxArmorPerTick = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Armor Per Tick for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minArmorPerTick = 1;

    public Config() {
      baseCost = 3;
      costFactor = 0.335;
      maxLevel = 7;
      initialCost = 1;
    }
  }
}
