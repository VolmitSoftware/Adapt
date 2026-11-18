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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SwordsBloodyBlade extends SimpleAdaptation<SwordsBloodyBlade.Config> {
  private static final Rgb BLOOD = Rgb.of(0x9E1414);
  private static final long KILL_CREDIT_GRACE_MS = 4000L;
  private static final int BLEED_PERIOD_TICKS = 5;
  private static final Id MAGIC = Id.minecraft("magic");
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, BleedMark> bleedSource = playerState();

  public SwordsBloodyBlade() {
    super("sword-bloody-blade");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("red_dye"));
    setInterval(5534);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_bloody_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_swords_bloody_500", "swords.bloody-blade.bleed-damage", 500, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_swords_bloody_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_swords_bloody_kills_100", "swords.bloody-blade.bleed-kills", 100, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, "", 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getDurationOfEffect(level), 1), 2);
    statLore(v, C.RED, "* ", Form.duration(getCooldown(level), 1), 3);
  }

  public long getCooldown(int level) {
    return Math.max(getConfig().cooldown, getDurationOfEffect(level));
  }

  public long getDurationOfEffect(int level) {
    return getConfig().effectDuration * level;
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, this::isSword);
    if (combat == null || !preference(combat.attacker(), SwordPreferences.TARGETS).accepts(combat.target())) {
      return;
    }

    PlayerRef player = combat.attacker();
    UUID playerId = player.uuid();
    long durationMillis = getDurationOfEffect(combat.level());
    if (!cooldowns.isReady(playerId, getCooldown(combat.level()))) {
      return;
    }

    LivingRef target = combat.target();
    BleedPulse pulse = new BleedPulse(target, player, Math.max(0.01D, getConfig().damagePerBleedProc),
        bleedProcCount(durationMillis));
    if (!scheduleBleed(pulse)) {
      return;
    }

    cooldowns.mark(playerId);
    long now = System.currentTimeMillis();
    pruneExpired(now);
    bleedSource.put(target.uuid(), new BleedMark(playerId, now + durationMillis + KILL_CREDIT_GRACE_MS));
    fx(target.location().add(0, 1, 0), FxPriority.COMBAT)
        .dustBurst(BLOOD, 8, 0.3D, 1.1F)
        .particle(Particles.DAMAGE_INDICATOR, 3, 0, 0, 0, 0.1D, 0.05D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.6F, 0.8F, Sounds.BLOCK_HONEY_BLOCK_SLIDE, 0.3F, 0.6F);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    UUID victimId = e.entity().uuid();
    BleedMark mark = bleedSource.remove(victimId);
    if (mark == null || mark.expiresAt() < System.currentTimeMillis()) {
      return;
    }
    PlayerRef source = AdaptCore.platform().host().player(mark.source());
    if (source != null) {
      Tasks.runEntity(source, () -> {
        if (source.isOnline()) {
          addStat(source, "swords.bloody-blade.bleed-kills", 1);
        }
      });
    }
    fx(e.entity().location().add(0, 0.8D, 0), FxPriority.TRANSITION)
        .dustBurst(BLOOD, 10, 0.4D, 1.1F)
        .particle(Particles.SMOKE, 4, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.7F, 0.7F);
  }

  private void pruneExpired(long now) {
    bleedSource.values().removeIf(mark -> mark.expiresAt() < now);
  }

  static int bleedProcCount(long durationMillis) {
    long durationTicks = Math.max(1L, (Math.max(0L, durationMillis) + 49L) / 50L);
    return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (durationTicks + BLEED_PERIOD_TICKS - 1L) / BLEED_PERIOD_TICKS));
  }

  private boolean scheduleBleed(BleedPulse pulse) {
    return Tasks.runEntity(pulse.target(), () -> runBleedPulse(pulse), BLEED_PERIOD_TICKS);
  }

  private void runBleedPulse(BleedPulse pulse) {
    LivingRef target = pulse.target();
    if (pulse.remaining() <= 0 || !target.isValid() || target.isDead()) {
      return;
    }

    BleedAuthorization authorization = new BleedAuthorization(target.location(), target instanceof PlayerRef,
        isProtectedFriendly(null, target), target.tameOwner());
    if (!Tasks.runEntity(pulse.source(), () -> authorizeBleedPulse(pulse, authorization))) {
      return;
    }
  }

  private void authorizeBleedPulse(BleedPulse pulse, BleedAuthorization authorization) {
    PlayerRef source = pulse.source();
    if (!source.isOnline() || getActiveLevel(source) <= 0 || authorization.protectedFriendly()
        || source.uuid().equals(authorization.tameOwnerId())) {
      return;
    }

    boolean allowed = authorization.playerTarget()
        ? canPVP(source, authorization.location())
        : canPVE(source, authorization.location());
    if (!allowed) {
      return;
    }

    Tasks.runEntity(pulse.target(), () -> applyBleedPulse(pulse));
  }

  private void applyBleedPulse(BleedPulse pulse) {
    LivingRef target = pulse.target();
    if (!target.isValid() || target.isDead()) {
      return;
    }

    double before = target.health() + target.absorption();
    applyBleedDamage(pulse);
    double dealt = Math.max(0D, before - target.health() - target.absorption());
    if (dealt > 0D) {
      Tasks.runEntity(pulse.source(), () -> addStat(pulse.source(), "swords.bloody-blade.bleed-damage", dealt));
      fx(target.location().add(0, 0.8D, 0), FxPriority.COMBAT)
          .dustBurst(BLOOD, 4, 0.25D, 0.9F)
          .particle(Particles.DAMAGE_INDICATOR, 2, 0, 0, 0, 0.08D, 0.03D)
          .sound(Sounds.BLOCK_HONEY_BLOCK_SLIDE, 0.2F, 0.7F);
    }

    if (pulse.remaining() > 1 && target.isValid() && !target.isDead()) {
      scheduleBleed(new BleedPulse(target, pulse.source(), pulse.damage(), pulse.remaining() - 1));
    }
  }

  static void applyBleedDamage(BleedPulse pulse) {
    pulse.target().damage(pulse.damage(), MAGIC, pulse.source());
  }

  private record BleedMark(UUID source, long expiresAt) {
  }

  record BleedPulse(LivingRef target, PlayerRef source, double damage, int remaining) {
  }

  private record BleedAuthorization(Loc location, boolean playerTarget, boolean protectedFriendly,
                                    UUID tameOwnerId) {
  }



  @ConfigDescription("Sword strikes cause bleeding over time.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long cooldown = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Bleed Proc for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double damagePerBleedProc = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Effect Duration for the Swords Bloody Blade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long effectDuration = 1000;

    public Config() {
      baseCost = 7;
      costFactor = 0.325;
      maxLevel = 7;
      initialCost = 7;
    }

  }
}
