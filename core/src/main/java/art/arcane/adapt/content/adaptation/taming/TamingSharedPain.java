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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class TamingSharedPain extends SimpleAdaptation<TamingSharedPain.Config> {
  private final TamePainShare painShare = new TamePainShare(this, cooldowns());

  public TamingSharedPain() {
    super("tame-shared-pain");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.shared_pain");
    setIcon(Id.minecraft("poppy"));
    setInterval(1700);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_taming_shared_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("totem_of_undying"))
            .key("challenge_taming_shared_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_shared_500", "taming.shared-pain.damage-taken", 500, 400);
    registerMilestone("challenge_taming_shared_5k", "taming.shared-pain.damage-taken", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.HEALTH);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getRedirectPercent(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.f(getPetHealthFloor(level), 1), 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef owner) || e.finalDamage() <= TamePainShare.MINIMUM_TRANSFER) {
      return;
    }

    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    double transferred = painShare.shareHit(e, owner, getRedirectPercent(level), getRadius(level),
        getPetHealthFloor(level), TamePainShare.petLimit(getConfig().maxPets));
    if (transferred <= TamePainShare.MINIMUM_TRANSFER) {
      return;
    }

    addStat(owner, "taming.shared-pain.damage-taken", transferred);
    xp(owner, transferred * getConfig().xpPerRedirectedDamage);
  }

  private double getRedirectPercent(int level) {
    return clampRedirectPercent(
        getConfig().redirectPercentBase + (getLevelPercent(level) * getConfig().redirectPercentFactor),
        getConfig().maxRedirectPercent);
  }

  public static double clampRedirectPercent(double calculated, double configuredMaximum) {
    return Math.max(0D, Math.min(1D, Math.min(calculated, configuredMaximum)));
  }

  private double getPetHealthFloor(int level) {
    return Math.max(1.0, getConfig().petHealthFloorBase + (getLevelPercent(level) * getConfig().petHealthFloorFactor));
  }

  private double getRadius(int level) {
    return Math.max(1.0, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  static void redirectFx(Adaptation<?> owner, Loc ownerChest) {
    Fx.now(owner, ownerChest, FxPriority.COMBAT)
        .particle(Particles.DAMAGE_INDICATOR, 3, 0, 0, 0, 0.15D, 0)
        .dustBurst(Rgb.of(0xF2C14E), 2, 0.3D, 1.0F)
        .chord(Sounds.BLOCK_AMETHYST_CLUSTER_HIT, 0.55F, 0.8F, Sounds.BLOCK_CONDUIT_ACTIVATE, 0.3F, 1.5F);
  }

  static void petPainFx(Adaptation<?> owner, Loc petChest, Loc ownerChest) {
    Fx.now(owner, petChest, FxPriority.COMBAT)
        .line(Particles.CRIT, ownerChest.x(), ownerChest.y(), ownerChest.z(), 6)
        .sound(Sounds.ENTITY_WOLF_WHINE, 0.4F, 1.35F);
  }

  @ConfigDescription("Spread part of your incoming damage across nearby owned companions without reducing them below their health floor.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Base for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectPercentBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Redirect Percent Factor for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double redirectPercentFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Redirect Percent for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRedirectPercent = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum health preserved on each companion before it stops receiving Shared Pain damage.", impact = "Higher values protect companions more aggressively but redirect less owner damage.")
    double petHealthFloorBase = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional companion health floor reached at maximum adaptation level.", impact = "Higher values preserve more companion health as Shared Pain levels up.")
    double petHealthFloorFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base radius searched for owned companions that can share incoming damage.", impact = "Higher values allow more distant companions to participate.")
    double radiusBase = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional companion search radius reached at maximum adaptation level.", impact = "Higher values expand Shared Pain's range as it levels up.")
    double radiusFactor = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum companions included in one damage split, capped internally at 16.", impact = "Higher values spread damage more thinly but perform more nested damage events.")
    int maxPets = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Redirected Damage for the Taming Shared Pain adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerRedirectedDamage = 2.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
