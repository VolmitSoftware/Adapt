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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.adaptation.VelocityBurstRuntime;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HunterMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HunterSpeed extends SimpleAdaptation<HunterSpeed.Config> {
  private static final Id HUNGER = Id.minecraft("hunger");
  private static final Id POISON = Id.minecraft("poison");
  private final VelocityBurstRuntime.Client speedBursts;
  private final Map<UUID, Long> burstUntil = playerState();

  public HunterSpeed() {
    super("hunter-speed");
    speedBursts = VelocityBurstRuntime.register(this, new BurstFeedback());
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sugar"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sugar"))
        .key("challenge_hunter_speed_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
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
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (getActiveLevel(player.getPlayer()) <= 0) {
      speedBursts.stop(player.getPlayer());
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(HunterMessages.SPEED_LORE1));
    statLore(v, level, 2);
    statLore(v, C.RED, "- ", (5 + level), 3);
    statLore(v, C.GRAY, "* ", level, 4);
    statLore(v, C.GRAY, "* ", level, 5);
    statLore(v, C.GRAY, "- ", level, C.RED, HunterMessages.PENALTY_LORE1);

  }


  @Handle
  public void on(EntityDamageEvent e) {
    if (e.entity() instanceof PlayerRef p && isAdaptableDamageCause(e)) {
      if (!preference(p, HunterPreferences.RESERVE).permits(p.foodLevel())) {
      return;
    }
    int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }
      if (AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied() && p.hasEffect(HUNGER)) {
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
            if (payItemCost(p, "consumable", AdaptCore.platform().host().items().create(mat), 1, () -> {
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
    VelocityBurstRuntime.BurstRequest request = new VelocityBurstRuntime.BurstRequest(
        amplifier,
        durationTicks,
        overlap,
        burstProfile()
    );
    VelocityBurstRuntime.StartResult result = speedBursts.start(p, request);
    UUID id = p.uuid();
    if (result.accepted() && AbilityGlyphs.capable(id)) {
      long now = System.currentTimeMillis();
      Long until = burstUntil.get(id);
      long base = result == VelocityBurstRuntime.StartResult.EXTENDED && until != null && until > now ? until : now;
      long endsAt = base + (durationTicks * 50L);
      burstUntil.put(id, endsAt);
      AbilityGlyphs.duration(id, this, now, endsAt - now);
    }
    return result.accepted();
  }

  private void brakeScuff(PlayerRef p) {
    fx(p.location(), FxPriority.TRAIL)
        .burst(Particles.CLOUD, 4, 0.2D)
        .sound(Sounds.BLOCK_SAND_STEP, 0.3F, 0.8F);
  }

  private VelocityBurstRuntime.Profile burstProfile() {
    Config config = getConfig();
    return new VelocityBurstRuntime.Profile(
        config.baseHorizontalSpeed,
        config.maxHorizontalSpeed,
        config.accelPerTick,
        config.brakePerTick,
        config.stopThreshold,
        config.hardStopOnInvalidState,
        config.fallbackInputVelocityThreshold
    );
  }

  private final class BurstFeedback implements VelocityBurstRuntime.Feedback {
    @Override
    public void onStarted(PlayerRef ref) {
      Vec3 back = ref.location().direction().multiply(-1);
      fx(ref.location().add(0, 0.5D, 0), FxPriority.TRANSITION)
          .trail(Particles.CLOUD, back.x(), back.y(), back.z(), 1.4D, 8)
          .chord(Sounds.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.5F, Sounds.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.4F);
    }

    @Override
    public void onTrail(PlayerRef ref, Vec3 horizontal, int updateIndex) {
      if ((updateIndex % 3) == 0) {
        fx(ref.location().add(0, 0.1D, 0), FxPriority.TRAIL)
            .trail(Particles.CLOUD, -horizontal.x(), 0, -horizontal.z(), 0.8D, 3);
      }
    }

    @Override
    public void onBraked(PlayerRef ref) {
      brakeScuff(ref);
    }

    @Override
    public void onEnded(PlayerRef ref) {
      burstUntil.remove(ref.uuid());
      AbilityGlyphs.clear(ref.uuid(), HunterSpeed.this, HudGlyph.DURATION);
      fx(ref.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.2D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.2F, 0.6F);
    }
  }

  @ConfigDescription("Gain speed when struck, at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Use Consumable for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean useConsumable = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Poison Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean poisonPenalty = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Hunger Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackHungerPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Poison Penalty for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackPoisonPenalty = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Buff for the Hunter Speed adaptation.", impact = "True enables this behavior and false disables it.")
    boolean stackBuff = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Effectby Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseEffectbyLevel = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger Duration for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerDuration = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Hunger From Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int baseHungerFromLevel = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Poison From Level for the Hunter Speed adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int basePoisonFromLevel = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed used for hunter bursts before amplifier scaling.", impact = "Higher values increase movement speed while a burst is active.")
    double baseHorizontalSpeed = 0.13;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force.", impact = "Acts as a hard cap to prevent runaway momentum.")
    double maxHorizontalSpeed = 0.32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast velocity accelerates toward the burst target per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
    double accelPerTick = 0.045;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast velocity decays when movement input is released.", impact = "Higher values reduce carry momentum more aggressively.")
    double brakePerTick = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal velocity threshold considered fully stopped.", impact = "Higher values stop sooner; lower values preserve tiny momentum longer.")
    double stopThreshold = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "If true, burst velocity is force-cleared when entering invalid states.", impact = "Prevents retained speed when state changes skip expected flow.")
    boolean hardStopOnInvalidState = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
    double fallbackInputVelocityThreshold = 0.0008;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consumable for the Hunter Speed adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
    String consumable = "ROTTEN_FLESH";

    public Config() {
      costFactor = 0.4;
      initialCost = 8;
    }
  }
}
