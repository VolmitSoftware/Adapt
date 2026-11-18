package art.arcane.adapt.api.fx.display;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.Billboard;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.entity.display.ItemDisplayMode;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.data.StackExclusion;

import java.util.Objects;

public final class HiddenDisplays {
  public static final float SURFACE_OFFSET = 0.01F;
  private static final float FACE_DISTANCE = 0.5F;
  private static final float ANCHOR_INSET = 0.001F;
  private static final float QUARTER_TURN = (float) (Math.PI / 2D);
  private static final float HALF_TURN = (float) Math.PI;

  private HiddenDisplays() {
  }

  public static ItemDisplayRef spawnFaceItem(BlockRef block, BlockFace face, ItemRef item, float scale, Vec3 faceOffset) {
    Transform transformation = faceTransformation(face, scale, faceOffset);
    ItemRef icon = Objects.requireNonNull(item).copy();
    Loc anchor = block.center();
    return anchor.world().spawnItemDisplay(anchor, icon, (ItemDisplayRef display) -> {
      display.setVisibleByDefault(false);
      display.setPersistent(false);
      StackExclusion.exclude(display);
      display.setItem(icon);
      display.setDisplayMode(ItemDisplayMode.FIXED);
      display.setBrightness(new Brightness(15, 15));
      display.setTransform(transformation);
    });
  }

  public static TextDisplayRef spawnLabel(Loc location, String markup, int backgroundArgb, float viewRange) {
    Loc anchor = Objects.requireNonNull(location);
    return anchor.world().spawnTextDisplay(anchor, markup, (TextDisplayRef display) -> {
      display.setVisibleByDefault(false);
      display.setPersistent(false);
      StackExclusion.exclude(display);
      display.setBillboard(Billboard.CENTER);
      display.setText(markup);
      display.setBackgroundArgb(backgroundArgb);
      display.setViewRange(viewRange);
    });
  }

  public static EntityRef spawnFaceHitbox(BlockRef block, BlockFace face, float width, float height) {
    Vec3 offset = hitboxOffset(face, height);
    Loc anchor = block.center().add(offset);
    return anchor.world().spawnInteraction(anchor, width, height, false, (EntityRef interaction) -> {
      interaction.setPersistent(false);
      StackExclusion.exclude(interaction);
    });
  }

  public static void show(PlayerRef viewer, EntityRef entity) {
    AdaptCore.platform().viewers().showEntity(viewer, entity);
  }

  public static void hide(PlayerRef viewer, EntityRef entity) {
    AdaptCore.platform().viewers().hideEntity(viewer, entity);
  }

  public static Quat faceRotation(BlockFace face) {
    return switch (face) {
      case SOUTH -> Quat.IDENTITY;
      case NORTH -> Quat.axisAngle(0F, 1F, 0F, HALF_TURN);
      case EAST -> Quat.axisAngle(0F, 1F, 0F, QUARTER_TURN);
      case WEST -> Quat.axisAngle(0F, 1F, 0F, -QUARTER_TURN);
      case UP -> Quat.axisAngle(1F, 0F, 0F, -QUARTER_TURN);
      case DOWN -> Quat.axisAngle(1F, 0F, 0F, QUARTER_TURN);
      default -> throw new IllegalArgumentException("Display faces must be cartesian, got " + face);
    };
  }

  public static Transform faceTransformation(BlockFace face, float scale, Vec3 faceOffset) {
    Quat rotation = faceRotation(face);
    Vec3 translation = rotate(rotation, faceOffset.add(0D, 0D, FACE_DISTANCE + SURFACE_OFFSET));
    return new Transform(translation, rotation, new Vec3(scale, scale, scale), Quat.IDENTITY);
  }

  public static Vec3 hitboxOffset(BlockFace face, float height) {
    Vec3 normal = rotate(faceRotation(face), new Vec3(0D, 0D, 1D));
    return normal.multiply(FACE_DISTANCE - ANCHOR_INSET).subtract(new Vec3(0D, height * 0.5F, 0D));
  }

  private static Vec3 rotate(Quat q, Vec3 v) {
    Vec3 axis = new Vec3(q.x(), q.y(), q.z());
    Vec3 t = axis.cross(v).multiply(2.0D);
    return v.add(t.multiply(q.w())).add(axis.cross(t));
  }
}
