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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChronosBorrowedTime extends SimpleAdaptation<ChronosBorrowedTime.Config> {
  private final Map<UUID, Deque<DeferredDamage>> deferred = playerState();
  private final Set<UUID> settling = ConcurrentHashMap.newKeySet();
  private final ThreadLocal<Boolean> applyingPaybackDamage = new ThreadLocal<>();
  private final AtomicBoolean acceptingSettlements = new AtomicBoolean(true);
  private final NamespacedKey debtStampKey;

  public ChronosBorrowedTime() {
    super("chronos-borrowed-time");
    registerConfiguration(Config.class);
    debtStampKey = new NamespacedKey(Adapt.instance, "chronos_borrowed_time_debt");
    setIcon(Material.SOUL_SAND);
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SOUL_SAND)
        .key("challenge_chronos_borrowed_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_borrowed_2500", "chronos.borrowed-time.damage-deferred", 2500, 900);
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingSettlements.set(true);
    for (Player player : Bukkit.getOnlinePlayers()) {
      J.runEntity(player, () -> restoreDeferredDamage(player));
    }
  }

  @Override
  public void unregister() {
    acceptingSettlements.set(false);
    settling.clear();
    deferred.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Math.round(getDeferFraction(level) * 100D) + "%", 1);
    statLore(v, C.YELLOW, "+ ", getConfig().paybackPulses + "s", 2);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.BORROWED_TIME_LORE3));
  }

  private double getDeferFraction(int level) {
    return Math.min(getConfig().maxDeferFraction,
        getConfig().baseDeferFraction + (Math.max(1, level) * getConfig().deferFractionPerLevel));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    UUID id = player.getUniqueId();
    Deque<DeferredDamage> queue = deferred.remove(id);
    if (queue != null && !queue.isEmpty()) {
      persistDeferredDamage(player, queue);
    }
    settling.remove(id);
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    restoreDeferredDamage(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    Player player = e.getEntity();
    UUID id = player.getUniqueId();
    deferred.remove(id);
    settling.remove(id);
    player.getPersistentDataContainer().remove(debtStampKey);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }
    if (isApplyingPaybackDamage()) {
      return;
    }

    UUID id = p.getUniqueId();

    double finalDamage = e.getFinalDamage();
    if (finalDamage < getConfig().minimumDeferDamage) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double fraction = getDeferFraction(level);
    if (fraction <= 0D) {
      return;
    }

    double deferredAmount = finalDamage * fraction;
    int pulses = Math.max(1, getConfig().paybackPulses);
    Deque<DeferredDamage> queue = deferred.computeIfAbsent(id, unused -> new ConcurrentLinkedDeque<>());
    DeferredDamage chunk = new DeferredDamage(deferredAmount / pulses, pulses);
    queue.add(chunk);
    if (!persistDeferredDamage(p, queue)) {
      queue.remove(chunk);
      if (queue.isEmpty()) {
        deferred.remove(id, queue);
      }
      return;
    }

    e.setDamage(Math.max(0D, e.getDamage() * (1D - fraction)));
    addStat(p, "chronos.borrowed-time.damage-deferred", deferredAmount);

    Location chest = p.getLocation().add(0, 1.0, 0);
    fx(chest, FxPriority.TRANSITION)
        .particle(Particle.SOUL, 4, 0, 0.1D, 0, 0.2D, 0.01D)
        .dustRing(Color.fromRGB(210, 150, 60), 0.6D, 8, 0.9F)
        .sound(Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 0.8F);
  }

  @Override
  public void onTick() {
    if (!acceptingSettlements.get() || deferred.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, Deque<DeferredDamage>> entry : deferred.entrySet()) {
      UUID id = entry.getKey();
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        deferred.remove(id, entry.getValue());
        settling.remove(id);
        continue;
      }

      Deque<DeferredDamage> queue = entry.getValue();
      if (!settling.add(id)) {
        continue;
      }

      boolean scheduled = J.runEntity(p, () -> settleDeferredDamage(p, queue));
      if (!scheduled) {
        settling.remove(id);
      }
    }
  }

  private void settleDeferredDamage(Player player, Deque<DeferredDamage> queue) {
    UUID id = player.getUniqueId();
    try {
      if (!acceptingSettlements.get() || deferred.get(id) != queue
          || !player.isOnline()) {
        return;
      }

      if (player.isDead() || player.getHealth() <= 0D) {
        deferred.remove(id, queue);
        player.getPersistentDataContainer().remove(debtStampKey);
        return;
      }

      double damage = pendingPulseAmount(queue);
      if (damage <= 0D) {
        deferred.remove(id, queue);
        player.getPersistentDataContainer().remove(debtStampKey);
        return;
      }

      try {
        applyDeferredDamage(player, damage);
      } catch (Throwable error) {
        Adapt.error("Borrowed Time could not settle deferred damage for " + id + ".");
        Adapt.error(error);
        return;
      }

      if (deferred.get(id) != queue) {
        return;
      }

      commitDebtPulse(queue);
      boolean cleared = queue.isEmpty();
      if (cleared) {
        deferred.remove(id, queue);
      }
      persistDeferredDamage(player, queue);

      int motes = (int) Math.max(2, Math.min(5, Math.round(damage)));
      float pitch = 0.5F + Math.min(0.9F, (float) (damage * 0.12D));
      fx(player.getLocation().add(0, 1.0, 0), FxPriority.TRAIL)
          .particle(Particle.SOUL, motes, 0, 0.1D, 0, 0.2D, 0.02D)
          .sound(Sound.BLOCK_SAND_STEP, 0.2F, pitch);

      if (cleared && !player.isDead()) {
        fx(player.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.2D)
            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
      }
    } finally {
      settling.remove(id);
    }
  }

  private boolean persistDeferredDamage(Player player, Deque<DeferredDamage> queue) {
    try {
      PersistentDataContainer data = player.getPersistentDataContainer();
      if (queue.isEmpty()) {
        data.remove(debtStampKey);
      } else {
        data.set(debtStampKey, PersistentDataType.STRING, encodeDeferredDamage(queue));
      }
      return true;
    } catch (Throwable error) {
      Adapt.error("Borrowed Time could not persist deferred damage for "
          + player.getUniqueId() + ".");
      Adapt.error(error);
      return false;
    }
  }

  private void restoreDeferredDamage(Player player) {
    UUID id = player.getUniqueId();
    if (!player.isOnline() || deferred.containsKey(id)) {
      return;
    }

    String encoded = player.getPersistentDataContainer().get(debtStampKey, PersistentDataType.STRING);
    if (encoded == null) {
      return;
    }

    try {
      Deque<DeferredDamage> queue = decodeDeferredDamage(encoded);
      if (queue.isEmpty()) {
        player.getPersistentDataContainer().remove(debtStampKey);
        return;
      }
      deferred.putIfAbsent(id, queue);
    } catch (RuntimeException error) {
      Adapt.error("Borrowed Time could not restore deferred damage for " + id + ".");
      Adapt.error(error);
      player.getPersistentDataContainer().remove(debtStampKey);
    }
  }

  static double pendingPulseAmount(Deque<DeferredDamage> queue) {
    double amount = 0D;
    for (DeferredDamage chunk : queue) {
      amount += chunk.perPulse();
    }
    return amount;
  }

  static void commitDebtPulse(Deque<DeferredDamage> queue) {
    Iterator<DeferredDamage> iterator = queue.iterator();
    while (iterator.hasNext()) {
      DeferredDamage chunk = iterator.next();
      if (chunk.consumePulse() <= 0) {
        iterator.remove();
      }
    }
  }

  static String encodeDeferredDamage(Deque<DeferredDamage> queue) {
    StringBuilder encoded = new StringBuilder();
    for (DeferredDamage chunk : queue) {
      if (encoded.length() > 0) {
        encoded.append(';');
      }
      encoded.append(chunk.perPulse()).append(',').append(chunk.pulsesRemaining());
    }
    return encoded.toString();
  }

  static Deque<DeferredDamage> decodeDeferredDamage(String encoded) {
    Deque<DeferredDamage> queue = new ConcurrentLinkedDeque<>();
    if (encoded == null || encoded.isBlank()) {
      return queue;
    }

    String[] chunks = encoded.split(";");
    for (String value : chunks) {
      String[] parts = value.split(",", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid deferred damage chunk");
      }
      double perPulse = Double.parseDouble(parts[0]);
      int pulsesRemaining = Integer.parseInt(parts[1]);
      if (!Double.isFinite(perPulse) || perPulse <= 0D || pulsesRemaining <= 0) {
        throw new IllegalArgumentException("Invalid deferred damage values");
      }
      queue.add(new DeferredDamage(perPulse, pulsesRemaining));
    }
    return queue;
  }

  private void applyDeferredDamage(Player player, double damage) {
    withPaybackDamage(() -> applyPlayerDamage(player, damage));
  }

  boolean isApplyingPaybackDamage() {
    return Boolean.TRUE.equals(applyingPaybackDamage.get());
  }

  void withPaybackDamage(Runnable damage) {
    Boolean previous = applyingPaybackDamage.get();
    applyingPaybackDamage.set(true);
    try {
      damage.run();
    } finally {
      if (previous == null) {
        applyingPaybackDamage.remove();
      } else {
        applyingPaybackDamage.set(previous);
      }
    }
  }

  @ConfigDescription("Defer a portion of incoming damage, paying it back in small ticks over the following seconds.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of incoming damage that is deferred.", impact = "Higher values move more damage into the payback window.")
    double baseDeferFraction = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra deferred fraction per adaptation level.", impact = "Higher values make leveling defer more damage.")
    double deferFractionPerLevel = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the deferred damage fraction.", impact = "Higher values allow more of each hit to be deferred.")
    double maxDeferFraction = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum final damage required before any deferral happens.", impact = "Higher values skip deferring small hits entirely.")
    double minimumDeferDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Number of one second pulses the deferred damage is paid back over.", impact = "Higher values spread payback thinner over a longer window.")
    int paybackPulses = 10;

    public Config() {
      baseCost = 6;
      costFactor = 0.42;
      initialCost = 5;
    }
  }

  static final class DeferredDamage {
    private final double perPulse;
    private int pulsesRemaining;

    DeferredDamage(double perPulse, int pulsesRemaining) {
      this.perPulse = perPulse;
      this.pulsesRemaining = pulsesRemaining;
    }

    double perPulse() {
      return perPulse;
    }

    int pulsesRemaining() {
      return pulsesRemaining;
    }

    int consumePulse() {
      pulsesRemaining--;
      return pulsesRemaining;
    }
  }
}
