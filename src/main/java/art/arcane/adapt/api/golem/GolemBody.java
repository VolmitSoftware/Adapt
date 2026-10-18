package art.arcane.adapt.api.golem;

import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;

import java.util.Optional;

public enum GolemBody {
  FROST("frost", Snowman.class),
  COPPER("copper", CopperGolem.class),
  IRON("iron", IronGolem.class),
  BONE("bone", Skeleton.class);

  private final String id;
  private final Class<? extends Mob> entityClass;

  GolemBody(String id, Class<? extends Mob> entityClass) {
    this.id = id;
    this.entityClass = entityClass;
  }

  public static Optional<GolemBody> fromId(String id) {
    for (GolemBody body : values()) {
      if (body.id.equals(id)) {
        return Optional.of(body);
      }
    }
    return Optional.empty();
  }

  public static Optional<GolemBody> of(Entity entity) {
    for (GolemBody body : values()) {
      if (body.entityClass.isInstance(entity)) {
        return Optional.of(body);
      }
    }
    return Optional.empty();
  }

  public String id() {
    return id;
  }

  public Class<? extends Mob> entityClass() {
    return entityClass;
  }

  public TextKey displayName() {
    return switch (this) {
      case FROST -> GolemancyMessages.BODY_FROST;
      case COPPER -> GolemancyMessages.BODY_COPPER;
      case IRON -> GolemancyMessages.BODY_IRON;
      case BONE -> GolemancyMessages.BODY_BONE;
    };
  }
}
