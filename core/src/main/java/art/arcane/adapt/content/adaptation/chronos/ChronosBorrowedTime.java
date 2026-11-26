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
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;

public class ChronosBorrowedTime extends SimpleAdaptation<ChronosBorrowedTime.Config> implements DamageDeferral {
  private static final DataKey<String> DEBT_STAMP = DataKey.adapt("chronos_borrowed_time_debt", DataCodecs.STRING);

  private final Map<UUID, Deque<ChronosDebt.DeferredDamage>> deferred = playerState();
  private final ChronosDebt debt = new ChronosDebt(DEBT_STAMP, deferred, playerState(), new ChronosDebt.Hooks() {
    @Override
    public void pay(PlayerRef player, double damage) {
      applyPlayerDamage(player, damage);
    }

    @Override
    public void deferred(PlayerRef player, double amount) {
      addStat(player, "chronos.borrowed-time.damage-deferred", amount);
      xpSilent(player, amount * getConfig().xpPerDeferredDamage, "chronos:borrowed-time");
      emitDeferralFeedback(player);
    }

    @Override
    public void paid(PlayerRef player, double damage, boolean cleared) {
      emitPaybackFeedback(player, damage, cleared);
    }
  });

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
    debt.start();
  }

  @Override
  public void unregister() {
    debt.stop();
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

  /** The share of each hit this player defers right now, 0 when Borrowed Time is not active for them. */
  public double deferShare(PlayerRef player) {
    int level = getActiveLevel(player);
    return level <= 0 ? 0D : getDeferFraction(level);
  }

  /** Clears everything the player owes. */
  public void cancelDebt(PlayerRef player) {
    debt.cancel(player);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    debt.quit(e.player());
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    debt.restore(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    debt.cancel(e.player());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.isCancelled() || debt.isPaying()) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }
    debt.defer(p, e, getDeferFraction(level), getConfig().minimumDeferDamage, getConfig().paybackPulses);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void finishDamage(EntityDamageEvent e) {
    debt.finish(e);
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
    debt.tick();
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
}
