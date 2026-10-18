package art.arcane.adapt.util.common.compat;

import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.world.WeatheringCopperState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaperCompatTest {
  @Test
  void weatheringStagesCountOxidationStepsOfCopperGolemsOnly() {
    CopperGolem golem = mock(CopperGolem.class);

    for (WeatheringCopperState state : WeatheringCopperState.values()) {
      when(golem.getWeatheringState()).thenReturn(state);
      assertThat(PaperCompat.weatheringStage(golem)).as("stage of %s", state).isEqualTo(switch (state) {
        case UNAFFECTED -> 0;
        case EXPOSED -> 1;
        case WEATHERED -> 2;
        case OXIDIZED -> 3;
      });
    }
    assertThat(PaperCompat.weatheringStage(mock(IronGolem.class))).isZero();
  }

  @Test
  void rangedAttackFiresARangedMobsOwnAttackAndSkipsOtherMobs() {
    Snowman golem = mock(Snowman.class);
    IronGolem iron = mock(IronGolem.class);
    Zombie target = mock(Zombie.class);

    assertThat(PaperCompat.rangedAttack(golem, target)).isTrue();
    assertThat(PaperCompat.rangedAttack(iron, target)).isFalse();

    verify(golem).rangedAttack(target, 1F);
    verifyNoInteractions(iron);
  }

  @Test
  void freezeTicksLockedReportsAnotherPluginsFreezeLock() {
    Zombie locked = mock(Zombie.class);
    Zombie free = mock(Zombie.class);
    when(locked.isFreezeTickingLocked()).thenReturn(true);

    assertThat(PaperCompat.freezeTicksLocked(locked)).isTrue();
    assertThat(PaperCompat.freezeTicksLocked(free)).isFalse();
  }

  @Test
  void collisionChecksAskTheWorld() {
    World world = mock(World.class);
    BoundingBox blocked = new BoundingBox(0, 64, 0, 1, 66, 1);
    when(world.hasCollisionsIn(blocked)).thenReturn(true);

    assertThat(PaperCompat.hasCollisionsIn(world, blocked)).isTrue();
    assertThat(PaperCompat.hasCollisionsIn(world, new BoundingBox(5, 64, 5, 6, 66, 6))).isFalse();
  }

  @Test
  void hasChangedBlockIsFalseForFractionalMoveWithinSameBlock() {
    Location from = new Location(null, 10.2D, 64.0D, -3.9D);
    Location to = new Location(null, 10.8D, 64.9D, -3.1D);

    assertThat(PaperCompat.hasChangedBlock(from, to)).isFalse();
  }

  @Test
  void hasChangedBlockDetectsCrossingEachAxis() {
    Location from = new Location(null, 10.5D, 64.5D, -3.5D);

    assertThat(PaperCompat.hasChangedBlock(from, new Location(null, 11.1D, 64.5D, -3.5D))).isTrue();
    assertThat(PaperCompat.hasChangedBlock(from, new Location(null, 10.5D, 63.9D, -3.5D))).isTrue();
    assertThat(PaperCompat.hasChangedBlock(from, new Location(null, 10.5D, 64.5D, -2.9D))).isTrue();
  }

  @Test
  void hasChangedBlockFloorsNegativeCoordinates() {
    Location from = new Location(null, -0.4D, 64.0D, 0.0D);
    Location to = new Location(null, 0.4D, 64.0D, 0.0D);

    assertThat(PaperCompat.hasChangedBlock(from, to)).isTrue();
  }

  @Test
  void hasChangedBlockIsFalseForNullDestination() {
    Location from = new Location(null, 1.0D, 2.0D, 3.0D);

    assertThat(PaperCompat.hasChangedBlock(from, null)).isFalse();
  }

  @Test
  void hasClassReportsPresentAndMissingClasses() {
    assertThat(PaperCompat.hasClass("org.bukkit.Location")).isTrue();
    assertThat(PaperCompat.hasClass("io.papermc.paper.event.player.DoesNotExistEvent")).isFalse();
    // cached second lookup stays stable
    assertThat(PaperCompat.hasClass("io.papermc.paper.event.player.DoesNotExistEvent")).isFalse();
  }

  @Test
  void isReplaceableReportsWhatAPlacementWouldOverwrite() {
    Block grass = mock(Block.class);
    Block stone = mock(Block.class);
    when(grass.isReplaceable()).thenReturn(true);
    when(stone.isReplaceable()).thenReturn(false);

    assertThat(PaperCompat.isReplaceable(grass)).isTrue();
    assertThat(PaperCompat.isReplaceable(stone)).isFalse();
    assertThat(PaperCompat.isReplaceable(null)).isFalse();
  }

  @Test
  void tamedOwnerIdResolvesOwnerUuidWithoutPaperApi() {
    UUID ownerId = UUID.randomUUID();
    AnimalTamer owner = mock(AnimalTamer.class);
    when(owner.getUniqueId()).thenReturn(ownerId);
    Tameable tamed = mock(Tameable.class);
    when(tamed.getOwner()).thenReturn(owner);

    assertThat(PaperCompat.tamedOwnerId(tamed)).isEqualTo(ownerId);
  }

  @Test
  void tamedOwnerIdIsNullForOwnerlessEntity() {
    Tameable tamed = mock(Tameable.class);
    when(tamed.getOwner()).thenReturn(null);

    assertThat(PaperCompat.tamedOwnerId(tamed)).isNull();
  }

  @Test
  void nearbyEntityQueryStopsBeforeWorldAccessWhenFoliaDoesNotOwnTheFootprint() {
    World world = mock(World.class);
    Location center = new Location(world, 15.5D, 64.0D, 15.5D);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(center, 8.0D, 8.0D)).thenReturn(false);

      assertThat(PaperCompat.nearbyEntitiesByType(Player.class, center, 8.0D)).isEmpty();
    }

    verifyNoInteractions(world);
  }

  @Test
  void textSetsTheComponentOnPaperTextDisplays() {
    TextDisplay display = mock(TextDisplay.class);
    Component text = Component.text("Guard", NamedTextColor.GOLD);

    PaperCompat.text(display, text);

    verify(display).text(text);
  }

  @Test
  void textFallsBackToLegacyTextWhereComponentsAreMissing() {
    TextDisplay display = mock(TextDisplay.class);
    Component text = Component.text("Guard", NamedTextColor.GOLD);
    doThrow(new NoSuchMethodError("text")).when(display).text(any(Component.class));

    PaperCompat.text(display, text);

    verify(display).setText("\u00a76Guard");
  }

  @Test
  void enchantingWithLevelsUsesPaperWithTreasureAndFallsBackToTheBukkitHelper() {
    ItemFactory factory = mock(ItemFactory.class);
    ItemStack book = mock(ItemStack.class);
    ItemStack paperBook = mock(ItemStack.class);
    ItemStack bukkitBook = mock(ItemStack.class);
    Random random = new Random(7L);
    when(factory.enchantWithLevels(book, 30, true, random)).thenReturn(paperBook).thenThrow(new NoSuchMethodError("enchantWithLevels"));
    when(factory.enchantItem(book, 30, true)).thenReturn(bukkitBook);

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getItemFactory).thenReturn(factory);

      assertThat(PaperCompat.enchantWithLevels(book, 30, random)).isSameAs(paperBook);
      assertThat(PaperCompat.enchantWithLevels(book, 30, random)).isSameAs(bukkitBook);
      assertThat(PaperCompat.enchantWithLevels(book, 30, random)).isSameAs(bukkitBook);
    }

    verify(factory, times(2)).enchantWithLevels(book, 30, true, random);
  }
}
