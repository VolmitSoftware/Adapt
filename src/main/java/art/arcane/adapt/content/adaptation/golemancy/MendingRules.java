package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemMend;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

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

  static boolean shatters(boolean validated, boolean ownerOnline, int level, int shatterLevel) {
    return validated && ownerOnline && GolemancyRules.unlocked(level, shatterLevel);
  }

  static boolean withinReach(Vector eye, BoundingBox body) {
    double x = Math.max(body.getMinX(), Math.min(eye.getX(), body.getMaxX()));
    double y = Math.max(body.getMinY(), Math.min(eye.getY(), body.getMaxY()));
    double z = Math.max(body.getMinZ(), Math.min(eye.getZ(), body.getMaxZ()));
    return eye.distanceSquared(new Vector(x, y, z)) <= REBUILD_REACH * REBUILD_REACH;
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
    if (!check.canInteract()) {
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
    COPPER_INGOT(Material.COPPER_INGOT, GolemBody.COPPER),
    SNOW_BLOCK(Material.SNOW_BLOCK, GolemBody.FROST),
    BONE_MEAL(Material.BONE_MEAL, null);

    private final Material material;
    private final GolemBody body;

    Remedy(Material material, GolemBody body) {
      this.material = material;
      this.body = body;
    }

    static Optional<Remedy> of(Material material) {
      for (Remedy remedy : values()) {
        if (remedy.material == material) {
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
