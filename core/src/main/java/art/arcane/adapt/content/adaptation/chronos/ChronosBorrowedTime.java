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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.damage.DamageDeferral;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChronosBorrowedTime extends SimpleAdaptation<ChronosBorrowedTime.Config> implements DamageDeferral {
  private static final int MAX_PENDING_HITS = 16;
  private static final DataKey<String> DEBT_STAMP = DataKey.adapt("chronos_borrowed_time_debt", DataCodecs.STRING);

  private final Map<UUID, Deque<DeferredDamage>> deferred = playerState();
  private final Map<UUID, Deque<PendingDamage>> pending = playerState();
  private final Set<UUID> settling = ConcurrentHashMap.newKeySet();
  private final ThreadLocal<Boolean> applyingPaybackDamage = new ThreadLocal<>();
  private final AtomicBoolean acceptingSettlements = new AtomicBoolean(true);

  public ChronosBorrowedTime() {
    super("chronos-borrowed-time");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("soul_sand"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("soul_sand"))
        .key("challenge_chronos_borrowed_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_borrowed_2500", "chronos.borrowed-time.damage-deferred", 2500, 900);
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingSettlements.set(true);
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      Tasks.runEntity(player, () -> restoreDeferredDamage(player));
    }
  }

  @Override
  public void unregister() {
    acceptingSettlements.set(false);
    settling.clear();
    deferred.clear();
    pending.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Math.round(getDeferFraction(level) * 100D) + "%", 1);
    statLore(v, C.YELLOW, "+ ", getConfig().paybackPulses + "s", 2);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.BORROWED_TIME_LORE3));
  }

  private double getDeferFraction(int level) {
    return Math.min(getConfig().maxDeferFraction,
        getConfig().baseDeferFraction + (Math.max(1, level) * getConfig().deferFractionPerLevel));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    UUID id = player.uuid();
    pending.remove(id);
    Deque<DeferredDamage> queue = deferred.remove(id);
    if (queue != null && !queue.isEmpty()) {
      persistDeferredDamage(player, queue);
    }
    settling.remove(id);
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    restoreDeferredDamage(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    PlayerRef player = e.player();
    UUID id = player.uuid();
    deferred.remove(id);
    pending.remove(id);
    settling.remove(id);
    player.data().remove(DEBT_STAMP);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }
    if (e.isCancelled() || isApplyingPaybackDamage()) {
      return;
    }

    UUID id = p.uuid();

    double finalDamage = e.finalDamage();
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

    Deque<PendingDamage> hits = pending.computeIfAbsent(id, unused -> new ConcurrentLinkedDeque<>());
    if (hits.size() >= MAX_PENDING_HITS) {
      return;
    }
    e.setDamage(Math.max(0D, e.damage() * (1D - fraction)));
    double deferredAmount = Math.max(0D, finalDamage - Math.max(0D, e.finalDamage()));
    if (deferredAmount <= 0D) {
      if (hits.isEmpty()) {
        pending.remove(id, hits);
      }
      return;
    }
    hits.add(new PendingDamage(e.dispatchId(), deferredAmount, Math.max(1, getConfig().paybackPulses)));
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void finishDamage(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef player)) {
      return;
    }
    UUID id = player.uuid();
    Deque<PendingDamage> hits = pending.get(id);
    if (hits == null) {
      return;
    }
    PendingDamage accepted = null;
    for (PendingDamage hit : hits) {
      if (hit.dispatchId() == e.dispatchId()) {
        hits.remove(hit);
        accepted = hit;
        break;
      }
    }
    if (hits.isEmpty()) {
      pending.remove(id, hits);
    }
    if (accepted == null || e.isCancelled()) {
      return;
    }
    double deferredAmount = accepted.amount();
    int pulses = accepted.pulses();
    Deque<DeferredDamage> queue = deferred.computeIfAbsent(id, unused -> new ConcurrentLinkedDeque<>());
    DeferredDamage chunk = new DeferredDamage(deferredAmount / pulses, pulses);
    queue.add(chunk);
    persistDeferredDamage(player, queue);
    addStat(player, "chronos.borrowed-time.damage-deferred", deferredAmount);
    xpSilent(player, deferredAmount * getConfig().xpPerDeferredDamage, "chronos:borrowed-time");
    emitDeferralFeedback(player);
  }

  void emitDeferralFeedback(PlayerRef p) {
    Loc chest = p.location().add(0, 1.0, 0);
    fx(chest, FxPriority.TRANSITION)
        .particle(Particles.SOUL, 4, 0, 0.1D, 0, 0.2D, 0.01D)
        .dustRing(new Rgb(210, 150, 60), 0.6D, 8, 0.9F)
        .sound(Sounds.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 0.8F);
  }

  @Override
  public void onTick() {
    if (!acceptingSettlements.get() || deferred.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, Deque<DeferredDamage>> entry : deferred.entrySet()) {
      UUID id = entry.getKey();
      PlayerRef p = AdaptCore.platform().host().player(id);
      if (p == null) {
        deferred.remove(id, entry.getValue());
        settling.remove(id);
        continue;
      }

      Deque<DeferredDamage> queue = entry.getValue();
      if (!settling.add(id)) {
        continue;
      }

      boolean scheduled = Tasks.runEntity(p, () -> settleDeferredDamage(p, queue));
      if (!scheduled) {
        settling.remove(id);
      }
    }
  }

  private void settleDeferredDamage(PlayerRef player, Deque<DeferredDamage> queue) {
    UUID id = player.uuid();
    try {
      if (!acceptingSettlements.get() || deferred.get(id) != queue
          || !player.isOnline()) {
        return;
      }

      if (player.isDead() || player.health() <= 0D) {
        deferred.remove(id, queue);
        player.data().remove(DEBT_STAMP);
        return;
      }

      double damage = pendingPulseAmount(queue);
      if (damage <= 0D) {
        deferred.remove(id, queue);
        player.data().remove(DEBT_STAMP);
        return;
      }

      if (player.noDamageTicks() > player.maxNoDamageTicks() / 2D) {
        return;
      }

      try {
        applyDeferredDamage(player, damage);
      } catch (Throwable error) {
        AdaptLog.error("Borrowed Time could not settle deferred damage for " + id + ".");
        AdaptLog.error(error);
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
      emitPaybackFeedback(player, damage, cleared);
    } finally {
      settling.remove(id);
    }
  }

  void emitPaybackFeedback(PlayerRef player, double damage, boolean cleared) {
    int motes = (int) Math.max(2, Math.min(5, Math.round(damage)));
    float pitch = 0.5F + Math.min(0.9F, (float) (damage * 0.12D));
    fx(player.location().add(0, 1.0, 0), FxPriority.TRAIL)
        .particle(Particles.SOUL, motes, 0, 0.1D, 0, 0.2D, 0.02D)
        .sound(Sounds.BLOCK_SAND_STEP, 0.2F, pitch);

    if (cleared && !player.isDead()) {
      fx(player.location().add(0, 1.0, 0), FxPriority.TRANSITION)
          .column(Particles.END_ROD, 6, 1.2D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
    }
  }

  private boolean persistDeferredDamage(PlayerRef player, Deque<DeferredDamage> queue) {
    try {
      DataHolder data = player.data();
      if (queue.isEmpty()) {
        data.remove(DEBT_STAMP);
      } else {
        data.set(DEBT_STAMP, encodeDeferredDamage(queue));
      }
      return true;
    } catch (Throwable error) {
      AdaptLog.error("Borrowed Time could not persist deferred damage for "
          + player.uuid() + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void restoreDeferredDamage(PlayerRef player) {
    UUID id = player.uuid();
    if (!player.isOnline() || deferred.containsKey(id)) {
      return;
    }

    String encoded = player.data().get(DEBT_STAMP);
    if (encoded == null) {
      return;
    }

    try {
      Deque<DeferredDamage> queue = decodeDeferredDamage(encoded);
      if (queue.isEmpty()) {
        player.data().remove(DEBT_STAMP);
        return;
      }
      deferred.putIfAbsent(id, queue);
    } catch (RuntimeException error) {
      AdaptLog.error("Borrowed Time could not restore deferred damage for " + id + ".");
      AdaptLog.error(error);
      player.data().remove(DEBT_STAMP);
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

  private void applyDeferredDamage(PlayerRef player, double damage) {
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chronos XP granted per point of damage deferred.", impact = "Higher values make every deferred hit teach more Chronos; 0 disables the reward.")
    double xpPerDeferredDamage = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  private record PendingDamage(long dispatchId, double amount, int pulses) {
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
