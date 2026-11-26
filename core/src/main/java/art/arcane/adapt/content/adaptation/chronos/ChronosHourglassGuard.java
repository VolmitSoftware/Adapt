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
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.DeathSaveCooldown;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.damage.PlayerDeathSave;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public class ChronosHourglassGuard extends SimpleAdaptation<ChronosHourglassGuard.Config> implements PlayerDeathSave {
  public static final Id SLOWNESS = Id.minecraft("slowness");
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", ChronosMessages.PREFERENCE_CHRONOSHOURGLASSGUARD_CLOCK_SOUNDS, CommonPreferences.Toggle.ON);

  private final ChronosHourglass hourglass = new ChronosHourglass(this, playerState());
  private final Map<UUID, Long> cooldownReadyNotify = playerState();

  public ChronosHourglassGuard() {
    super("chronos-hourglass-guard");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("totem_of_undying"))
        .key("challenge_chronos_hourglass_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_hourglass_10", "chronos.hourglass-guard.saves", 10, 800);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY, CLOCK_SOUNDS);
  }

  @Override
  public int deathSavePriority(PlayerRef player) {
    return preference(player, CommonPreferences.DEATH_SAVE_PRIORITY).ordinal();
  }

  @Override
  public int defaultOrder() {
    return 1;
  }

  @Override
  public boolean protectsFromDamage(EntityDamageEvent event) {
    return event.entity() instanceof PlayerRef player
        && hourglass.isInvulnerable(player.uuid(), M.ms());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, (getConfig().invulnerabilityMillis / 1000D) + "s", 1);
    statLore(v, C.RED, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.HOURGLASS_GUARD_LORE3));
  }

  private long getCooldownMillis(int level) {
    return Math.max(getConfig().minimumCooldownMillis,
        getConfig().baseCooldownMillis - (Math.max(1, level) * getConfig().cooldownReductionPerLevelMillis));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    hourglass.forget(id);
    cooldownReadyNotify.remove(id);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  @Override
  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    UUID id = p.uuid();
    long now = M.ms();
    if (hourglass.isInvulnerable(id, now)) {
      e.setCancelled(true);
      return;
    }

    if (p.health() - e.finalDamage() > 0D) {
      return;
    }

    if (!DeathSaveCooldown.HOURGLASS_GUARD.isReady(p, now)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    long cooldownMillis = getCooldownMillis(level);
    if (!DeathSaveCooldown.HOURGLASS_GUARD.activate(p, now, cooldownMillis)) {
      return;
    }
    hourglass.save(p, e, new ChronosHourglass.Save(getConfig().survivalHealth, getConfig().invulnerabilityMillis,
        cooldownMillis, getConfig().enemySlowRadius, getConfig().enemySlowTicks, getConfig().enemySlowAmplifier), now);

    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playRewindFinish(p);
    }

    cooldownReadyNotify.put(id, now > Long.MAX_VALUE - cooldownMillis ? Long.MAX_VALUE : now + cooldownMillis);
    addStat(p, "chronos.hourglass-guard.saves", 1);
    xp(p, p.location(), getConfig().xpOnSave + (level * getConfig().xpPerLevel));
  }

  /** The flash, bell and closing dome of a save. */
  public static void saveFx(Adaptation<?> owner, PlayerRef p) {
    Loc saveCenter = p.location();
    Fx.now(owner, saveCenter, FxPriority.GAMEPLAY)
        .particle(Particles.FLASH, 1, 0, 1.0D, 0, 0, 0)
        .sound(Sounds.BLOCK_BELL_USE, 0.7F, 0.5F);
    FxTimeline.follow(owner, p)
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(48)
        .frame((f, tick, progress) -> {
          f.dome(Particles.END_ROD, 2.6D - (2.0D * progress), 16);
          if (tick >= 3) {
            f.particle(Particles.TOTEM, 6, 0, 1.0D, 0, 0.25D, 0.05D);
          }
        })
        .start();
  }

  public static void slowFx(Adaptation<?> owner, LivingRef target) {
    Fx.now(owner, target.location(), FxPriority.COMBAT).ring(Particles.END_ROD, 0.9D, 3, 0.15D);
  }

  @Override
  public void onTick() {
    long now = M.ms();
    hourglass.prune(now);

    if (!cooldownReadyNotify.isEmpty()) {
      for (UUID id : cooldownReadyNotify.keySet()) {
        PlayerRef p = AdaptCore.platform().host().player(id);
        if (p == null) {
          cooldownReadyNotify.remove(id);
          continue;
        }

        if (cooldownReadyNotify.getOrDefault(id, Long.MAX_VALUE) <= now && cooldownReadyNotify.remove(id) != null) {
          Tasks.runEntity(p, () -> {
            if (!p.isOnline()) {
              return;
            }
            FxPresets.readyPing(this, p);
            if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
              ChronosSoundFX.playCooldownReady(p);
            }
          });
        }
      }
    }
  }

  @ConfigDescription("A killing blow instead leaves you at half a heart, granting brief invulnerability and slowing nearby enemies, on a long cooldown.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Hourglass Guard adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health the player is left with after a save.", impact = "Higher values leave the player healthier after cheating death.")
    double survivalHealth = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Invulnerability window in milliseconds after a save.", impact = "Higher values protect the player longer after a save.")
    long invulnerabilityMillis = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between saves.", impact = "Higher values force longer waits between saves.")
    long baseCooldownMillis = 480000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 180000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in blocks for the slow applied to nearby enemies on save.", impact = "Higher values slow enemies from further away up to the hard 12-block radius limit.")
    double enemySlowRadius = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the slow applied to nearby enemies.", impact = "Higher values keep enemies slowed longer.")
    int enemySlowTicks = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness amplifier applied to nearby enemies on save.", impact = "Higher values slow enemies more severely.")
    int enemySlowAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted when a save triggers.", impact = "Higher values grant more skill XP per save.")
    double xpOnSave = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra XP granted per adaptation level on save.", impact = "Higher values scale save XP with level faster.")
    double xpPerLevel = 10;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 3;
      initialCost = 6;
    }
  }

  public static boolean isOwnPet(LivingRef target, UUID playerId) {
    if (!target.has(EntityTrait.TAMEABLE)) {
      return false;
    }
    UUID tameOwner = target.tameOwner();
    return tameOwner != null && playerId.equals(tameOwner);
  }

  static final class HourglassWorkBudget {
    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private long windowStartedAt = Long.MIN_VALUE;
    private int acquired;

    HourglassWorkBudget(int limit, long windowMillis, LongSupplier clock) {
      this.limit = Math.max(1, limit);
      this.windowMillis = Math.max(1L, windowMillis);
      this.clock = clock;
    }

    synchronized int reserve(int requested) {
      long now = clock.getAsLong();
      if (windowStartedAt == Long.MIN_VALUE || now < windowStartedAt || now - windowStartedAt >= windowMillis) {
        windowStartedAt = now;
        acquired = 0;
      }
      int granted = Math.min(Math.max(0, requested), limit - acquired);
      acquired += granted;
      return granted;
    }
  }
}
