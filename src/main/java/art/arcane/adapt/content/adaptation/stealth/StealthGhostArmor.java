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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.tag.DamageTypeTags;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthGhostArmor extends SimpleAdaptation<StealthGhostArmor.Config> {
  private static final String SLOT_ARMOR = "armor";
  private static final long ELIGIBILITY_RECHECK_MILLIS = 5_000L;

  private final Map<UUID, ArmorSession> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Long> eligibilityChecks = new ConcurrentHashMap<>();

  public StealthGhostArmor() {
    super("stealth-ghost-armor");
    registerConfiguration(Config.class);
    setIcon(Material.CHAINMAIL_HELMET);
    setInterval(5353);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_CHESTPLATE)
        .key("challenge_stealth_ghost_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHAINMAIL_CHESTPLATE)
            .key("challenge_stealth_ghost_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_ghost_100", "stealth.ghost-armor.armor-consumed", 100, 300);
    registerMilestone("challenge_stealth_ghost_500", "stealth.ghost-armor.armor-consumed", 500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
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

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    Player player = e.getPlayer();
    J.runEntity(player, () -> startSessionIfEligible(player), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    Player player = e.getPlayer();
    J.runEntity(player, () -> startSessionIfEligible(player), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null
        || (e.getFrom().getBlockX() == e.getTo().getBlockX()
        && e.getFrom().getBlockY() == e.getTo().getBlockY()
        && e.getFrom().getBlockZ() == e.getTo().getBlockZ())) {
      return;
    }

    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
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

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    stopSession(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    stopSession(e.getEntity());
  }

  @Override
  public void onTick() {
    List<AdaptPlayer> candidates = learnedCandidates(System.currentTimeMillis());
    for (AdaptPlayer adaptPlayer : candidates) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline() || sessions.containsKey(player.getUniqueId())) {
        continue;
      }
      J.runEntity(player, () -> startSessionIfEligible(player));
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

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)
        || e.getDamage() <= 0D
        || e.getFinalDamage() <= 0D) {
      return;
    }

    if (!hasActiveAdaptation(p)) {
      if (sessions.containsKey(p.getUniqueId())) {
        stopSession(p);
      }
      return;
    }

    if (bypassesArmor(e)) {
      return;
    }

    ArmorSession session = sessions.computeIfAbsent(p.getUniqueId(), key -> new ArmorSession(p));
    startScheduledRefresh(session, 1);
    double stored = clampArmor(session.armorAmount);
    if (!isConsumableCharge(stored)) {
      return;
    }

    AdaptAttributeService.get().removeAll(p, getName());
    session.armorAmount = 0.0D;
    session.charged = false;
    double incomingDamage = e.getDamage();
    int damageXP = (int) Math.min(10, 2.5D * incomingDamage);
    xp(p, damageXP);
    addStat(p, "stealth.ghost-armor.armor-consumed", 1);
    double radius = Math.min(1.6D, 0.8D + (stored * 0.05D));
    fx(p.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
        .ring(Particle.CRIT, radius, 12, 0)
        .burst(Particle.CRIT, 4, 0.25D)
        .dustBurst(4, 0.4D, 1.0F)
        .chord(Sound.ITEM_SHIELD_BREAK, 0.7F, 1.2F, Sound.BLOCK_GLASS_BREAK, 0.5F, 0.8F, Sound.BLOCK_ANVIL_LAND, 0.2F, 1.8F);
  }

  private boolean bypassesArmor(EntityDamageEvent event) {
    return DamageTypeTags.BYPASSES_ARMOR != null
        && DamageTypeTags.BYPASSES_ARMOR.isTagged(event.getDamageSource().getDamageType());
  }

  private void startSessionIfEligible(Player player) {
    if (!player.isOnline() || !hasActiveAdaptation(player)) {
      return;
    }

    ArmorSession session = sessions.computeIfAbsent(player.getUniqueId(), key -> new ArmorSession(player));
    if (session.refreshScheduled.compareAndSet(false, true)) {
      refreshArmor(session);
    }
  }

  private void refreshArmor(ArmorSession session) {
    Player player = session.owner;
    UUID playerId = player.getUniqueId();
    if (sessions.get(playerId) != session || !player.isOnline() || !hasActiveAdaptation(player)) {
      stopSession(player);
      return;
    }

    double levelPercent = getLevelPercent(player);
    double maximum = getMaxArmorPoints(levelPercent);
    double newAmount = nextArmorAmount(session.armorAmount, maximum, getMaxArmorPerTick(levelPercent));
    session.armorAmount = newAmount;
    if (newAmount > 0D) {
      AdaptAttributeService.get().apply(player, getName(), SLOT_ARMOR, Attributes.ARMOR, newAmount, AttributeModifier.Operation.ADD_NUMBER);
    }
    updateChargeFeedback(player, session, maximum, newAmount);

    scheduleNextRefresh(session, refreshDelayTicks());
  }

  private void updateChargeFeedback(Player player, ArmorSession session, double armor, double newAmount) {
    boolean nowCharged = armor > 0 && newAmount >= armor - 1.0E-4D;
    boolean wasCharged = session.charged;
    session.charged = nowCharged;
    if (nowCharged && !wasCharged) {
      timeline(player).duration(6).priority(FxPriority.TRANSITION).cullRadius(24)
          .frame((emit, tick, progress) -> {
            emit.ring(Particles.END_ROD, 0.6D, 8, 1.4D + (progress * 0.3D));
            if (tick == 0) {
              emit.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.8F);
            }
          }).start();
      return;
    }

    if (!nowCharged) {
      fx(player.getLocation().add(0, 1.0D, 0), FxPriority.AMBIENT)
          .particle(Particle.WAX_ON, 1, 0, 0.3D, 0, 0.1D, 0.01D);
    }
  }

  private void startScheduledRefresh(ArmorSession session, int delayTicks) {
    if (!session.refreshScheduled.compareAndSet(false, true)) {
      return;
    }

    scheduleNextRefresh(session, delayTicks);
  }

  private void scheduleNextRefresh(ArmorSession session, int delayTicks) {
    if (!session.refreshScheduled.get() || sessions.get(session.owner.getUniqueId()) != session) {
      return;
    }

    if (!J.runEntity(session.owner, () -> refreshArmor(session), Math.max(1, delayTicks))) {
      stopSession(session.owner);
    }
  }

  private int refreshDelayTicks() {
    return Math.max(1, (int) Math.ceil(Math.max(50L, getInterval()) / 50.0D));
  }

  private void stopSession(Player player) {
    UUID playerId = player.getUniqueId();
    ArmorSession session = sessions.remove(playerId);
    eligibilityChecks.remove(playerId);
    if (session != null) {
      session.refreshScheduled.set(false);
      session.armorAmount = 0.0D;
      session.charged = false;
    }
    AdaptAttributeService.get().removeAll(player, getName());
  }

  private static class ArmorSession {
    private final Player owner;
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private double armorAmount;
    private boolean charged;

    private ArmorSession(Player owner) {
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
