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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.attribute.LegacyModifierKey;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityTameEvent;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.UUID;

public class TamingStableHand extends SimpleAdaptation<TamingStableHand.Config> {
  public static final Id ID = Id.adapt("tame/stable_hand");
  private static final LegacyModifierKey SPEED_KEY = LegacyModifierKey.of(Id.of("adapt", "tame-stable-speed"));
  private static final LegacyModifierKey HEALTH_KEY = LegacyModifierKey.of(Id.of("adapt", "tame-stable-health"));
  private static final LegacyModifierKey JUMP_KEY = LegacyModifierKey.of(Id.of("adapt", "tame-stable-jump"));
  private static final LegacyModifierKey FALL_KEY = LegacyModifierKey.of(Id.of("adapt", "tame-stable-fall"));
  private static final double FALL_BLOCKS_PER_BIAS = 10.0;
  private static final List<ConfirmationDing> CONFIRMATION_DINGS = List.of(
      new ConfirmationDing(0, 1.2F),
      new ConfirmationDing(3, 1.5F),
      new ConfirmationDing(6, 1.8F));
  static double bias(double levelPercent, double base, double factor, double max) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static double fallBonusBlocks(double bias) {
    return bias * FALL_BLOCKS_PER_BIAS;
  }

  public static boolean hasAppliedBias(LivingRef animal) {
    if (animal == null) {
      return false;
    }

    return hasAppliedBias(
        modifier(animal, Attributes.MOVEMENT_SPEED, SPEED_KEY),
        modifier(animal, Attributes.MAX_HEALTH, HEALTH_KEY),
        modifier(animal, Attributes.JUMP_STRENGTH, JUMP_KEY));
  }

  static boolean hasAppliedBias(AttributeMod speed, AttributeMod health, AttributeMod jump) {
    return speed != null || health != null || jump != null;
  }

  private static AttributeMod modifier(LivingRef animal, Id attribute, LegacyModifierKey key) {
    if (!animal.hasAttribute(attribute)) {
      return null;
    }

    List<AttributeMod> found = key.find(animal, attribute);
    return found.isEmpty() ? null : found.get(0);
  }

  static List<ConfirmationDing> confirmationDings() {
    return CONFIRMATION_DINGS;
  }

  public TamingStableHand() {
    super(ID);
    needs(PlatformNeed.partialAttribute(Attributes.SAFE_FALL_DISTANCE, "mounts get no extra safe fall distance"));
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.taming.stable_hand");
    setIcon(Id.minecraft("saddle"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("saddle"))
        .key("challenge_taming_stable_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_horse_armor"))
            .key("challenge_taming_stable_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_stable_100", "taming.stable-hand.animals-shaped", 100, 400);
    registerMilestone("challenge_taming_stable_1k", "taming.stable-hand.animals-shaped", 1000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getBias(level), 0), 1);
    statLore(v, Format.f(fallBonusBlocks(getBias(level)), 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityTameEvent e) {
    PlayerRef p = e.owner();
    if (p == null) {
      return;
    }
    LivingRef animal = e.entity();

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double bias = getBias(level);
    UUID ownerId = p.uuid();
    AdaptApi.scheduler().runEntity(animal, () -> applyBias(animal, bias, ownerId));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityBreedEvent e) {
    if (!(e.breeder() instanceof PlayerRef p)) {
      return;
    }
    LivingRef child = e.child();

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double bias = getBias(level);
    UUID ownerId = p.uuid();
    AdaptApi.scheduler().runEntity(child, () -> applyBias(child, bias, ownerId), 1);
  }

  private void applyBias(LivingRef animal, double bias, UUID ownerId) {
    if (!animal.isValid() || animal.isDead() || bias <= 0) {
      return;
    }

    boolean applied = false;
    applied |= applyModifier(animal, Attributes.MOVEMENT_SPEED, SPEED_KEY, bias, AttributeOperation.ADD_MULTIPLIED_BASE);
    applied |= applyModifier(animal, Attributes.JUMP_STRENGTH, JUMP_KEY, bias, AttributeOperation.ADD_MULTIPLIED_BASE);
    applied |= applyModifier(animal, Attributes.SAFE_FALL_DISTANCE, FALL_KEY, fallBonusBlocks(bias), AttributeOperation.ADD_VALUE);

    if (animal.hasAttribute(Attributes.MAX_HEALTH) && !HEALTH_KEY.present(animal, Attributes.MAX_HEALTH)) {
      HEALTH_KEY.set(animal, Attributes.MAX_HEALTH, bias, AttributeOperation.ADD_MULTIPLIED_BASE);
      double newMax = animal.attributeValue(Attributes.MAX_HEALTH);
      if (newMax > 0 && !Double.isNaN(newMax)) {
        animal.setHealth(Math.min(newMax, Math.max(animal.health(), newMax)));
      }
      applied = true;
    }

    if (!applied) {
      return;
    }

    shapedFx(this, animal);

    PlayerRef owner = AdaptApi.platform().host().player(ownerId);
    if (owner != null) {
      AdaptApi.scheduler().runEntity(owner, () -> creditShaped(owner));
    }
  }

  /** The rings, hearts and breath of an animal taking a stable-hand bias. */
  public static void shapedFx(Adaptation<?> owner, LivingRef animal) {
    Fx.now(owner, animal, FxPriority.TRANSITION)
        .ring(Particles.VILLAGER_HAPPY, 0.6D, 8, 0.3D)
        .column(Particles.HEART, 3, 1.0D)
        .sound(Sounds.ENTITY_HORSE_BREATHE, 0.4F, 1.2F);
  }

  private boolean applyModifier(LivingRef animal, Id attribute, LegacyModifierKey key, double amount, AttributeOperation operation) {
    if (!animal.hasAttribute(attribute) || key.present(animal, attribute)) {
      return false;
    }

    key.set(animal, attribute, amount, operation);
    return true;
  }

  private void creditShaped(PlayerRef owner) {
    if (!owner.isOnline()) {
      return;
    }
    addStat(owner, "taming.stable-hand.animals-shaped", 1);
    xp(owner, getConfig().xpPerAnimal);
    playConfirmation(owner);
  }

  private void playConfirmation(PlayerRef owner) {
    if (!areSoundsEnabled()) {
      return;
    }

    for (ConfirmationDing ding : CONFIRMATION_DINGS) {
      Runnable playback = () -> {
        if (owner.isOnline()) {
          owner.playSound(owner.location(), Sounds.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.65F, ding.pitch());
        }
      };
      if (ding.delayTicks() == 0) {
        playback.run();
      } else {
        AdaptApi.scheduler().runEntity(owner, playback, ding.delayTicks());
      }
    }
  }

  private double getBias(int level) {
    return bias(getLevelPercent(level), getConfig().biasBase, getConfig().biasFactor, getConfig().maxBias);
  }


  @ConfigDescription("Animals you tame or breed keep a permanent bias toward better speed, jump, health, and safe fall distance.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Bias Base for the Taming Stable Hand adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double biasBase = 0.1;
    @ConfigDoc(value = "Controls Bias Factor for the Taming Stable Hand adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double biasFactor = 0.2;
    @ConfigDoc(value = "Controls Max Bias for the Taming Stable Hand adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBias = 0.3;
    @ConfigDoc(value = "Xp granted each time a tamed or bred animal receives the stable-hand bias.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerAnimal = 20;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }

  record ConfirmationDing(int delayTicks, float pitch) {
  }
}
