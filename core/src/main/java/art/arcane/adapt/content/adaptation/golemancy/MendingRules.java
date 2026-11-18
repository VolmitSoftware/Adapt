package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemMend;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Optional;

final class MendingRules {
  static final double VANILLA_INGOT_HEAL = 25D;
  static final double REBUILD_REACH = 3D;
  static final int MAX_AMPLIFIER = 255;

  private MendingRules() {
  }

  static double healBonus(List<Double> bonusByLevel, int level) {
    double bonus = AnimusBindingRules.atLevel(bonusByLevel, level, 0D);
    return Double.isFinite(bonus) ? Math.max(0D, bonus) : 0D;
  }

  static double healAmount(double base, List<Double> bonusByLevel, int level) {
    if (level < 1 || !Double.isFinite(base) || base <= 0D) {
      return 0D;
    }
    return base * (1D + healBonus(bonusByLevel, level));
  }

  static double ingotBonus(List<Double> bonusByLevel, int level) {
    return level < 1 ? 0D : healBonus(bonusByLevel, level) * VANILLA_INGOT_HEAL;
  }

  static double regen(GolemMending.Config config, int level, GolemMend.RegenSenses senses) {
    double owner = GolemancyRules.unlocked(level, config.ownerRegenLevel) && within(senses.ownerDistance(), config.ownerRegenRadius)
        ? config.ownerRegenAmount
        : 0D;
    double home = GolemancyRules.unlocked(level, config.homeRegenLevel) && senses.idle() && within(senses.homeDistance(), config.homeRegenRadius)
        ? config.homeRegenAmount
        : 0D;
    return Math.max(0D, Math.max(owner, home));
  }

  static boolean shatters(GolemBody body, boolean validated, boolean ownerOnline, int level, int shatterLevel) {
    return body != GolemBody.BONE && validated && ownerOnline && GolemancyRules.unlocked(level, shatterLevel);
  }

  static boolean withinReach(Vec3 eye, Box body) {
    double x = Math.max(body.minX(), Math.min(eye.x(), body.maxX()));
    double y = Math.max(body.minY(), Math.min(eye.y(), body.maxY()));
    double z = Math.max(body.minZ(), Math.min(eye.z(), body.maxZ()));
    return eye.distanceSquared(new Vec3(x, y, z)) <= REBUILD_REACH * REBUILD_REACH;
  }

  static RebuildVerdict rebuildVerdict(RebuildCheck check) {
    if (!check.readable()) {
      return RebuildVerdict.UNREADABLE;
    }
    if (!check.ownsItem()) {
      return RebuildVerdict.NOT_OWNER;
    }
    if (!check.rosterKnown()) {
      return RebuildVerdict.UNAVAILABLE;
    }
    if (check.status() != GolemRoster.Status.SHATTERED || !check.sealed()) {
      return RebuildVerdict.VOID;
    }
    if (!GolemancyRules.unlocked(check.level(), check.shatterLevel())) {
      return RebuildVerdict.LOCKED;
    }
    GolemRegistry.BindBlock block = check.registryBlock();
    if (block == GolemRegistry.BindBlock.ALREADY_BOUND) {
      return RebuildVerdict.ALREADY_BOUND;
    }
    if (block == GolemRegistry.BindBlock.BODY_MISMATCH) {
      return RebuildVerdict.BODY_MISMATCH;
    }
    if (!check.playerCreated() && !check.allowVillageGolems()) {
      return RebuildVerdict.VILLAGE_GOLEM;
    }
    if (check.builderExclusive()) {
      return RebuildVerdict.NOT_BUILDER;
    }
    if (!check.bodyUnlocked()) {
      return RebuildVerdict.BODY_LOCKED;
    }
    if (block == GolemRegistry.BindBlock.CORE_NOT_ALLOWED) {
      return RebuildVerdict.CORE_NOT_ALLOWED;
    }
    if (!check.regionAllows()) {
      return RebuildVerdict.PROTECTED;
    }
    if (block == GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE) {
      return RebuildVerdict.UNAVAILABLE;
    }
    if (block == GolemRegistry.BindBlock.CHUNK_CAP) {
      return RebuildVerdict.CHUNK_CAP;
    }
    return RebuildVerdict.READY;
  }

  private static boolean within(double distance, double radius) {
    return !Double.isNaN(distance) && distance <= radius;
  }

  enum Remedy {
    COPPER_INGOT(Id.minecraft("copper_ingot"), GolemBody.COPPER),
    SNOW_BLOCK(Id.minecraft("snow_block"), GolemBody.FROST),
    BONE_MEAL(Id.minecraft("bone_meal"), null);

    private final Id material;
    private final GolemBody body;

    Remedy(Id material, GolemBody body) {
      this.material = material;
      this.body = body;
    }

    static Optional<Remedy> of(Id material) {
      for (Remedy remedy : values()) {
        if (remedy.material.equals(material)) {
          return Optional.of(remedy);
        }
      }
      return Optional.empty();
    }

    GolemBody body() {
      return body;
    }

    double base(GolemMending.Config config) {
      return switch (this) {
        case COPPER_INGOT -> config.copperIngotHeal;
        case SNOW_BLOCK -> config.snowBlockHeal;
        case BONE_MEAL -> config.boneMealHeal;
      };
    }
  }
}
