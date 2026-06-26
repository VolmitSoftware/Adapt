package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class XpNoveltyTest extends AdaptTestBase {

    private Player stillPlayerAt(UUID id, Location loc) {
        Player p = mock(Player.class);
        lenient().when(p.getUniqueId()).thenReturn(id);
        lenient().when(p.getLocation()).thenReturn(loc);
        lenient().when(p.getLocation(any(Location.class))).thenReturn(loc);
        return p;
    }

    private Location fixedLocation() {
        World world = mock(World.class);
        lenient().when(world.getName()).thenReturn("world");
        Location loc = mock(Location.class, RETURNS_DEEP_STUBS);
        lenient().when(loc.getWorld()).thenReturn(world);
        lenient().when(loc.getBlockX()).thenReturn(100);
        lenient().when(loc.getBlockY()).thenReturn(64);
        lenient().when(loc.getBlockZ()).thenReturn(100);
        lenient().when(loc.getX()).thenReturn(100.0);
        lenient().when(loc.getY()).thenReturn(64.0);
        lenient().when(loc.getZ()).thenReturn(100.0);
        return loc;
    }

    @Test
    @DisplayName("the first novelty award sits within (0, 1]")
    void firstAwardWithinUnit() {
        UUID id = UUID.randomUUID();
        Location loc = fixedLocation();
        double first = XpNovelty.noveltyMultiplier(stillPlayerAt(id, loc), loc, "mine-stone");
        assertThat(first).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        XpNovelty.clear(id);
    }

    @Test
    @DisplayName("repeating the same action in place decays the multiplier")
    void repeatedActionDecays() {
        UUID id = UUID.randomUUID();
        Location loc = fixedLocation();
        Player p = stillPlayerAt(id, loc);
        double first = XpNovelty.noveltyMultiplier(p, loc, "mine-stone");
        double last = first;
        for (int i = 0; i < 30; i++) {
            last = XpNovelty.noveltyMultiplier(p, loc, "mine-stone");
        }
        assertThat(last).isGreaterThan(0.0);
        assertThat(last).isLessThanOrEqualTo(first);
        XpNovelty.clear(id);
    }

    @Test
    @DisplayName("clearing a player resets the decay")
    void clearResetsDecay() {
        UUID id = UUID.randomUUID();
        Location loc = fixedLocation();
        Player p = stillPlayerAt(id, loc);
        double decayed = 1.0;
        for (int i = 0; i < 30; i++) {
            decayed = XpNovelty.noveltyMultiplier(p, loc, "mine-stone");
        }
        XpNovelty.clear(id);
        double afterClear = XpNovelty.noveltyMultiplier(p, loc, "mine-stone");
        assertThat(afterClear).isGreaterThanOrEqualTo(decayed);
        XpNovelty.clear(id);
    }
}
