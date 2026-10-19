package art.arcane.adapt.content.protector;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.perms.PermissibleActions;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FactionsClaimProtectorTest {
  private final FactionsClaimProtector protector = new FactionsClaimProtector();
  private final Player player = mock(Player.class);
  private final World world = mock(World.class);
  private final Location territory = new Location(world, 10, 64, 10);
  private final Location wilderness = new Location(world, 5_000, 64, 5_000);
  private final Faction foreign = mock(Faction.class);
  private final Faction wild = mock(Faction.class);
  private final FPlayer fplayer = mock(FPlayer.class);
  @TempDir
  File dataFolder;
  private MockedStatic<Board> boards;
  private MockedStatic<FPlayers> players;

  @BeforeEach
  void setUp() {
    FactionsPlugin factions = mock(FactionsPlugin.class);
    lenient().when(factions.getDataFolder()).thenReturn(dataFolder);
    lenient().when(factions.getServerUUID()).thenReturn(UUID.randomUUID());
    Board board;
    FPlayers registry;
    try (MockedStatic<FactionsPlugin> plugin = mockStatic(FactionsPlugin.class)) {
      plugin.when(FactionsPlugin::getInstance).thenReturn(factions);
      board = mock(Board.class);
      registry = mock(FPlayers.class);
      boards = mockStatic(Board.class);
      players = mockStatic(FPlayers.class);
    }
    boards.when(Board::getInstance).thenReturn(board);
    players.when(FPlayers::getInstance).thenReturn(registry);
    lenient().when(world.getName()).thenReturn("world");
    lenient().when(board.getFactionAt(any(FLocation.class)))
        .thenAnswer(invocation -> invocation.<FLocation>getArgument(0).getX() == 0 ? foreign : wild);
    lenient().when(registry.getByPlayer(player)).thenReturn(fplayer);
    lenient().when(wild.isWilderness()).thenReturn(true);
    lenient().when(foreign.isWilderness()).thenReturn(false);
    lenient().when(fplayer.getFaction()).thenReturn(mock(Faction.class));
  }

  @AfterEach
  void tearDown() {
    players.close();
    boards.close();
  }

  @Test
  void foreignTerritoryFollowsTheFactionsPermissions() {
    when(foreign.hasAccess(eq(fplayer), eq(PermissibleActions.DESTROY), any(FLocation.class))).thenReturn(false);
    when(foreign.hasAccess(eq(fplayer), eq(PermissibleActions.BUILD), any(FLocation.class))).thenReturn(true);
    when(foreign.hasAccess(eq(fplayer), eq(PermissibleActions.CONTAINER), any(FLocation.class))).thenReturn(false);

    assertThat(protector.canBlockBreak(player, territory, null)).isFalse();
    assertThat(protector.canBlockPlace(player, territory, null)).isTrue();
    assertThat(protector.canAccessChest(player, territory, null)).isFalse();
    assertThat(protector.checkRegion(player, territory, null)).isFalse();
  }

  @Test
  void monstersInForeignTerritoryCanBeHitButCreaturesCannot() {
    assertThat(protector.canPVE(player, entity(Zombie.class, territory), null)).isTrue();
    assertThat(protector.canPVE(player, entity(ArmorStand.class, territory), null)).isTrue();
    assertThat(protector.canPVE(player, entity(Cow.class, territory), null)).isFalse();
    assertThat(protector.canPVE(player, entity(Squid.class, territory), null)).isFalse();
  }

  @Test
  void locationOnlyDamageChecksAreNotLimitedByTerritory() {
    assertThat(protector.canPVE(player, territory, null)).isTrue();
  }

  @Test
  void interactionIsNotGatedByTheFactionsItemPermission() {
    lenient().when(foreign.hasAccess(any(FPlayer.class), any(), any(FLocation.class))).thenReturn(false);

    assertThat(protector.canInteract(player, territory, null)).isTrue();
  }

  @Test
  void wildernessAllowsEverything() {
    assertThat(protector.canBlockBreak(player, wilderness, null)).isTrue();
    assertThat(protector.canBlockPlace(player, wilderness, null)).isTrue();
    assertThat(protector.canAccessChest(player, wilderness, null)).isTrue();
    assertThat(protector.canInteract(player, wilderness, null)).isTrue();
    assertThat(protector.canPVE(player, entity(Cow.class, wilderness), null)).isTrue();
    assertThat(protector.checkRegion(player, wilderness, null)).isTrue();
  }

  @Test
  void adminBypassAndOwnTerritoryAllowEverything() {
    when(fplayer.isAdminBypassing()).thenReturn(true);
    assertThat(protector.canBlockBreak(player, territory, null)).isTrue();
    assertThat(protector.canAccessChest(player, territory, null)).isTrue();

    when(fplayer.isAdminBypassing()).thenReturn(false);
    when(fplayer.getFaction()).thenReturn(foreign);
    assertThat(protector.canPVE(player, entity(Cow.class, territory), null)).isTrue();
    assertThat(protector.checkRegion(player, territory, null)).isTrue();
  }

  @Test
  void regionChecksLookAtTheGivenLocationNotThePlayer() {
    lenient().when(player.getLocation()).thenReturn(wilderness);

    assertThat(protector.checkRegion(player, territory, null)).isFalse();
  }

  private static <T extends Entity> T entity(Class<T> type, Location at) {
    T entity = mock(type);
    lenient().when(entity.getLocation()).thenReturn(at);
    return entity;
  }
}
