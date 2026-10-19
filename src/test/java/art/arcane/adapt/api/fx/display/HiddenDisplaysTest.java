package art.arcane.adapt.api.fx.display;

import art.arcane.adapt.AdaptTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiddenDisplaysTest extends AdaptTestBase {
  private static final float EPSILON = 1.0E-5F;
  private static final Vector3f ITEM_NORMAL = new Vector3f(0F, 0F, 1F);
  private static final Vector3f UP = new Vector3f(0F, 1F, 0F);
  private static final Map<BlockFace, Vector3f> VIEWER_RIGHT = Map.of(
      BlockFace.SOUTH, new Vector3f(1F, 0F, 0F),
      BlockFace.NORTH, new Vector3f(-1F, 0F, 0F),
      BlockFace.EAST, new Vector3f(0F, 0F, -1F),
      BlockFace.WEST, new Vector3f(0F, 0F, 1F)
  );

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
  void faceRotationPointsTheItemNormalOutOfTheFace(BlockFace face) {
    Quaternionf rotation = HiddenDisplays.faceRotation(face);

    assertVector(rotation.transform(new Vector3f(ITEM_NORMAL)), normal(face));
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
  void wallFacesKeepTheItemUpright(BlockFace face) {
    Quaternionf rotation = HiddenDisplays.faceRotation(face);

    assertVector(rotation.transform(new Vector3f(UP)), UP);
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
  void faceTransformationLaysTheItemFlatJustOffTheSurface(BlockFace face) {
    Transformation transformation = HiddenDisplays.faceTransformation(face, 0.5F, new Vector3f());

    assertVector(transformation.getTranslation(), normal(face).mul(0.51F));
    assertVector(transformation.getLeftRotation().transform(new Vector3f(ITEM_NORMAL)), normal(face));
    assertVector(transformation.getScale(), new Vector3f(0.5F, 0.5F, 0.5F));
    assertThat(transformation.getRightRotation()).isEqualTo(new Quaternionf());
    assertThat(transformation.getTranslation().dot(normal(face)) - 0.5F)
        .isCloseTo(HiddenDisplays.SURFACE_OFFSET, within(EPSILON));
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
  void faceOffsetMovesAlongTheViewersRightAndUp(BlockFace face) {
    Transformation transformation = HiddenDisplays.faceTransformation(face, 0.5F, new Vector3f(0.2F, 0.1F, 0.05F));

    Vector3f expected = normal(face).mul(0.56F)
        .add(new Vector3f(VIEWER_RIGHT.get(face)).mul(0.2F))
        .add(new Vector3f(UP).mul(0.1F));
    assertVector(transformation.getTranslation(), expected);
  }

  @Test
  void faceOffsetOnTheTopFaceMovesEastAndNorth() {
    Transformation transformation = HiddenDisplays.faceTransformation(BlockFace.UP, 0.5F, new Vector3f(0.2F, 0.1F, 0.05F));

    assertVector(transformation.getTranslation(), new Vector3f(0.2F, 0.56F, -0.1F));
  }

  @Test
  void faceOffsetOnTheBottomFaceMovesEastAndSouth() {
    Transformation transformation = HiddenDisplays.faceTransformation(BlockFace.DOWN, 0.5F, new Vector3f(0.2F, 0.1F, 0.05F));

    assertVector(transformation.getTranslation(), new Vector3f(0.2F, -0.56F, 0.1F));
  }

  @Test
  void nonCartesianFacesAreRejected() {
    assertThatThrownBy(() -> HiddenDisplays.faceRotation(BlockFace.NORTH_EAST))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> HiddenDisplays.faceTransformation(BlockFace.SELF, 0.5F, new Vector3f()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @EnumSource(value = BlockFace.class, names = {"NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN"})
  void hitboxCentersOnTheFaceWithoutLeavingTheAnchorColumn(BlockFace face) {
    Vector3f offset = HiddenDisplays.hitboxOffset(face, 0.6F);

    Vector3f hitboxCenter = new Vector3f(offset).add(0F, 0.3F, 0F);
    assertThat(hitboxCenter.distance(normal(face).mul(0.5F))).isLessThan(0.01F);
    assertThat(0.5F + offset.x).isGreaterThanOrEqualTo(0F).isLessThan(1F);
    assertThat(0.5F + offset.z).isGreaterThanOrEqualTo(0F).isLessThan(1F);
  }

  @Test
  void spawnFaceItemHidesTheDisplayBeforeItEntersTheWorld() {
    World world = mock(World.class);
    Block block = block(world, 10, 64, -3);
    ItemDisplay display = mock(ItemDisplay.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    ItemStack item = mock(ItemStack.class);
    ItemStack icon = mock(ItemStack.class);
    when(item.clone()).thenReturn(icon);
    when(display.getPersistentDataContainer()).thenReturn(data);
    when(world.createEntity(any(Location.class), eq(ItemDisplay.class))).thenReturn(display);
    when(world.addEntity(display)).thenReturn(display);

    ItemDisplay spawned = HiddenDisplays.spawnFaceItem(block, BlockFace.EAST, item, 0.5F, new Vector3f());

    assertThat(spawned).isSameAs(display);
    ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
    verify(world).createEntity(location.capture(), eq(ItemDisplay.class));
    assertThat(location.getValue()).isEqualTo(new Location(world, 10.5D, 64.5D, -2.5D));
    InOrder order = inOrder(display, data, world);
    order.verify(display).setVisibleByDefault(false);
    order.verify(display).setPersistent(false);
    order.verify(data).set(any(), any(), any());
    order.verify(world).addEntity(display);
    verify(display).setItemStack(icon);
    verify(display).setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
    verify(display).setBrightness(new Display.Brightness(15, 15));
    verify(display).setTransformation(HiddenDisplays.faceTransformation(BlockFace.EAST, 0.5F, new Vector3f()));
  }

  @Test
  void spawnLabelBillboardsTextOnAHiddenDisplay() {
    World world = mock(World.class);
    TextDisplay display = mock(TextDisplay.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    Location location = new Location(world, 1.5D, 70D, 2.5D);
    Component text = Component.text("Guard");
    Color background = Color.fromARGB(96, 0, 0, 0);
    when(display.getPersistentDataContainer()).thenReturn(data);
    when(world.createEntity(any(Location.class), eq(TextDisplay.class))).thenReturn(display);
    when(world.addEntity(display)).thenReturn(display);

    TextDisplay spawned = HiddenDisplays.spawnLabel(location, text, background, 0.25F);

    assertThat(spawned).isSameAs(display);
    verify(world).createEntity(eq(location), eq(TextDisplay.class));
    InOrder order = inOrder(display, data, world);
    order.verify(display).setVisibleByDefault(false);
    order.verify(display).setPersistent(false);
    order.verify(data).set(any(), any(), any());
    order.verify(world).addEntity(display);
    verify(display).setBillboard(Display.Billboard.CENTER);
    verify(display).text(text);
    verify(display).setBackgroundColor(background);
    verify(display).setViewRange(0.25F);
  }

  @Test
  void spawnFaceHitboxSizesAHiddenInteractionAtTheFace() {
    World world = mock(World.class);
    Block block = block(world, 10, 64, -3);
    Interaction interaction = mock(Interaction.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(interaction.getPersistentDataContainer()).thenReturn(data);
    when(world.createEntity(any(Location.class), eq(Interaction.class))).thenReturn(interaction);
    when(world.addEntity(interaction)).thenReturn(interaction);

    Interaction spawned = HiddenDisplays.spawnFaceHitbox(block, BlockFace.UP, 0.6F, 0.6F);

    assertThat(spawned).isSameAs(interaction);
    ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
    verify(world).createEntity(location.capture(), eq(Interaction.class));
    Vector3f offset = HiddenDisplays.hitboxOffset(BlockFace.UP, 0.6F);
    assertThat(location.getValue().getX()).isCloseTo(10.5D + offset.x, within(1.0E-6D));
    assertThat(location.getValue().getY()).isCloseTo(64.5D + offset.y, within(1.0E-6D));
    assertThat(location.getValue().getZ()).isCloseTo(-2.5D + offset.z, within(1.0E-6D));
    InOrder order = inOrder(interaction, data, world);
    order.verify(interaction).setVisibleByDefault(false);
    order.verify(interaction).setPersistent(false);
    order.verify(data).set(any(), any(), any());
    order.verify(world).addEntity(interaction);
    verify(interaction).setInteractionWidth(0.6F);
    verify(interaction).setInteractionHeight(0.6F);
  }

  @Test
  void showAndHideUseTheAdaptPluginInstance() {
    Player viewer = mock(Player.class);
    Entity entity = mock(Entity.class);

    HiddenDisplays.show(viewer, entity);
    HiddenDisplays.hide(viewer, entity);

    verify(viewer).showEntity(plugin, entity);
    verify(viewer).hideEntity(plugin, entity);
  }

  private static Block block(World world, int x, int y, int z) {
    Block block = mock(Block.class);
    when(block.getWorld()).thenReturn(world);
    when(block.getX()).thenReturn(x);
    when(block.getY()).thenReturn(y);
    when(block.getZ()).thenReturn(z);
    return block;
  }

  private static Vector3f normal(BlockFace face) {
    return new Vector3f(face.getModX(), face.getModY(), face.getModZ());
  }

  private static void assertVector(Vector3f actual, Vector3f expected) {
    assertThat(actual.x).isCloseTo(expected.x, within(EPSILON));
    assertThat(actual.y).isCloseTo(expected.y, within(EPSILON));
    assertThat(actual.z).isCloseTo(expected.z, within(EPSILON));
  }
}
