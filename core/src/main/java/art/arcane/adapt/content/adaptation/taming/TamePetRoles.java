package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The role a named pet holds, stored on the pet: guard its owner, guard a spot, or fetch. */
public final class TamePetRoles {
  private static final DataKey<Role> ROLE = DataKey.adapt("taming_pet_role", DataCodecs.enumByName(Role.class));
  private static final DataKey<String> SPOT = DataKey.adapt("taming_pet_spot", DataCodecs.STRING);
  private static final String SEPARATOR = ";";

  private TamePetRoles() {
  }

  public enum Role {
    NONE,
    GUARD_OWNER,
    GUARD_SPOT,
    FETCH;

    /** The role after this one in the menu's cycle. */
    public Role next() {
      Role[] all = values();
      return all[(ordinal() + 1) % all.length];
    }
  }

  /** Whether the pet can hold a role: only pets their owner named. */
  public static boolean named(EntityRef pet) {
    String name = pet.customName();
    return name != null && !name.isBlank();
  }

  public static Role role(EntityRef pet) {
    Role role = named(pet) ? pet.data().get(ROLE) : null;
    return role == null ? Role.NONE : role;
  }

  /** The spot a spot guard keeps, or null when it has none or its world is gone. */
  public static @Nullable Loc spot(EntityRef pet) {
    String raw = pet.data().get(SPOT);
    if (raw == null) {
      return null;
    }
    String[] parts = raw.split(SEPARATOR);
    if (parts.length != 4) {
      return null;
    }
    try {
      WorldRef world = AdaptCore.platform().host().world(UUID.fromString(parts[0]));
      return world == null ? null
          : new Loc(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  /** Gives the pet {@code role}; {@code spot} is kept only for a spot guard. */
  public static void assign(EntityRef pet, Role role, @Nullable Loc spot) {
    if (role == Role.NONE) {
      pet.data().remove(ROLE);
    } else {
      pet.data().set(ROLE, role);
    }
    if (role == Role.GUARD_SPOT && spot != null) {
      pet.data().set(SPOT, spot.world().uid() + SEPARATOR + spot.blockX() + SEPARATOR + spot.blockY() + SEPARATOR + spot.blockZ());
    } else {
      pet.data().remove(SPOT);
    }
  }
}
