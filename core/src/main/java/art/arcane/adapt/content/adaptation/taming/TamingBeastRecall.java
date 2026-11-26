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
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
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
import art.arcane.adapt.localization.catalog.TamingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class TamingBeastRecall extends SimpleAdaptation<TamingBeastRecall.Config> {
  private static final Id LEAD = Id.minecraft("lead");
  private final TameRecall recall = new TameRecall(this, playerState(), (PlayerRef p) -> {
    xp(p, getConfig().xpOnRecall);
    addStat(p, "taming.beast-recall.recalls", 1);
  });

  public TamingBeastRecall() {
    super("tame-beast-recall");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.beast_recall");
    setIcon(Id.minecraft("lead"));
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lead"))
        .key("challenge_taming_recall_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_pearl"))
            .key("challenge_taming_recall_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_recall_100", "taming.beast-recall.recalls", 100, 300);
    registerMilestone("challenge_taming_recall_1k", "taming.beast-recall.recalls", 1000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.SITTING, TamingPreferences.HUNGER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getSearchRadius(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 2);
    if (getConfig().hungerCost > 0) {
      statLore(v, C.RED, "* ", getConfig().hungerCost, TamingMessages.BEAST_RECALL_LORE_COST_HUNGER);
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (!TameRecall.isRecallGesture(e)) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || !LEAD.equals(p.inventory().mainHand().type()) || p.hasCooldown(LEAD)) {
      return;
    }

    TameRecall.Tuning tuning = new TameRecall.Tuning(LEAD, getSearchRadius(level), getConfig().minRecallDistanceSquared,
        getCooldownTicks(level), getConfig().hungerCost,
        TameRecall.candidateLimit(getConfig().maxCandidatesPerActivation),
        TameRecall.affectedLimit(getConfig().maxAffectedPerActivation));
    if (recall.recall(p, level, tuning)) {
      e.setCancelled(true);
    }
  }

  private double getSearchRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  static void recallFx(Adaptation<?> owner, Loc from, Loc safe) {
    Fx.now(owner, from, FxPriority.TRANSITION)
        .burst(Particles.REVERSE_PORTAL, 12, 0.3D)
        .particle(Particles.POOF, 1, 0, 0.2D, 0, 0.1D, 0.02D)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.45F, Sounds.ITEM_TRIDENT_RETURN, 0.4F, 1.6F);
    Fx.now(owner, safe, FxPriority.TRANSITION)
        .dome(Particles.PORTAL, 0.6D, 8)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.5D, 0, 0.4D, 0)
        .chord(Sounds.ITEM_LEAD_BREAK, 0.6F, 1.2F, Sounds.ENTITY_WOLF_PANT, 0.6F, 1.3F);
  }

  @ConfigDescription("Sneak-right-click with a lead to recall your nearest tamed companion.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 38;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Recall Distance Squared for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minRecallDistanceSquared = 9.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 420;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 280;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Recall for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnRecall = 26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Food points consumed per beast recall.", impact = "Higher values make each recall cost more hunger; 0 disables the cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum nearby tameable candidates inspected by one recall.", impact = "Lower values reduce dense-pet scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pets recalled by one activation.", impact = "This is hard-clamped to 1; set to 0 to disable recall effects without disabling the adaptation.")
    int maxAffectedPerActivation = 1;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
