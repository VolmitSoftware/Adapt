package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
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

  public static FxEmitter now(ContentComponent owner, Loc location, FxPriority priority) {
    if (owner == null || location == null || priority == null) {
      return WorldEmitter.NO_OP;
    }
    return emitter(owner.areParticlesEnabled(), owner.areSoundsEnabled(), color(owner), location.world(), location.x(), location.y(), location.z(), priority);
  }

  public static FxEmitter now(ContentComponent owner, EntityRef entity, FxPriority priority) {
    if (owner == null || entity == null || entity.world() == null || priority == null) {
      return WorldEmitter.NO_OP;
    }
    Loc location = entity.location();
    return emitter(owner.areParticlesEnabled(), owner.areSoundsEnabled(), color(owner), location.world(), location.x(), location.y(), location.z(), priority);
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
        host.particle(player, particle, x, y, z, count, spreadX, spreadY, spreadZ, speed, false);
      }
    }, null);
    if (!FxViewers.dispatch(viewer, emission) && FxViewers.shouldFallback(viewer)) {
      FxDispatch.dispatch(viewer, emission);
    }
  }

  /**
   * Plays {@code sound} at {@code location} for {@code viewer} alone, wherever the viewer snapshot last placed them; for a
   * player who has just teleported, whom an emitter at their new location would still miss.
   */
  public static void sound(Adaptation<?> adaptation, PlayerRef viewer, Loc location, Id sound, float volume, float pitch) {
    if (adaptation == null || viewer == null || location == null || sound == null || !adaptation.areSoundsEnabled()) {
      return;
    }
    if (FxBudget.densityScalar(FxPriority.TRANSITION) <= 0) {
      return;
    }
    FxDispatch.Emission emission = FxDispatch.emission(player -> {
      if (viewerEffectsEnabled(player)) {
        player.playSound(location, sound, SoundCategory.MASTER, volume, pitch);
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

  /** The colour a component's effects are tinted with: its skill's, an adaptation's skill's. */
  static Rgb color(ContentComponent owner) {
    if (owner instanceof Skill<?> skill) {
      return skillColor(skill);
    }
    if (owner instanceof Adaptation<?> adaptation) {
      Skill<?> skill = adaptation.getSkillId() == null ? null : skillOf(adaptation);
      return skillColor(skill);
    }
    return WHITE;
  }

  private static Skill<?> skillOf(Adaptation<?> adaptation) {
    try {
      return adaptation.getSkill();
    } catch (IllegalStateException unplaced) {
      return null;
    }
  }

  static Rgb skillColor(Skill<?> skill) {
    if (skill == null) {
      return WHITE;
    }
    return colorOf(SkillHost.legacyColor(skill.getColor()));
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
      return WorldEmitter.NO_OP;
    }
    if (FxBudget.densityScalar(priority) <= 0) {
      return WorldEmitter.NO_OP;
    }
    return WorldEmitter.create(world, x, y, z, priority, FxViewers.DEFAULT_CULL_RADIUS, particles, sounds, color);
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
