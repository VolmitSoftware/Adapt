package art.arcane.adapt.content.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.data.DataHolder;
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
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiddenDisplaysTest extends AdaptTestBase {
  private static final double EPSILON = 1.0E-5D;
  private static final Vec3 ITEM_NORMAL = new Vec3(0D, 0D, 1D);
  private static final Vec3 UP = new Vec3(0D, 1D, 0D);
  private static final Map<BlockFace, Vec3> VIEWER_RIGHT = Map.of(
      BlockFace.SOUTH, new Vec3(1D, 0D, 0D),
      BlockFace.NORTH, new Vec3(-1D, 0D, 0D),
      BlockFace.EAST, new Vec3(0D, 0D, -1D),
      BlockFace.WEST, new Vec3(0D, 0D, 1D)
  );

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
  void faceRotationPointsTheItemNormalOutOfTheFace(BlockFace face) {
    Quat rotation = HiddenDisplays.faceRotation(face);

    assertVector(rotate(rotation, ITEM_NORMAL), normal(face));
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
  void wallFacesKeepTheItemUpright(BlockFace face) {
    Quat rotation = HiddenDisplays.faceRotation(face);

    assertVector(rotate(rotation, UP), UP);
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
  void faceTransformationLaysTheItemFlatJustOffTheSurface(BlockFace face) {
    Transform transformation = HiddenDisplays.faceTransformation(face, 0.5F, Vec3.ZERO);

    assertVector(transformation.translation(), normal(face).multiply(0.51D));
    assertVector(rotate(transformation.leftRotation(), ITEM_NORMAL), normal(face));
    assertVector(transformation.scale(), new Vec3(0.5D, 0.5D, 0.5D));
    assertThat(transformation.rightRotation()).isEqualTo(Quat.IDENTITY);
    assertThat(transformation.translation().dot(normal(face)) - 0.5D)
        .isCloseTo(HiddenDisplays.SURFACE_OFFSET, within(EPSILON));
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
  void faceOffsetMovesAlongTheViewersRightAndUp(BlockFace face) {
    Transform transformation = HiddenDisplays.faceTransformation(face, 0.5F, new Vec3(0.2D, 0.1D, 0.05D));

    Vec3 expected = normal(face).multiply(0.56D)
        .add(VIEWER_RIGHT.get(face).multiply(0.2D))
        .add(UP.multiply(0.1D));
    assertVector(transformation.translation(), expected);
  }

  @Test
  void faceOffsetOnTheTopFaceMovesEastAndNorth() {
    Transform transformation = HiddenDisplays.faceTransformation(BlockFace.UP, 0.5F, new Vec3(0.2D, 0.1D, 0.05D));

    assertVector(transformation.translation(), new Vec3(0.2D, 0.56D, -0.1D));
  }

  @Test
  void faceOffsetOnTheBottomFaceMovesEastAndSouth() {
    Transform transformation = HiddenDisplays.faceTransformation(BlockFace.DOWN, 0.5F, new Vec3(0.2D, 0.1D, 0.05D));

    assertVector(transformation.translation(), new Vec3(0.2D, -0.56D, 0.1D));
  }

  @Test
  void nonCartesianFacesAreRejected() {
    assertThatThrownBy(() -> HiddenDisplays.faceRotation(BlockFace.NORTH_EAST))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HiddenDisplays.faceRotation(BlockFace.SELF))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HiddenDisplays.faceTransformation(BlockFace.SELF, 0.5F, Vec3.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
  void hitboxCentersOnTheFaceWithoutLeavingTheAnchorColumn(BlockFace face) {
    Vec3 offset = HiddenDisplays.hitboxOffset(face, 0.6F);
    Vec3 hitboxCenter = offset.add(0D, 0.3D, 0D);

    assertThat(hitboxCenter.distance(normal(face).multiply(0.5D))).isLessThan(0.01D);
    assertThat(0.5D + offset.x()).isGreaterThanOrEqualTo(0D).isLessThan(1D);
    assertThat(0.5D + offset.z()).isGreaterThanOrEqualTo(0D).isLessThan(1D);
  }

  @Test
  @SuppressWarnings("unchecked")
  void spawnFaceItemHidesTheDisplayBeforeItEntersTheWorld() {
    WorldRef world = mock(WorldRef.class);
    BlockRef block = block(world, 10, 64, -3);
    ItemDisplayRef display = mock(ItemDisplayRef.class);
    DataHolder data = mock(DataHolder.class);
    ItemRef item = mock(ItemRef.class);
    ItemRef icon = mock(ItemRef.class);
    when(item.copy()).thenReturn(icon);
    when(display.data()).thenReturn(data);
    ArgumentCaptor<Consumer<ItemDisplayRef>> prepare = ArgumentCaptor.forClass((Class<Consumer<ItemDisplayRef>>) (Class<?>) Consumer.class);
    when(world.spawnItemDisplay(any(Loc.class), eq(icon), prepare.capture())).thenAnswer(invocation -> {
      prepare.getValue().accept(display);
      world.isLoaded();
      return display;
    });

    ItemDisplayRef spawned = HiddenDisplays.spawnFaceItem(block, BlockFace.EAST, item, 0.5F, Vec3.ZERO);

    assertThat(spawned).isSameAs(display);
    ArgumentCaptor<Loc> location = ArgumentCaptor.forClass(Loc.class);
    verify(world).spawnItemDisplay(location.capture(), eq(icon), any());
    assertThat(location.getValue()).isEqualTo(new Loc(world, 10.5D, 64.5D, -2.5D));
    InOrder order = inOrder(display, data, world);
    order.verify(display).setVisibleByDefault(false);
    order.verify(display).setPersistent(false);
    order.verify(data).set(any(), any());
    order.verify(world).isLoaded();
    verify(display).setItem(icon);
    verify(display).setDisplayMode(ItemDisplayMode.FIXED);
    verify(display).setBrightness(new Brightness(15, 15));
    verify(display).setTransform(HiddenDisplays.faceTransformation(BlockFace.EAST, 0.5F, Vec3.ZERO));
  }

  @Test
  @SuppressWarnings("unchecked")
  void spawnLabelBillboardsTextOnAHiddenDisplay() {
    WorldRef world = mock(WorldRef.class);
    TextDisplayRef display = mock(TextDisplayRef.class);
    DataHolder data = mock(DataHolder.class);
    Loc location = new Loc(world, 1.5D, 70D, 2.5D);
    String text = "<aqua>Guard</aqua>";
    int background = 96 << 24;
    when(display.data()).thenReturn(data);
    ArgumentCaptor<Consumer<TextDisplayRef>> prepare = ArgumentCaptor.forClass((Class<Consumer<TextDisplayRef>>) (Class<?>) Consumer.class);
    when(world.spawnTextDisplay(any(Loc.class), eq(text), prepare.capture())).thenAnswer(invocation -> {
      prepare.getValue().accept(display);
      world.isLoaded();
      return display;
    });

    TextDisplayRef spawned = HiddenDisplays.spawnLabel(location, text, background, 0.25F);

    assertThat(spawned).isSameAs(display);
    verify(world).spawnTextDisplay(eq(location), eq(text), any());
    InOrder order = inOrder(display, data, world);
    order.verify(display).setVisibleByDefault(false);
    order.verify(display).setPersistent(false);
    order.verify(data).set(any(), any());
    order.verify(world).isLoaded();
    verify(display).setBillboard(Billboard.CENTER);
    verify(display).setText(text);
    verify(display).setBackgroundArgb(background);
    verify(display).setViewRange(0.25F);
  }

  @Test
  @SuppressWarnings("unchecked")
  void spawnFaceHitboxSizesAHiddenInteractionAtTheFace() {
    WorldRef world = mock(WorldRef.class);
    BlockRef block = block(world, 10, 64, -3);
    EntityRef interaction = mock(EntityRef.class);
    DataHolder data = mock(DataHolder.class);
    when(interaction.data()).thenReturn(data);
    ArgumentCaptor<Consumer<EntityRef>> prepare = ArgumentCaptor.forClass((Class<Consumer<EntityRef>>) (Class<?>) Consumer.class);
    when(world.spawnInteraction(any(Loc.class), anyFloat(), anyFloat(), anyBoolean(), prepare.capture())).thenAnswer(invocation -> {
      prepare.getValue().accept(interaction);
      world.isLoaded();
      return interaction;
    });

    EntityRef spawned = HiddenDisplays.spawnFaceHitbox(block, BlockFace.UP, 0.6F, 0.6F);

    assertThat(spawned).isSameAs(interaction);
    ArgumentCaptor<Loc> location = ArgumentCaptor.forClass(Loc.class);
    verify(world).spawnInteraction(location.capture(), eq(0.6F), eq(0.6F), eq(false), any());
    Vec3 offset = HiddenDisplays.hitboxOffset(BlockFace.UP, 0.6F);
    assertThat(location.getValue().x()).isCloseTo(10.5D + offset.x(), within(1.0E-6D));
    assertThat(location.getValue().y()).isCloseTo(64.5D + offset.y(), within(1.0E-6D));
    assertThat(location.getValue().z()).isCloseTo(-2.5D + offset.z(), within(1.0E-6D));
    InOrder order = inOrder(interaction, data, world);
    order.verify(interaction).setPersistent(false);
    order.verify(data).set(any(), any());
    order.verify(world).isLoaded();
  }

  @Test
  void showAndHideUseTheAdaptPluginInstance() {
    PlayerRef viewer = mock(PlayerRef.class);
    EntityRef entity = mock(EntityRef.class);
    ViewerHost viewers = core.getPlatform().viewers();

    HiddenDisplays.show(viewer, entity);
    HiddenDisplays.hide(viewer, entity);

    verify(viewers).showEntity(viewer, entity);
    verify(viewers).hideEntity(viewer, entity);
  }

  private static BlockRef block(WorldRef world, int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    when(block.world()).thenReturn(world);
    when(block.center()).thenReturn(new Loc(world, x + 0.5D, y + 0.5D, z + 0.5D));
    return block;
  }

  private static Vec3 normal(BlockFace face) {
    return new Vec3(face.dx(), face.dy(), face.dz());
  }

  private static Vec3 rotate(Quat q, Vec3 v) {
    Vec3 axis = new Vec3(q.x(), q.y(), q.z());
    Vec3 t = axis.cross(v).multiply(2.0D);
    return v.add(t.multiply(q.w())).add(axis.cross(t));
  }

  private static void assertVector(Vec3 actual, Vec3 expected) {
    assertThat(actual.x()).isCloseTo(expected.x(), within(EPSILON));
    assertThat(actual.y()).isCloseTo(expected.y(), within(EPSILON));
    assertThat(actual.z()).isCloseTo(expected.z(), within(EPSILON));
  }
}
