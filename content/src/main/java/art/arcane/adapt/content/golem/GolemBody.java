package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Optional;

public enum GolemBody {
  FROST("frost", Id.minecraft("snow_golem")),
  COPPER("copper", Id.minecraft("copper_golem")),
  IRON("iron", Id.minecraft("iron_golem")),
  BONE("bone", Id.minecraft("skeleton"));

  private static final GolemBody[] VALUES = values();

  private final String id;
  private final Id entityType;

  GolemBody(String id, Id entityType) {
    this.id = id;
    this.entityType = entityType;
  }

  public static Optional<GolemBody> fromId(String id) {
    for (GolemBody body : VALUES) {
      if (body.id.equals(id)) {
        return Optional.of(body);
      }
    }
    return Optional.empty();
  }

  public static Optional<GolemBody> of(EntityRef entity) {
    if (entity == null) {
      return Optional.empty();
    }
    Id type = entity.type();
    for (GolemBody body : VALUES) {
      if (body.entityType.equals(type)) {
        return Optional.of(body);
      }
    }
    return Optional.empty();
  }

  public String id() {
    return id;
  }

  public Id entityType() {
    return entityType;
  }

  public boolean fights() {
    return this != COPPER;
  }

  public Text displayName() {
    return switch (this) {
      case FROST -> Text.key("adapt.golemancy.body.frost");
      case COPPER -> Text.key("adapt.golemancy.body.copper");
      case IRON -> Text.key("adapt.golemancy.body.iron");
      case BONE -> Text.key("adapt.golemancy.body.bone");
    };
  }
}
