package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.HiddenDisplays;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public final class NodeRenderer {
  private static final float HITBOX_SIZE = 0.6F;
  private static final double LABEL_FACE_DISTANCE = 0.499D;
  private static final float WALL_LABEL_LIFT = 0.55F;
  private static final float TOP_LABEL_LIFT = 0.15F;
  private static final float BOTTOM_LABEL_LIFT = -0.35F;
  private static final int LABEL_BACKGROUND_ARGB = 64 << 24;

  private final Function<NodeType, ItemRef> icons;
  private final Map<NodeKey, NodeBundle> bundles = new ConcurrentHashMap<>();
  private final Map<UUID, NodeKey> entities = new ConcurrentHashMap<>();
  private final Map<NodeKey, NodeStatus> statuses = new ConcurrentHashMap<>();

  public NodeRenderer(Function<NodeType, ItemRef> icons) {
    this.icons = Objects.requireNonNull(icons);
  }

  public NodeBundle spawn(WorldRef world, NodeKey key, NodeRecord record, NodeStatus status, float labelViewRange) {
    despawn(key);
    BlockFace face = key.face();
    BlockRef anchor = world.blockAt(key.x(), key.y(), key.z());
    Id iconMaterial = NodeIcons.icon(record.type());
    ItemDisplayRef icon = HiddenDisplays.spawnFaceItem(anchor, face, icons.apply(record.type()), NodeIcons.ICON_SCALE, NodeIcons.faceOffset(iconMaterial));
    applyGlow(icon, record.channel());
    EntityRef hitbox = HiddenDisplays.spawnFaceHitbox(anchor, face, HITBOX_SIZE, HITBOX_SIZE);
    TextDisplayRef label = HiddenDisplays.spawnLabel(labelLocation(world, key), label(record, status), LABEL_BACKGROUND_ARGB, labelViewRange);
    label.setTransform(label.transform().withTranslation(new Vec3(0D, labelLift(face), 0D)));
    NodeBundle bundle = new NodeBundle(icon, hitbox, label);
    bundles.put(key, bundle);
    statuses.put(key, status);
    for (EntityRef entity : bundle.entities()) {
      entities.put(entity.uuid(), key);
    }
    return bundle;
  }

  public void refresh(NodeKey key, NodeRecord record) {
    NodeBundle bundle = bundles.get(key);
    if (bundle == null) {
      return;
    }
    applyGlow(bundle.icon(), record.channel());
    bundle.label().setText(label(record, statuses.getOrDefault(key, NodeStatus.IDLE)));
  }

  public void status(NodeKey key, NodeRecord record, NodeStatus status) {
    NodeBundle bundle = bundles.get(key);
    if (bundle == null || statuses.get(key) == status) {
      return;
    }
    statuses.put(key, status);
    bundle.label().setText(label(record, status));
  }

  public Optional<NodeBundle> bundle(NodeKey key) {
    return Optional.ofNullable(bundles.get(key));
  }

  public Optional<NodeKey> nodeOf(EntityRef entity) {
    return Optional.ofNullable(entities.get(entity.uuid()));
  }

  public void despawn(NodeKey key) {
    NodeBundle bundle = bundles.remove(key);
    statuses.remove(key);
    if (bundle == null) {
      return;
    }
    for (EntityRef entity : bundle.entities()) {
      entities.remove(entity.uuid());
      remove(entity);
    }
  }

  public void shutdown() {
    for (NodeKey key : List.copyOf(bundles.keySet())) {
      despawn(key);
    }
  }

  private static Loc labelLocation(WorldRef world, NodeKey key) {
    BlockFace face = key.face();
    return key.center(world).add(face.dx() * LABEL_FACE_DISTANCE, face.dy() * LABEL_FACE_DISTANCE, face.dz() * LABEL_FACE_DISTANCE);
  }

  private static float labelLift(BlockFace face) {
    return switch (face) {
      case UP -> TOP_LABEL_LIFT;
      case DOWN -> BOTTOM_LABEL_LIFT;
      default -> WALL_LABEL_LIFT;
    };
  }

  private static String label(NodeRecord record, NodeStatus status) {
    return Text.renderFor(record.owner(), () -> {
      return NodeLabel.compose(new NodeLabel.Content(
          record.type().displayName().resolve(),
          record.channel(),
          Text.key("adapt.golemancy.node_label.priority").withMarkup("priority", String.valueOf(record.priority())).resolve(),
          Text.key("adapt.golemancy.node_label.filters").withMarkup("count", String.valueOf(record.filterCount())).resolve(),
          status,
          status.displayName().resolve()));
    });
  }

  private static void applyGlow(ItemDisplayRef icon, DyeColor channel) {
    if (channel == null) {
      icon.setGlowing(false);
      return;
    }
    icon.setGlowColorOverride(channel.color());
    icon.setGlowing(true);
  }

  private static void remove(EntityRef entity) {
    if (!entity.isValid()) {
      return;
    }
    if (AdaptApi.scheduler().owns(entity)) {
      entity.remove();
      return;
    }
    if (AdaptApi.isRunning()) {
      AdaptApi.scheduler().runEntity(entity, () -> {
        if (entity.isValid()) {
          entity.remove();
        }
      });
    }
  }
}
