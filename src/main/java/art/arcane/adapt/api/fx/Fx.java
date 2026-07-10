package art.arcane.adapt.api.fx;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.format.C;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

public final class Fx {
  private static final Map<C, Color> SKILL_COLORS = buildSkillColors();

  private Fx() {
  }

  public static FxEmitter now(Adaptation<?> adaptation, Location location, FxPriority priority) {
    if (adaptation == null || location == null || location.getWorld() == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    return emitter(adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), adaptationColor(adaptation), location.getWorld(), location.getX(), location.getY(), location.getZ(), priority);
  }

  public static FxEmitter now(Adaptation<?> adaptation, Entity entity, FxPriority priority) {
    if (adaptation == null || entity == null || entity.getWorld() == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    Location location = entity.getLocation();
    return emitter(adaptation.areParticlesEnabled(), adaptation.areSoundsEnabled(), adaptationColor(adaptation), entity.getWorld(), location.getX(), location.getY(), location.getZ(), priority);
  }

  public static FxEmitter now(Skill<?> skill, Location location, FxPriority priority) {
    if (skill == null || location == null || location.getWorld() == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    return emitter(skill.areParticlesEnabled(), skill.areSoundsEnabled(), skillColor(skill), location.getWorld(), location.getX(), location.getY(), location.getZ(), priority);
  }

  public static FxEmitter now(Skill<?> skill, Entity entity, FxPriority priority) {
    if (skill == null || entity == null || entity.getWorld() == null || priority == null) {
      return FxEmitter.NO_OP;
    }
    Location location = entity.getLocation();
    return emitter(skill.areParticlesEnabled(), skill.areSoundsEnabled(), skillColor(skill), entity.getWorld(), location.getX(), location.getY(), location.getZ(), priority);
  }

  public static void targeted(Player viewer, Particle particle, Location location, int count, double spreadX, double spreadY, double spreadZ, double speed) {
    if (viewer == null || particle == null || location == null || location.getWorld() == null) {
      return;
    }
    AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
    if (effects != null && !effects.isParticlesEnabled()) {
      return;
    }

    double x = location.getX();
    double y = location.getY();
    double z = location.getZ();
    FxDispatch.Emission emission = FxDispatch.emission(player -> {
      if (viewerEffectsEnabled(player)) {
        player.spawnParticle(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
      }
    }, null);
    if (!FxViewers.dispatch(viewer, emission) && FxViewers.shouldFallback(viewer)) {
      FxDispatch.dispatch(viewer, emission);
    }
  }

  static Color adaptationColor(Adaptation<?> adaptation) {
    if (adaptation == null) {
      return Color.WHITE;
    }
    return skillColor(adaptation.getSkill());
  }

  static Color skillColor(Skill<?> skill) {
    if (skill == null) {
      return Color.WHITE;
    }
    return colorOf(skill.getColor());
  }

  static Color colorOf(C color) {
    if (color == null) {
      return Color.WHITE;
    }
    Color resolved = SKILL_COLORS.get(color);
    return resolved == null ? Color.WHITE : resolved;
  }

  private static FxEmitter emitter(boolean particles, boolean sounds, Color color, World world, double x, double y, double z, FxPriority priority) {
    if (!particles && !sounds) {
      return FxEmitter.NO_OP;
    }
    if (FxBudget.densityScalar(priority) <= 0) {
      return FxEmitter.NO_OP;
    }
    return FxEmitter.create(world, x, y, z, priority, FxViewers.DEFAULT_CULL_RADIUS, particles, sounds, color);
  }

  private static boolean viewerEffectsEnabled(Player viewer) {
    try {
      AdaptServer server = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
      if (server == null) {
        return true;
      }
      PlayerData data = server.getPlayerData(viewer.getUniqueId()).orElse(null);
      return data != null && data.isEffectsEnabled();
    } catch (Throwable ignored) {
      return true;
    }
  }

  private static Map<C, Color> buildSkillColors() {
    EnumMap<C, Color> colors = new EnumMap<>(C.class);
    for (C candidate : C.values()) {
      if (!candidate.isColor()) {
        continue;
      }
      try {
        int rgb = Integer.decode(candidate.hex()) & 0xFFFFFF;
        colors.put(candidate, Color.fromRGB(rgb));
      } catch (Throwable ignored) {
        colors.put(candidate, Color.WHITE);
      }
    }
    return colors;
  }
}
