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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SeaborneFishWhisperer extends SimpleAdaptation<SeaborneFishWhisperer.Config> {
  public static final Id ID = Id.adapt("seaborne/fish_whisperer");
  public static final PlayerPreference<CommonPreferences.Toggle> ATTRACTION = CommonPreferences.toggle("attraction", Text.key("adapt.seabornefishwhisperer.preferences.preference_attraction"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ASSISTANCE = CommonPreferences.toggle("assistance", Text.key("adapt.seabornefishwhisperer.preferences.preference_assistance"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> LUCK = CommonPreferences.toggle("luck", Text.key("adapt.seabornefishwhisperer.preferences.preference_luck"), CommonPreferences.Toggle.ON);

  private static final int MAX_FISH = 12;
  private static final long CHARM_SESSION_GAP_MILLIS = 12_000L;
  private final Map<UUID, Long> charmedRecently = new ConcurrentHashMap<>();

  public SeaborneFishWhisperer() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.seaborn.fish_whisperer");
    setIcon(Id.minecraft("tropical_fish"));
    setInterval(4000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("tropical_fish"))
        .key("challenge_seaborne_charm_2k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("cod"))
            .key("challenge_seaborne_charm_20k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_charm_2k", "seaborne.fish-whisperer.charmed", 2000, 300);
    registerMilestone("challenge_seaborne_charm_20k", "seaborne.fish-whisperer.charmed", 20000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, ATTRACTION, ASSISTANCE, LUCK);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    SeaborneSeaLife.removeLuck(this, player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, SeaborneSeaLife.luckAmount(level, getMaxLevel()), 1);
    statLore(v, Format.f(getSchoolRange(level), 1), 2);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    charmedRecently.values().removeIf(at -> now - at >= CHARM_SESSION_GAP_MILLIS);
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      withPlayerThread(player, () -> {
        if (!player.isOnline()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        if (preferenceEnabled(player, LUCK)) {
          applyLuck(player, level);
        }
        if (preferenceEnabled(player, ATTRACTION) && (player.isInWater() || player.isSwimming())) {
          schoolFish(player, level);
        }
      });
    }
  }

  private void applyLuck(PlayerRef player, int level) {
    SeaborneSeaLife.applyLuck(this, player, SeaborneSeaLife.luckAmount(level, getMaxLevel()));
  }

  private void schoolFish(PlayerRef player, int level) {
    double range = getSchoolRange(level);
    Loc center = player.location();
    Loc target = center.add(0D, 0.4D, 0D);
    int scheduled = 0;
    for (EntityRef fish : center.world().entitiesIn(Box.around(center.position(), range, range, range),
        (EntityRef entity) -> entity.has(EntityTrait.FISH))) {
      if (AdaptApi.scheduler().runEntity(fish, () -> nudgeAndCreditFish(player, fish, target, level))) {
        scheduled++;
      }

      if (scheduled >= MAX_FISH) {
        break;
      }
    }
  }

  private void nudgeAndCreditFish(PlayerRef player, EntityRef fish, Loc target, int level) {
    if (!nudgeFish(fish, target, level)) {
      return;
    }
    UUID fishId = fish.uuid();
    long now = System.currentTimeMillis();
    Long lastCharm = charmedRecently.put(fishId, now);
    if (!startsNewCharmSession(lastCharm, now)) {
      return;
    }
    if (!AdaptApi.scheduler().runEntity(player, () -> creditCharmedFish(player))) {
      charmedRecently.remove(fishId, now);
    }
  }

  private void creditCharmedFish(PlayerRef player) {
    if (!player.isOnline() || !isRuntimeRegistered() || getActiveLevel(player) <= 0) {
      return;
    }
    addStat(player, "seaborne.fish-whisperer.charmed", 1);
  }

  private boolean nudgeFish(EntityRef fish, Loc target, int level) {
    if (!fish.isValid() || fish.isDead() || !target.sameWorld(fish.location())) {
      return false;
    }

    Vec3 toTarget = target.position().subtract(fish.location().position());
    double distance = toTarget.length();
    if (distance < 1.0D) {
      return false;
    }

    Vec3 pull = toTarget.normalize().multiply(getSchoolPull(level));
    Vec3 current = fish.velocity();
    Vec3 updated = current.multiply(0.5D).add(pull);
    if (!hasVelocityChange(current, updated)) {
      return false;
    }
    fish.setVelocity(updated);
    fx(fish.location(), FxPriority.AMBIENT)
        .particle(Particles.BUBBLE, 2, 0D, 0D, 0D, 0.1D, 0.02D);
    return true;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !(e.entity() instanceof MobRef victim)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!preferenceEnabled(p, ASSISTANCE)) {
      return;
    }
    SeaborneSeaLife.rally(this, victim, getAssistRange(level), getConfig().dolphinChargeStrength);
  }

  private double getSchoolRange(int level) {
    return schoolRange(getConfig().schoolRangeBase, getConfig().schoolRangeFactor, getLevelPercent(level));
  }

  private double getSchoolPull(int level) {
    return getConfig().schoolPullBase + (getLevelPercent(level) * getConfig().schoolPullFactor);
  }

  private double getAssistRange(int level) {
    return getConfig().assistRangeBase + (getLevelPercent(level) * getConfig().assistRangeFactor);
  }

  static double schoolRange(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  static boolean startsNewCharmSession(Long lastCharm, long now) {
    return lastCharm == null || now - lastCharm >= CHARM_SESSION_GAP_MILLIS;
  }

  static boolean hasVelocityChange(Vec3 current, Vec3 updated) {
    if (current == null || updated == null) {
      return false;
    }
    double differenceSquared = updated.subtract(current).lengthSquared();
    return Double.isFinite(differenceSquared) && differenceSquared > 1.0E-8D;
  }

  @ConfigDescription("Fish school toward you, dolphins and axolotls assist your hunts, and you fish with a permanent Luck of the Sea tier.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base block range fish school toward you within.", impact = "Higher values gather fish from farther away at low levels.")
    double schoolRangeBase = 6;
    @ConfigDoc(value = "Additional fish schooling range gained across levels.", impact = "Higher values gather fish from much farther at higher levels.")
    double schoolRangeFactor = 8;
    @ConfigDoc(value = "Base velocity pull applied to schooling fish.", impact = "Higher values pull fish toward you more strongly at low levels.")
    double schoolPullBase = 0.12;
    @ConfigDoc(value = "Additional schooling pull gained across levels.", impact = "Higher values pull fish more strongly at higher levels.")
    double schoolPullFactor = 0.18;
    @ConfigDoc(value = "Base block range dolphins and axolotls assist within.", impact = "Higher values recruit sea creatures from farther away at low levels.")
    double assistRangeBase = 8;
    @ConfigDoc(value = "Additional assist recruitment range gained across levels.", impact = "Higher values recruit sea creatures from much farther at higher levels.")
    double assistRangeFactor = 8;
    @ConfigDoc(value = "Velocity strength dolphins use to charge your combat target.", impact = "Higher values ram dolphins into your target harder; 0 disables the charge motion.")
    double dolphinChargeStrength = 0.9;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
