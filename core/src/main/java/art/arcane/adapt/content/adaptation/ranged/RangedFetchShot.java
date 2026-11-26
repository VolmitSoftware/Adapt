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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class RangedFetchShot extends SimpleAdaptation<RangedFetchShot.Config> {
  private static final DataKey<Byte> LAUNCH_SNEAK_KEY = DataKey.adapt("fetch-launch-sneak", DataCodecs.BYTE);
  private static final Id FISHING_BOBBER = Id.minecraft("fishing_bobber");
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 8;

  private final RangedFetch fetch = new RangedFetch(this, new RangedFetch.Listener() {
    @Override
    public void delivered(PlayerRef player, Loc from) {
      fetchedOne(player, from);
    }

    @Override
    public void finished(PlayerRef player, int fetched) {
      fetchedAll(player, fetched);
    }
  });

  public RangedFetchShot() {
    super("ranged-fetch-shot");
    ProjectileCooperativeKeys.register(LAUNCH_SNEAK_KEY.id());
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("fishing_rod"));
    setInterval(2751);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("hopper"))
        .key("challenge_ranged_fetch_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chest"))
            .key("challenge_ranged_fetch_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_ranged_fetch_500", "ranged.fetch-shot.items-fetched", 500, 400);
    registerMilestone("challenge_ranged_fetch_5k", "ranged.fetch-shot.items-fetched", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK, RangedPreferences.ITEMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent event) {
    if (!(event.projectile().shooter() instanceof PlayerRef player)
        || !Tasks.isOwnedByCurrentRegion(player)
        || !hasActiveAdaptation(player)) {
      return;
    }
    event.projectile().data().set(LAUNCH_SNEAK_KEY, player.isSneaking() ? (byte) 1 : (byte) 0);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileHitEvent e) {
    ProjectileRef projectile = e.projectile();
    if (RangedHeartseeker.isSeekingProjectile(projectile)
        || projectile.type().equals(FISHING_BOBBER)
        || !(projectile.shooter() instanceof PlayerRef p)) {
      return;
    }

    Loc impact = projectile.location();
    Byte sneak = projectile.data().get(LAUNCH_SNEAK_KEY);
    boolean sneakingAtLaunch = sneak != null && sneak == 1;
    Tasks.runEntity(p, () -> startFetchOwned(p, impact, sneakingAtLaunch));
  }

  private void startFetchOwned(PlayerRef player, Loc impact, boolean sneakingAtLaunch) {
    if (!player.isOnline()) {
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0 || (preferenceEnabled(player, RangedPreferences.SNEAK) && !sneakingAtLaunch)) {
      return;
    }

    if (!canAccessChest(player, impact)) {
      return;
    }

    fetch.fetch(player, impact, new RangedFetch.Pull(getRadius(level), getCandidateLimit(), getAffectedLimit(),
        getTargetFxLimit(), (ItemRef stack) -> preference(player, RangedPreferences.ITEMS).accepts(stack.type())));
  }

  private void fetchedOne(PlayerRef player, Loc from) {
    Loc eye = player.eyeLocation();
    fx(from, FxPriority.TRAIL)
        .line(Particles.ENCHANTMENT_TABLE, eye.x(), eye.y(), eye.z(), 5)
        .chord(Sounds.ENTITY_ITEM_PICKUP, 0.6F, 1.5F, Sounds.ENTITY_ARROW_HIT_PLAYER, 0.3F, 1.8F);
  }

  private void fetchedAll(PlayerRef player, int fetched) {
    addStat(player, "ranged.fetch-shot.items-fetched", fetched);
    xp(player, fetched * getConfig().xpPerItemFetched);
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit()), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Shoot dropped items with projectiles to pull them straight into your inventory.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Item Fetched for the Ranged Fetch Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemFetched = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum dropped-item candidates inspected by one projectile impact.", impact = "Lower values reduce dense-item scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum dropped-item entities transferred by one projectile impact.", impact = "Lower values cap inventory and entity work in item piles; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum successful fetches that receive individual trail effects.", impact = "Lower values reduce particle and sound dispatch; values are clamped to an absolute maximum of 8.")
    int maxTargetFxPerActivation = 3;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
