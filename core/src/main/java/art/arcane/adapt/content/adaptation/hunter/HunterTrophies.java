package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.UUID;

/** Precision-kill checks and the trophy and head drops shared by the hunter trophy adaptations. */
public final class HunterTrophies {
  private HunterTrophies() {
  }

  public record Precision(boolean projectileKill, boolean precise) {
  }

  public record DropOutcome(boolean trophy, boolean head) {
  }

  public static DropOutcome decideDrops(double trophyRoll, double headRoll, double trophyChance, double headChance) {
    return new DropOutcome(trophyRoll <= trophyChance, headRoll <= headChance);
  }

  /** A kill is precise when the killer was sneaking, or shot the victim from at least {@code minimumRange}. */
  public static Precision precision(EntityDeathEvent e, PlayerRef killer, double minimumRange) {
    boolean projectileKill = false;
    double range = 0;
    if (e.directDamager() instanceof ProjectileRef projectile) {
      UUID shooter = projectile.shooterId();
      if (shooter != null && shooter.equals(killer.uuid())) {
        projectileKill = true;
      }
    }

    Loc killerLocation = killer.location();
    Loc victimLocation = e.entity().location();
    if (killerLocation.sameWorld(victimLocation)) {
      range = killerLocation.distance(victimLocation);
    }

    boolean rangedPrecision = projectileKill && range >= minimumRange;
    boolean stealthPrecision = killer.isSneaking();
    return new Precision(projectileKill, rangedPrecision || stealthPrecision);
  }

  /** The victim's trophy material, at most 8 of it. */
  public static ItemRef trophyDrop(Id type, int amount) {
    String material = switch (type.isMinecraft() ? type.path() : "") {
      case "creeper" -> "gunpowder";
      case "skeleton", "bogged", "wither_skeleton", "stray" -> "bone";
      case "zombie", "zombified_piglin", "husk", "drowned" -> "rotten_flesh";
      case "spider", "cave_spider" -> "string";
      case "blaze" -> "blaze_powder";
      case "enderman" -> "ender_pearl";
      case "witch" -> "redstone";
      case "piglin", "piglin_brute", "hoglin", "zoglin" -> "porkchop";
      default -> "leather";
    };
    return AdaptCore.platform().host().items().create(Id.minecraft(material), Math.min(8, amount));
  }

  /** The victim's head, or null when it has none. */
  public static ItemRef headDrop(Id type) {
    String material = switch (type.isMinecraft() ? type.path() : "") {
      case "creeper" -> "creeper_head";
      case "skeleton", "stray", "bogged" -> "skeleton_skull";
      case "wither_skeleton" -> "wither_skeleton_skull";
      case "zombie", "husk", "drowned", "zombified_piglin" -> "zombie_head";
      case "piglin", "piglin_brute" -> "piglin_head";
      default -> null;
    };
    return material == null ? null : AdaptCore.platform().host().items().create(Id.minecraft(material));
  }
}
