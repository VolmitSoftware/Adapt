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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.UUID;

public class AgilityRollLanding extends SimpleAdaptation<AgilityRollLanding.Config> {
  public static final Id ID = Id.adapt("agility/roll_landing");
  public static final PlayerPreference<FoodReserve> FOOD_RESERVE = PlayerPreference.of("food-reserve", FoodReserve.class, FoodReserve.NONE, List.of(
          new PlayerPreference.Choice<>(FoodReserve.NONE, Text.key("adapt.agilityrolllanding.preferences.preference_food_reserve_none"), Id.minecraft("bowl"), 1),
          new PlayerPreference.Choice<>(FoodReserve.FOUR, Text.key("adapt.agilityrolllanding.preferences.preference_food_reserve_four"), Id.minecraft("bread"), 1),
          new PlayerPreference.Choice<>(FoodReserve.EIGHT, Text.key("adapt.agilityrolllanding.preferences.preference_food_reserve_eight"), Id.minecraft("cooked_beef"), 1))).labelled(Text.key("adapt.agilityrolllanding.preferences.preference_food_reserve"));

  private static final Id HAY_BLOCK = Id.minecraft("hay_block");

  private final Cooldowns rollInputs = cooldowns();
  private final AgilityRoll roll = new AgilityRoll(this, playerState());

  public AgilityRollLanding() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(HAY_BLOCK);
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("hay_block"))
        .key("challenge_agility_roll_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("slime_block"))
            .key("challenge_agility_roll_1000")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("elytra"))
        .key("challenge_agility_fearless")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_agility_roll_100", "agility.roll-landing.damage-prevented", 100, 300);
    registerMilestone("challenge_agility_roll_1000", "agility.roll-landing.damage-prevented", 1000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FOOD_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getFallReduction(level), 0), 1);
    statLore(v, Format.duration(getInputWindowMillis(level), 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> recordRollInput(p, true, null, null));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || p.isOnGround()) {
      return;
    }

    withAdaptedPlayer(p, e, () -> recordRollInput(p, true, e.from().y(), e.to() == null ? null : e.to().y()));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.cause() != DamageCause.FALL) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.hasCooldown(HAY_BLOCK)) {
        return;
      }

      int level = getActiveLevel(p);
      if (rollInputs.remaining(p.uuid(), getInputWindowMillis(level)) <= 0) {
        return;
      }

      RollAbsorption absorption = computeRollAbsorption(e.damage(), getFallReduction(level), getHungerPerDamage(level), Math.max(0, p.foodLevel() - preference(p, FOOD_RESERVE).ordinal() * 4));
      double absorbed = absorption.absorbed();
      if (absorbed <= 0) {
        return;
      }

      p.setFoodLevel(Math.max(0, p.foodLevel() - absorption.hungerCost()));
      e.setDamage(Math.max(0, e.damage() - absorbed));
      if (e.damage() <= 0.01) {
        e.setCancelled(true);
      }

      int cooldownTicks = getCooldownTicks(level);
      p.setCooldown(HAY_BLOCK, cooldownTicks);
      HudStatus.cooldown(p, this, cooldownTicks * 50L);
      roll.roll(p, getProneTicks(level), absorbed);
      addStat(p, "agility.roll-landing.damage-prevented", absorbed);
      if (p.fallDistance() >= 30) {
        fx(p.location(), FxPriority.COMBAT)
            .ring(Particles.CRIT, 1.0D, 10, 0.1D)
            .sound(Sounds.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.5F);
        if (AdaptApi.settings().advancements() && !getPlayer(p).isGranted(getId(), "challenge_agility_fearless")) {
          getPlayer(p).grant(getId(), "challenge_agility_fearless");
        }
      }
      xp(p, absorbed * getConfig().xpPerDamagePrevented);
    });
  }

  private void recordRollInput(PlayerRef p, boolean sneaking, Double fromY, Double toY) {
    boolean descending = isDescending(
        p.velocity().y(),
        getConfig().maxVerticalVelocityForRollInput,
        fromY,
        toY,
        p.fallDistance()
    );
    if (!shouldRecordRollInput(sneaking, p.isOnGround(), p.isFlying(), p.isGliding(), descending)) {
      return;
    }

    UUID id = p.uuid();
    if (rollInputs.remaining(id, 800L) <= 0) {
      fx(p.location(), FxPriority.TRANSITION).sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.15F, 1.8F);
    }
    rollInputs.mark(id);
  }

  private double getFallReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }

  private long getInputWindowMillis(int level) {
    return inputWindowMillis(getConfig().inputWindowMillisBase, getConfig().inputWindowMillisFactor, getLevelPercent(level));
  }

  static long inputWindowMillis(double base, double factor, double levelPercent) {
    return Math.max(60L, Math.round(base + (levelPercent * factor)));
  }

  static boolean shouldRecordRollInput(boolean sneaking, boolean onGround, boolean flying, boolean gliding, boolean descending) {
    return sneaking && !onGround && !flying && !gliding && descending;
  }

  static boolean isDescending(double velocityY, double descentThreshold, Double fromY, Double toY, double fallDistance) {
    if (velocityY <= descentThreshold) {
      return true;
    }

    if (fallDistance > 0) {
      return true;
    }

    return fromY != null && toY != null && toY < fromY;
  }

  private double getHungerPerDamage(int level) {
    return Math.max(0.1, getConfig().hungerPerDamageBase - (getLevelPercent(level) * getConfig().hungerPerDamageReduction));
  }

  private int getCooldownTicks(int level) {
    return Math.max(4, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getProneTicks(int level) {
    return Math.max(2, (int) Math.round(getConfig().proneTicksBase + (getLevelPercent(level) * getConfig().proneTicksFactor)));
  }

  static RollAbsorption computeRollAbsorption(double damage, double reduction, double hungerPerDamage, int foodLevel) {
    double absorbCap = damage * reduction;
    int hungerNeeded = (int) Math.ceil(absorbCap * hungerPerDamage);
    if (hungerNeeded <= 0 || foodLevel <= 0) {
      return new RollAbsorption(0, 0D);
    }

    int usableFood = Math.min(foodLevel, hungerNeeded);
    double absorbed = Math.min(absorbCap, usableFood / hungerPerDamage);
    return new RollAbsorption(usableFood, absorbed);
  }

  record RollAbsorption(int hungerCost, double absorbed) {
  }


  @ConfigDescription("Timed sneak before landing converts part of fall damage into hunger cost.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Reduction Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.22;
    @ConfigDoc(value = "Controls Reduction Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.43;
    @ConfigDoc(value = "Controls Max Reduction for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReduction = 0.8;
    @ConfigDoc(value = "How long a crouch input stays armed before landing, in milliseconds, before level scaling.", impact = "Higher values make the roll timing more forgiving at every level.")
    double inputWindowMillisBase = 450;
    @ConfigDoc(value = "Extra crouch-input window granted at max level, in milliseconds.", impact = "Higher values make leveling widen the roll timing more.")
    double inputWindowMillisFactor = 350;
    @ConfigDoc(value = "Controls Hunger Per Damage Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerPerDamageBase = 1.4;
    @ConfigDoc(value = "Controls Hunger Per Damage Reduction for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerPerDamageReduction = 0.75;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 22;
    @ConfigDoc(value = "Controls Cooldown Ticks Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 12;
    @ConfigDoc(value = "Vertical velocity at or below which a crouch counts as a descent input.", impact = "Only a fallback signal; fall distance and downward movement also arm the roll.")
    double maxVerticalVelocityForRollInput = -0.08;
    @ConfigDoc(value = "Controls Prone Ticks Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double proneTicksBase = 4;
    @ConfigDoc(value = "Controls Prone Ticks Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double proneTicksFactor = 5;
    @ConfigDoc(value = "Controls Xp Per Damage Prevented for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamagePrevented = 4.2;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }

  public enum FoodReserve { NONE, FOUR, EIGHT }


  public static void rollFx(Adaptation<?> owner, Loc location, int dust) {
    Fx.now(owner, location, FxPriority.COMBAT)
        .particle(Particles.blockCrack(location.world().blockAt(location).relative(BlockFace.DOWN).state()), dust, 0, 0.1D, 0, 0.3D, 0.05D)
        .chord(Sounds.ENTITY_PLAYER_SMALL_FALL, 0.8F, 0.7F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.89F, Sounds.BLOCK_WOOL_BREAK, 0.55F, 0.9F);
  }

  static void rollTrailFx(Adaptation<?> owner, PlayerRef p, int proneTicks) {
    FxTimeline.follow(owner, p)
        .duration(Math.max(2, proneTicks))
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particles.CLOUD, 1, 0, 0.15D, 0, 0.05D, 0))
        .start();
  }
}
