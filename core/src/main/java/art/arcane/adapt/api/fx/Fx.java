package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;

import java.util.EnumMap;
import java.util.Map;

public final class Fx {
  static final Rgb WHITE = new Rgb(255, 255, 255);
  private static final Map<C, Rgb> SKILL_COLORS = buildSkillColors();

  private Fx() {
  }

  public static FxEmitter now(Adaptation<?> adaptation, Loc location, FxPriority priority) {
    if (adaptation == null || location == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    return emitter(adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), adaptationColor(adaptation), location.world(), location.x(), location.y(), location.z(), priority);
  }

  public static FxEmitter now(Adaptation<?> adaptation, EntityRef entity, FxPriority priority) {
    if (adaptation == null || entity == null || entity.world() == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    Loc location = entity.location();
    return emitter(adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), adaptationColor(adaptation), location.world(), location.x(), location.y(), location.z(), priority);
  }

  public static FxEmitter now(Skill<?> skill, Loc location, FxPriority priority) {
    if (skill == null || location == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    return emitter(skill.areParticlesEnabled(), skill.areSoundsEnabled(), skillColor(skill), location.world(), location.x(), location.y(), location.z(), priority);
  }

  public static FxEmitter now(Skill<?> skill, EntityRef entity, FxPriority priority) {
    if (skill == null || entity == null || entity.world() == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    Loc location = entity.location();
    return emitter(skill.areParticlesEnabled(), skill.areSoundsEnabled(), skillColor(skill), location.world(), location.x(), location.y(), location.z(), priority);
  }

  public static void targeted(PlayerRef viewer, ParticleSpec particle, Loc location, int count, double spreadX, double spreadY, double spreadZ, double speed) {
    if (viewer == null || particle == null || location == null) {
      return;
    }
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    if (effects != null && !effects.isParticlesEnabled()) {
      return;
    }
    FxHost host = host();
    if (host == null) {
      return;
    }

    double x = location.x();
    double y = location.y();
    double z = location.z();
    FxDispatch.Emission emission = FxDispatch.emission(player -> {
      if (viewerEffectsEnabled(player)) {
        host.particle(player, particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
      }
    }, null);
    if (!FxViewers.dispatch(viewer, emission) && FxViewers.shouldFallback(viewer)) {
      FxDispatch.dispatch(viewer, emission);
    }
  }

  static FxHost host() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().fx();
  }

  static Rgb adaptationColor(Adaptation<?> adaptation) {
    if (adaptation == null) {
      return WHITE;
    }
    return skillColor(adaptation.getSkill());
  }

  static Rgb skillColor(Skill<?> skill) {
    if (skill == null) {
      return WHITE;
    }
    return colorOf(skill.getColor());
  }

  static Rgb colorOf(C color) {
    if (color == null) {
      return WHITE;
    }
    Rgb resolved = SKILL_COLORS.get(color);
    return resolved == null ? WHITE : resolved;
  }

  private static FxEmitter emitter(boolean particles, boolean sounds, Rgb color, WorldRef world, double x, double y, double z, FxPriority priority) {
    if (!particles && !sounds) {
      return FxEmitter.NO_OP;
    }
    if (FxBudget.densityScalar(priority) <= 0) {
      return FxEmitter.NO_OP;
    }
    return FxEmitter.create(world, x, y, z, priority, FxViewers.DEFAULT_CULL_RADIUS, particles, sounds, color);
  }

  private static boolean viewerEffectsEnabled(PlayerRef viewer) {
    try {
      AdaptCore core = AdaptCore.current();
      AdaptServer server = core == null ? null : core.getAdaptServer();
      if (server == null) {
        return true;
      }
      PlayerData data = server.getPlayerData(viewer.uuid()).orElse(null);
      return data != null && data.isEffectsEnabled();
    } catch (Throwable ignored) {
      return true;
    }
  }

  private static Map<C, Rgb> buildSkillColors() {
    EnumMap<C, Rgb> colors = new EnumMap<>(C.class);
    for (C candidate : C.values()) {
      if (!candidate.isColor()) {
        continue;
      }
      try {
        int rgb = Integer.decode(candidate.hex()) & 0xFFFFFF;
        colors.put(candidate, Rgb.of(rgb));
      } catch (Throwable ignored) {
        colors.put(candidate, WHITE);
      }
    }
    return colors;
  }
}
