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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.VelocityBurst;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

public class HunterSpeed extends SimpleAdaptation<HunterSpeed.Config> {
  public static final Id ID = Id.adapt("hunter/speed");
  private static final Id HUNGER = Id.minecraft("hunger");
  private static final Id POISON = Id.minecraft("poison");
  private final HunterSpeedBurst speedBursts;

  public HunterSpeed() {
    super(ID);
    speedBursts = new HunterSpeedBurst(this, playerState());
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sugar"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sugar"))
        .key("challenge_hunter_speed_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_hunter_speed_200", "hunter.speed.activations", 200, 300);
  }

  @Override
  public void unregister() {
    speedBursts.unregister();
    super.unregister();
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      speedBursts.stop(player.player());
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GRAY + Text.key("adapt.hunter.speed.lore1").resolve());
    statLore(v, level, 2);
    statLore(v, TextFormat.RED, "- ", (5 + level), 3);
    statLore(v, TextFormat.GRAY, "* ", level, 4);
    statLore(v, TextFormat.GRAY, "* ", level, 5);
    statLore(v, TextFormat.GRAY, "- ", level, TextFormat.RED, Text.key("adapt.hunter.penalty.lore1"));

  }


  @Handle
  public void on(EntityDamageEvent e) {
    if (e.entity() instanceof PlayerRef p && isAdaptableDamageCause(e)) {
      int level = getActiveLevel(p);
      if (level <= 0 || HunterStruck.suppressed(this, p)) {
        return;
      }

      if (!getConfig().useConsumable) {
        if (p.foodLevel() == 0) {
          if (getConfig().poisonPenalty) {
            addPotionStacks(p, POISON, getConfig().basePoisonFromLevel - level, getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
          }

        } else if (grantSpeedBurst(p, level, getConfig().baseEffectbyLevel * level, getConfig().stackBuff)) {
          addPotionStacks(p, HUNGER, getConfig().baseHungerFromLevel - level, getConfig().baseHungerDuration * level, getConfig().stackHungerPenalty);
          addStat(p, "hunter.speed.activations", 1);
        }
      } else {
        Id mat = HunterConsumables.resolve(getConfig().consumable);
        if (mat != null) {
          if (p.inventory().contains(mat, 1)) {
            if (payItemCost(p, "consumable", AdaptApi.platform().host().items().create(mat), 1, () -> {
              p.inventory().removeItem(mat, 1);
              return true;
            }) && grantSpeedBurst(p, level, getConfig().baseEffectbyLevel * level, getConfig().stackBuff)) {
              addStat(p, "hunter.speed.activations", 1);
            }
          } else {
            if (getConfig().poisonPenalty) {
              addPotionStacks(p, POISON, getConfig().basePoisonFromLevel - level, getConfig().baseHungerDuration, getConfig().stackPoisonPenalty);
            }
          }
        }
      }
    }
  }

  private boolean grantSpeedBurst(PlayerRef p, int amplifier, int durationTicks, boolean overlap) {
    return speedBursts.grant(p, amplifier, durationTicks, overlap, burstProfile());
  }

  static void startedFx(Adaptation<?> owner, PlayerRef ref) {
    Vec3 back = ref.location().direction().multiply(-1);
    Fx.now(owner, ref.location().add(0, 0.5D, 0), FxPriority.TRANSITION)
        .trail(Particles.CLOUD, back.x(), back.y(), back.z(), 1.4D, 8)
        .chord(Sounds.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.5F, Sounds.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.4F);
  }

  static void trailFx(Adaptation<?> owner, PlayerRef ref, Vec3 horizontal) {
    Fx.now(owner, ref.location().add(0, 0.1D, 0), FxPriority.TRAIL)
        .trail(Particles.CLOUD, -horizontal.x(), 0, -horizontal.z(), 0.8D, 3);
  }

  static void brakedFx(Adaptation<?> owner, PlayerRef ref) {
    Fx.now(owner, ref.location(), FxPriority.TRAIL)
        .burst(Particles.CLOUD, 4, 0.2D)
        .sound(Sounds.BLOCK_SAND_STEP, 0.3F, 0.8F);
  }

  static void endedFx(Adaptation<?> owner, PlayerRef ref) {
    Fx.now(owner, ref.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.2F, 0.6F);
  }

  private VelocityBurst.Profile burstProfile() {
    Config config = getConfig();
    return VelocityBurst.Profile.builder()
        .baseHorizontalSpeed(config.baseHorizontalSpeed)
        .maxHorizontalSpeed(config.maxHorizontalSpeed)
        .accelerationPerTick(config.accelPerTick)
        .brakePerTick(config.brakePerTick)
        .stopThreshold(config.stopThreshold)
        .hardStopOnInvalidState(config.hardStopOnInvalidState)
        .fallbackInputVelocityThreshold(config.fallbackInputVelocityThreshold)
        .build();
  }

  @ConfigDescription("Gain speed when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Use Consumable for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @ConfigDoc(value = "Controls Poison Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @ConfigDoc(value = "Controls Stack Buff for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @ConfigDoc(value = "Controls Base Effectby Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 100;
    @ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @ConfigDoc(value = "Controls Base Poison From Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @ConfigDoc(value = "Base horizontal speed used for hunter bursts before amplifier scaling.", impact = "Higher values increase movement speed while a burst is active.")
    double baseHorizontalSpeed = 0.13;
    @ConfigDoc(value = "Maximum horizontal speed this adaptation can force.", impact = "Acts as a hard cap to prevent runaway momentum.")
    double maxHorizontalSpeed = 0.32;
    @ConfigDoc(value = "How fast velocity accelerates toward the burst target per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
    double accelPerTick = 0.045;
    @ConfigDoc(value = "How fast velocity decays when movement input is released.", impact = "Higher values reduce carry momentum more aggressively.")
    double brakePerTick = 0.08;
    @ConfigDoc(value = "Horizontal velocity threshold considered fully stopped.", impact = "Higher values stop sooner; lower values preserve tiny momentum longer.")
    double stopThreshold = 0.01;
    @ConfigDoc(value = "If true, burst velocity is force-cleared when entering invalid states.", impact = "Prevents retained speed when state changes skip expected flow.")
    boolean hardStopOnInvalidState = true;
    @ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
    double fallbackInputVelocityThreshold = 0.0008;
    @ConfigDoc(value = "Controls Consumable for the Hunter Speed adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
