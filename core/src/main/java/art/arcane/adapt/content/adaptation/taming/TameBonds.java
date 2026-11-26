package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * A player's one bonded mount. The bond lives on both sides: the mount names its owner and keeps the distance it was
 * ridden by them, and the player names the mount, so a traded mount keeps its growth but only answers its owner.
 */
public final class TameBonds {
  public static final DataKey<UUID> MOUNT_OWNER = DataKey.adapt("taming_bond_owner", DataCodecs.UUID_STRING);
  public static final DataKey<Double> MOUNT_RIDDEN = DataKey.adapt("taming_bond_ridden", DataCodecs.DOUBLE);
  public static final DataKey<UUID> PLAYER_MOUNT = DataKey.adapt("taming_bonded_mount", DataCodecs.UUID_STRING);
  private static final Id STRIDER = Id.minecraft("strider");
  private static final Id CAMEL = Id.minecraft("camel");
  private static final Set<Id> PACK_ANIMALS = Set.of(Id.minecraft("llama"), Id.minecraft("trader_llama"));

  private TameBonds() {
  }

  /** An upgrade that bonds mounts and lends Mounted Tactics to the bonded one. */
  public interface Bonds {
    /** The Mounted Tactics level {@code rider} gets on {@code vehicle}: its top level on their bonded mount, else 0. */
    int tacticsLevel(PlayerRef rider, @Nullable EntityRef vehicle, Adaptation<?> tactics);

    /** Bonds {@code mount} to the player, ending any earlier bond; false when it cannot be bonded. */
    boolean bond(PlayerRef player, LivingRef mount);

    /** Calls the bonded mount to the player when a path exists; false when it cannot come. */
    boolean call(PlayerRef player);
  }

  /** A horse, donkey, mule, camel or strider the player may bond: tamed by them, or a camel or strider nobody owns. */
  public static boolean bondable(PlayerRef player, EntityRef mount) {
    if (!(mount instanceof LivingRef living) || !living.isValid() || living.isDead() || PACK_ANIMALS.contains(mount.type())) {
      return false;
    }
    boolean horse = mount.has(EntityTrait.HORSE);
    if (!horse && !STRIDER.equals(mount.type())) {
      return false;
    }
    UUID owner = mount.tameOwner();
    if (owner != null) {
      return owner.equals(player.uuid());
    }
    return STRIDER.equals(mount.type()) || CAMEL.equals(mount.type());
  }

  /** Whether {@code mount} is the player's bonded mount, from both sides, and nobody else has tamed it since. */
  public static boolean bondedTo(PlayerRef player, @Nullable EntityRef mount) {
    if (mount == null) {
      return false;
    }
    UUID named = player.data().get(PLAYER_MOUNT);
    UUID tamer = mount.tameOwner();
    return mount.uuid().equals(named) && player.uuid().equals(mount.data().get(MOUNT_OWNER))
        && (tamer == null || tamer.equals(player.uuid()));
  }

  /** Distance the mount has been ridden by its bonded owner, in blocks. */
  public static double ridden(EntityRef mount) {
    Double ridden = mount.data().get(MOUNT_RIDDEN);
    return ridden == null || !Double.isFinite(ridden) ? 0D : Math.max(0D, ridden);
  }

  /** The Mounted Tactics level any bonding upgrade of {@code tactics}' skill lends {@code rider} on {@code vehicle}. */
  public static int tacticsLevel(Adaptation<?> tactics, PlayerRef rider, @Nullable EntityRef vehicle) {
    Bonds bonds = vehicle == null ? null : find(tactics);
    return bonds == null ? 0 : Math.max(0, bonds.tacticsLevel(rider, vehicle, tactics));
  }

  /** The bonding upgrade of {@code any}'s skill, or null. */
  public static @Nullable Bonds find(Adaptation<?> any) {
    if (any.getSkill() == null) {
      return null;
    }
    for (Adaptation<?> upgrade : any.getSkill().getUpgrades()) {
      if (upgrade instanceof Bonds bonds) {
        return bonds;
      }
    }
    return null;
  }
}
