package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class NodeRenderer {
  private static final float HITBOX_SIZE = 0.6F;
  private static final double LABEL_FACE_DISTANCE = 0.499D;
  private static final float WALL_LABEL_LIFT = 0.55F;
  private static final float TOP_LABEL_LIFT = 0.15F;
  private static final float BOTTOM_LABEL_LIFT = -0.35F;
  private static final int LABEL_BACKGROUND_ALPHA = 64;

  private final Function<NodeType, ItemStack> icons;
  private final Map<NodeKey, NodeBundle> bundles = new ConcurrentHashMap<>();
  private final Map<UUID, NodeKey> entities = new ConcurrentHashMap<>();

  public NodeRenderer(Function<NodeType, ItemStack> icons) {
    this.icons = Objects.requireNonNull(icons);
  }

  public NodeBundle spawn(World world, NodeKey key, NodeRecord record, NodeStatus status, float labelViewRange) {
    despawn(key);
    BlockFace face = key.face();
    Block anchor = world.getBlockAt(key.x(), key.y(), key.z());
    Material iconMaterial = NodeIcons.icon(record.type());
    ItemDisplay icon = HiddenDisplays.spawnFaceItem(anchor, face, icons.apply(record.type()), NodeIcons.ICON_SCALE, NodeIcons.faceOffset(iconMaterial));
    applyGlow(icon, record.channel());
    Interaction hitbox = HiddenDisplays.spawnFaceHitbox(anchor, face, HITBOX_SIZE, HITBOX_SIZE);
    TextDisplay label = HiddenDisplays.spawnLabel(labelLocation(world, key), label(record, status),
        Color.fromARGB(LABEL_BACKGROUND_ALPHA, 0, 0, 0), labelViewRange);
    Transformation base = label.getTransformation();
    label.setTransformation(new Transformation(new Vector3f(0F, labelLift(face), 0F), base.getLeftRotation(), base.getScale(), base.getRightRotation()));
    NodeBundle bundle = new NodeBundle(icon, hitbox, label);
    bundles.put(key, bundle);
    for (Entity entity : bundle.entities()) {
      entities.put(entity.getUniqueId(), key);
    }
    return bundle;
  }

  public Optional<NodeBundle> bundle(NodeKey key) {
    return Optional.ofNullable(bundles.get(key));
  }

  public Optional<NodeKey> nodeOf(Entity entity) {
    return Optional.ofNullable(entities.get(entity.getUniqueId()));
  }

  public void despawn(NodeKey key) {
    NodeBundle bundle = bundles.remove(key);
    if (bundle == null) {
      return;
    }
    for (Entity entity : bundle.entities()) {
      entities.remove(entity.getUniqueId());
      remove(entity);
    }
  }

  public void shutdown() {
    for (NodeKey key : List.copyOf(bundles.keySet())) {
      despawn(key);
    }
  }

  private static Location labelLocation(World world, NodeKey key) {
    BlockFace face = key.face();
    return key.center(world).add(face.getModX() * LABEL_FACE_DISTANCE, face.getModY() * LABEL_FACE_DISTANCE, face.getModZ() * LABEL_FACE_DISTANCE);
  }

  private static float labelLift(BlockFace face) {
    return switch (face) {
      case UP -> TOP_LABEL_LIFT;
      case DOWN -> BOTTOM_LABEL_LIFT;
      default -> WALL_LABEL_LIFT;
    };
  }

  private static Component label(NodeRecord record, NodeStatus status) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(record.owner())) {
      return NodeLabel.compose(new NodeLabel.Content(
          AdaptLanguage.text(record.type().displayName()),
          record.channel(),
          AdaptLanguage.text(GolemancyMessages.NODE_LABEL_PRIORITY, trusted("priority", record.priority())),
          AdaptLanguage.text(GolemancyMessages.NODE_LABEL_FILTERS, trusted("count", record.filterCount())),
          status,
          AdaptLanguage.text(status.displayName())));
    }
  }

  private static void applyGlow(ItemDisplay icon, DyeColor channel) {
    if (channel == null) {
      icon.setGlowing(false);
      return;
    }
    icon.setGlowColorOverride(channel.getColor());
    icon.setGlowing(true);
  }

  private static void remove(Entity entity) {
    if (!entity.isValid()) {
      return;
    }
    if (J.isOwnedByCurrentRegion(entity)) {
      entity.remove();
      return;
    }
    if (Adapt.instance.isEnabled()) {
      J.runEntity(entity, () -> {
        if (entity.isValid()) {
          entity.remove();
        }
      });
    }
  }
}
