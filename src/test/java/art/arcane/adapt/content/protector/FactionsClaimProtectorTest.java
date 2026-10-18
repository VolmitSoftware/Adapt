package art.arcane.adapt.content.protector;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactionsClaimProtectorTest {
  private final Player player = mock(Player.class);
  private final World world = mock(World.class);
  private final Location territory = new Location(world, 10, 64, 10);
  private final Location wilderness = new Location(world, 5_000, 64, 5_000);
  private final Faction foreign = mock(Faction.class);
  private final Faction wild = mock(Faction.class);
  private final FPlayer fplayer = mock(FPlayer.class);
  private final FactionsClaimProtector.Territory land = mock(FactionsClaimProtector.Territory.class);
  private final FactionsClaimProtector protector = new FactionsClaimProtector(land);

  @BeforeEach
  void setUp() {
    lenient().when(land.factionAt(territory)).thenReturn(foreign);
    lenient().when(land.factionAt(wilderness)).thenReturn(wild);
    lenient().when(land.player(player)).thenReturn(fplayer);
    lenient().when(wild.isWilderness()).thenReturn(true);
    lenient().when(foreign.isWilderness()).thenReturn(false);
    lenient().when(fplayer.getFaction()).thenReturn(mock(Faction.class));
  }

  @Test
  void foreignTerritoryFollowsTheFactionsPermissions() {
    when(land.hasAccess(eq(foreign), eq(fplayer), eq(FactionsClaimProtector.Permission.DESTROY), any(Location.class))).thenReturn(false);
    when(land.hasAccess(eq(foreign), eq(fplayer), eq(FactionsClaimProtector.Permission.BUILD), any(Location.class))).thenReturn(true);
    when(land.hasAccess(eq(foreign), eq(fplayer), eq(FactionsClaimProtector.Permission.CONTAINER), any(Location.class))).thenReturn(false);
    when(land.hasAccess(eq(foreign), eq(fplayer), eq(FactionsClaimProtector.Permission.ITEM), any(Location.class))).thenReturn(true);

    assertThat(protector.canBlockBreak(player, territory, null)).isFalse();
    assertThat(protector.canBlockPlace(player, territory, null)).isTrue();
    assertThat(protector.canAccessChest(player, territory, null)).isFalse();
    assertThat(protector.canInteract(player, territory, null)).isTrue();
    assertThat(protector.canPVE(player, territory, null)).isFalse();
    assertThat(protector.checkRegion(player, territory, null)).isFalse();
  }

  @Test
  void wildernessAllowsEverything() {
    assertThat(protector.canBlockBreak(player, wilderness, null)).isTrue();
    assertThat(protector.canBlockPlace(player, wilderness, null)).isTrue();
    assertThat(protector.canAccessChest(player, wilderness, null)).isTrue();
    assertThat(protector.canInteract(player, wilderness, null)).isTrue();
    assertThat(protector.canPVE(player, wilderness, null)).isTrue();
    assertThat(protector.checkRegion(player, wilderness, null)).isTrue();
  }

  @Test
  void adminBypassAndOwnTerritoryAllowEverything() {
    when(fplayer.isAdminBypassing()).thenReturn(true);
    assertThat(protector.canBlockBreak(player, territory, null)).isTrue();
    assertThat(protector.canAccessChest(player, territory, null)).isTrue();

    when(fplayer.isAdminBypassing()).thenReturn(false);
    when(fplayer.getFaction()).thenReturn(foreign);
    assertThat(protector.canPVE(player, territory, null)).isTrue();
    assertThat(protector.checkRegion(player, territory, null)).isTrue();
  }

  @Test
  void regionChecksLookAtTheGivenLocationNotThePlayer() {
    when(player.getLocation()).thenReturn(wilderness);

    assertThat(protector.checkRegion(player, territory, null)).isFalse();
  }
}
