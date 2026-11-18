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
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TamingWildEmpathy extends SimpleAdaptation<TamingWildEmpathy.Config> {
  private static final Id WOLF = Id.minecraft("wolf");
  private static final Map<Id, Set<Id>> TAME_FOODS = Map.of(
      WOLF, Set.of(Id.minecraft("bone")),
      Id.minecraft("cat"), Set.of(Id.minecraft("cod"), Id.minecraft("salmon")),
      Id.minecraft("ocelot"), Set.of(Id.minecraft("cod"), Id.minecraft("salmon")),
      Id.minecraft("parrot"), Set.of(
          Id.minecraft("wheat_seeds"), Id.minecraft("melon_seeds"), Id.minecraft("pumpkin_seeds"),
          Id.minecraft("beetroot_seeds"), Id.minecraft("torchflower_seeds"), Id.minecraft("pitcher_pod")));
  private static final Set<Id> PACIFIABLE_NEUTRALS = Set.of(WOLF, Id.minecraft("bee"), Id.minecraft("polar_bear"),
      Id.minecraft("llama"), Id.minecraft("trader_llama"), Id.minecraft("panda"), Id.minecraft("goat"));
  private final Cooldowns angerFxCd = cooldowns();

  public TamingWildEmpathy() {
    super("tame-wild-empathy");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.wild_empathy");
    setIcon(Id.minecraft("dandelion"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dandelion"))
        .key("challenge_taming_empathy_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("oxeye_daisy"))
            .key("challenge_taming_empathy_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_empathy_100", "taming.wild-empathy.tames", 100, 400);
    registerMilestone("challenge_taming_empathy_1k", "taming.wild-empathy.tames", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.TAMING, TamingPreferences.PACIFY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getTamingOdds(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.pc(getAngerResistance(level), 0), 2);
  }

  @Handle(priority = EventPriority.LOW)
  public void on(PlayerInteractEntityEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!(e.clicked() instanceof LivingRef target) || !target.has(EntityTrait.TAMEABLE) || animals().isTamed(target)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !preferenceEnabled(p, TamingPreferences.TAMING)) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isTameFood(target.type(), hand.type())) {
      return;
    }

    if (Math.random() > getTamingOdds(level)) {
      return;
    }

    e.setCancelled(true);
    UUID ownerId = p.uuid();
    Id food = hand.type();
    Tasks.runEntity(target, () -> forceTame(p, ownerId, target, food));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef p) || !isPacifiableNeutral(e.entity(), animals())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !preferenceEnabled(p, TamingPreferences.PACIFY)) {
      return;
    }

    if (Math.random() > getAngerResistance(level)) {
      return;
    }

    e.setCancelled(true);
    if (e.entity() instanceof MobRef mob && mob.target() != null && mob.target().uuid().equals(p.uuid())) {
      mob.setTarget(null);
    }
    if (WOLF.equals(e.entity().type())) {
      animals().setAngry(e.entity(), false);
    }

    addStat(p, "taming.wild-empathy.angers-resisted", 1);
    if (angerFxCd.isReady(p.uuid(), 1500L)) {
      angerFxCd.mark(p.uuid());
      fx(e.entity().location().add(0, 0.6, 0), FxPriority.AMBIENT)
          .ring(Particles.VILLAGER_HAPPY, 0.5D, 6, 0.2D)
          .sound(Sounds.ENTITY_VILLAGER_YES, 0.35F, 1.4F);
    }
  }

  private void forceTame(PlayerRef p, UUID ownerId, LivingRef target, Id food) {
    if (!target.isValid() || target.isDead() || animals().isTamed(target)) {
      return;
    }

    if (!animals().tame(target, p)) {
      return;
    }

    if (WOLF.equals(target.type())) {
      animals().setAngry(target, false);
    }

    fx(target, FxPriority.TRANSITION)
        .ring(Particles.VILLAGER_HAPPY, 0.6D, 10, 0.4D)
        .burst(Particles.HEART, 4, 0.2D)
        .chord(Sounds.ENTITY_WOLF_PANT, 0.7F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_BELL, 0.5F, 1.6F);

    Tasks.runEntity(p, () -> completeForcedTame(p, food, ownerId));
  }

  private void completeForcedTame(PlayerRef p, Id food, UUID ownerId) {
    if (!p.isOnline() || !p.uuid().equals(ownerId)) {
      return;
    }

    consumeOne(p, food);
    addStat(p, "taming.wild-empathy.tames", 1);
    xp(p, getConfig().xpPerTame);
  }

  private void consumeOne(PlayerRef p, Id food) {
    ItemRef current = p.inventory().mainHand();
    if (current == null || !food.equals(current.type()) || current.amount() <= 0) {
      return;
    }

    int amount = current.amount() - 1;
    if (amount <= 0) {
      p.inventory().setMainHand(AdaptCore.platform().host().items().empty());
    } else {
      current.setAmount(amount);
      p.inventory().setMainHand(current);
    }
  }

  static boolean isPacifiableNeutral(EntityRef entity, AnimalOps animals) {
    if (entity.has(EntityTrait.TAMEABLE) && animals.isTamed(entity)) {
      return false;
    }
    return PACIFIABLE_NEUTRALS.contains(entity.type());
  }

  private boolean isTameFood(Id type, Id material) {
    Set<Id> foods = TAME_FOODS.get(type);
    return foods != null && foods.contains(material);
  }

  private static AnimalOps animals() {
    return AdaptCore.platform().host().animals();
  }

  private double getTamingOdds(int level) {
    return Math.min(getConfig().maxTamingOdds, getConfig().tamingOddsBase + (getLevelPercent(level) * getConfig().tamingOddsFactor));
  }

  private double getAngerResistance(int level) {
    return Math.min(getConfig().maxAngerResistance, getConfig().angerResistanceBase + (getLevelPercent(level) * getConfig().angerResistanceFactor));
  }


  @ConfigDescription("Taming succeeds more often, and neutral mobs are slower to anger at you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Taming Odds Base for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tamingOddsBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Taming Odds Factor for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double tamingOddsFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Taming Odds for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxTamingOdds = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anger Resistance Base for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double angerResistanceBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Anger Resistance Factor for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double angerResistanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Anger Resistance for the Taming Wild Empathy adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAngerResistance = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time Wild Empathy forces a successful tame.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTame = 60;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
