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
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class TamingLastBreath extends SimpleAdaptation<TamingLastBreath.Config> {
  private final TameLastStand lastStand = new TameLastStand(this, cooldowns(), (PlayerRef owner) -> {
    addStat(owner, "taming.last-breath.saves", 1);
    xp(owner, getConfig().xpPerSave);
  });

  public static long cooldownMillis(double levelPercent, double base, double factor, double min) {
    return (long) Math.max(min, base - (levelPercent * factor));
  }

  public TamingLastBreath() {
    super("tame-last-breath");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.last_breath");
    setIcon(Id.minecraft("totem_of_undying"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("totem_of_undying"))
        .key("challenge_taming_lastbreath_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_taming_lastbreath_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_lastbreath_50", "taming.last-breath.saves", 50, 400);
    registerMilestone("challenge_taming_lastbreath_500", "taming.last-breath.saves", 500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 1);
    statLore(v, C.AQUA, "* ", Form.duration(getInvulnTicks() * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void onProtectedWindow(EntityDamageEvent e) {
    lastStand.guard(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    lastStand.save(e, this::getCooldownMillis, getInvulnTicks());
  }

  private long getCooldownMillis(int level) {
    return cooldownMillis(getLevelPercent(level), getConfig().cooldownMillisBase, getConfig().cooldownMillisFactor, getConfig().minCooldownMillis);
  }

  private int getInvulnTicks() {
    return Math.max(10, getConfig().invulnTicks);
  }


  static void saveFx(Adaptation<?> owner, LivingRef pet) {
    Fx.now(owner, pet.location().add(0, 1, 0), FxPriority.COMBAT)
        .burst(Particles.TOTEM, 24, 0.4D)
        .column(Particles.VILLAGER_HAPPY, 6, 1.4D)
        .chord(Sounds.ITEM_TOTEM_USE, 0.7F, 1.2F, Sounds.ENTITY_WOLF_WHINE, 0.6F, 1.4F);
  }

  static void arrivalFx(Adaptation<?> owner, Loc destination) {
    Fx.now(owner, destination, FxPriority.TRANSITION)
        .dome(Particles.PORTAL, 0.6D, 8)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.5D, 0, 0.4D, 0)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F, Sounds.ENTITY_WOLF_PANT, 0.6F, 1.3F);
  }

  @ConfigDescription("On a lethal hit a pet drops to 1 HP, is briefly invulnerable, and recalls to your side.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base per-pet cooldown in milliseconds before Last Breath can save the same pet again.", impact = "Higher values make the safety net rarer; leveling lowers this value.")
    double cooldownMillisBase = 300000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown milliseconds removed at max level.", impact = "Higher values reduce the per-pet cooldown faster as you level.")
    double cooldownMillisFactor = 180000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum per-pet cooldown in milliseconds after level reduction.", impact = "Higher values keep a longer floor on the save cooldown.")
    double minCooldownMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks of invulnerability granted to a pet saved by Last Breath.", impact = "Higher values give the pet a longer protected window after being saved.")
    int invulnTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time Last Breath saves a pet.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerSave = 40;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
