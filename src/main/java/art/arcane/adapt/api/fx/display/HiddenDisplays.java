package art.arcane.adapt.api.fx.display;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.volmlib.util.entity.StackExclusion;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public final class HiddenDisplays {
  public static final float SURFACE_OFFSET = 0.01F;
  private static final float FACE_DISTANCE = 0.5F;
  private static final float ANCHOR_INSET = 0.001F;
  private static final float QUARTER_TURN = (float) (Math.PI / 2D);
  private static final float HALF_TURN = (float) Math.PI;

  private HiddenDisplays() {
  }

  public static ItemDisplay spawnFaceItem(Block block, BlockFace face, ItemStack item, float scale, Vector3f faceOffset) {
    Transformation transformation = faceTransformation(face, scale, faceOffset);
    ItemStack icon = Objects.requireNonNull(item).clone();
    Location anchor = blockCenter(block);
    return PaperCompat.spawnHydrated(anchor.getWorld(), anchor, ItemDisplay.class, display -> {
      hideByDefault(display);
      display.setItemStack(icon);
      display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
      display.setBrightness(new Display.Brightness(15, 15));
      display.setTransformation(transformation);
    });
  }

  public static TextDisplay spawnLabel(Location location, Component text, Color background, float viewRange) {
    Location anchor = Objects.requireNonNull(location).clone();
    return PaperCompat.spawnHydrated(anchor.getWorld(), anchor, TextDisplay.class, display -> {
      hideByDefault(display);
      display.setBillboard(Display.Billboard.CENTER);
      PaperCompat.text(display, text);
      display.setBackgroundColor(background);
      display.setViewRange(viewRange);
    });
  }

  public static Interaction spawnFaceHitbox(Block block, BlockFace face, float width, float height) {
    Vector3f offset = hitboxOffset(face, height);
    Location anchor = blockCenter(block).add(offset.x, offset.y, offset.z);
    return PaperCompat.spawnHydrated(anchor.getWorld(), anchor, Interaction.class, interaction -> {
      hideByDefault(interaction);
      interaction.setInteractionWidth(width);
      interaction.setInteractionHeight(height);
    });
  }

  public static void show(Player viewer, Entity entity) {
    viewer.showEntity(Adapt.instance, entity);
  }

  public static void hide(Player viewer, Entity entity) {
    viewer.hideEntity(Adapt.instance, entity);
  }

  public static Quaternionf faceRotation(BlockFace face) {
    return switch (face) {
      case SOUTH -> new Quaternionf();
      case NORTH -> new Quaternionf().rotationY(HALF_TURN);
      case EAST -> new Quaternionf().rotationY(QUARTER_TURN);
      case WEST -> new Quaternionf().rotationY(-QUARTER_TURN);
      case UP -> new Quaternionf().rotationX(-QUARTER_TURN);
      case DOWN -> new Quaternionf().rotationX(QUARTER_TURN);
      default -> throw new IllegalArgumentException("Display faces must be cartesian, got " + face);
    };
  }

  public static Transformation faceTransformation(BlockFace face, float scale, Vector3f faceOffset) {
    Quaternionf rotation = faceRotation(face);
    Vector3f translation = rotation.transform(new Vector3f(faceOffset).add(0F, 0F, FACE_DISTANCE + SURFACE_OFFSET));
    return new Transformation(translation, rotation, new Vector3f(scale, scale, scale), new Quaternionf());
  }

  public static Vector3f hitboxOffset(BlockFace face, float height) {
    Vector3f normal = faceRotation(face).transform(new Vector3f(0F, 0F, 1F));
    return normal.mul(FACE_DISTANCE - ANCHOR_INSET).sub(0F, height * 0.5F, 0F);
  }

  private static Location blockCenter(Block block) {
    return new Location(block.getWorld(), block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D);
  }

  private static void hideByDefault(Entity entity) {
    entity.setVisibleByDefault(false);
    entity.setPersistent(false);
    StackExclusion.exclude(entity);
  }
}
