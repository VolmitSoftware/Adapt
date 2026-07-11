package art.arcane.adapt.content.mutation.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MutationCombatRuntimeTest {
  @Test
  void pickaxesAreToolsInsteadOfHeavyAxes() {
    assertThat(MutationCombatRuntime.weaponFamilyFor(Material.DIAMOND_PICKAXE, false))
        .isEqualTo(MutationWeaponFamily.TOOL);
    assertThat(MutationCombatRuntime.weaponFamilyFor(Material.IRON_AXE, false))
        .isEqualTo(MutationWeaponFamily.HEAVY);
  }

  @Test
  void weaponFamiliesRemainStableAcrossRepresentativeItems() {
    assertThat(MutationCombatRuntime.weaponFamilyFor(Material.DIAMOND_SWORD, false))
        .isEqualTo(MutationWeaponFamily.PRECISION);
    assertThat(MutationCombatRuntime.weaponFamilyFor(Material.CROSSBOW, false))
        .isEqualTo(MutationWeaponFamily.RANGED);
    assertThat(MutationCombatRuntime.weaponFamilyFor(Material.AIR, false))
        .isEqualTo(MutationWeaponFamily.BODY);
    assertThat(MutationCombatRuntime.weaponFamilyFor(Material.DIAMOND_SWORD, true))
        .isEqualTo(MutationWeaponFamily.RANGED);
  }

  @Test
  void authorizationCapabilitiesRequireTheSameTargetBlock() {
    World world = mock(World.class);
    Location authorized = new Location(world, 10.2D, 64D, -4.8D);
    Location sameBlock = new Location(world, 10.9D, 64.9D, -4.1D);
    Location movedBlock = new Location(world, 11D, 64D, -4D);

    assertThat(MutationCombatRuntime.sameBlock(authorized, sameBlock)).isTrue();
    assertThat(MutationMovementRuntime.sameBlock(authorized, sameBlock)).isTrue();
    assertThat(MutationFormulaRuntime.sameBlock(authorized, sameBlock)).isTrue();
    assertThat(MutationCombatRuntime.sameBlock(authorized, movedBlock)).isFalse();
    assertThat(MutationMovementRuntime.sameBlock(authorized, movedBlock)).isFalse();
    assertThat(MutationFormulaRuntime.sameBlock(authorized, movedBlock)).isFalse();
  }

  @Test
  void trophyReservationsRequireAnExactUnexpiredDurableImprint() {
    assertThat(MutationCombatRuntime.activeTrophyReservation(
        "undead", 2_000L, "undead", 2_000L, 1_999L
    )).isTrue();
    assertThat(MutationCombatRuntime.activeTrophyReservation(
        "undead", 2_000L, "undead", 2_000L, 2_000L
    )).isFalse();
    assertThat(MutationCombatRuntime.activeTrophyReservation(
        "undead", 2_000L, "beast", 2_000L, 1_000L
    )).isFalse();
    assertThat(MutationCombatRuntime.activeTrophyReservation(
        "undead", 2_000L, "undead", 3_000L, 1_000L
    )).isFalse();
  }
}
