package art.arcane.adapt.engine.protection;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtectorsTest {
  private static final Id MINER = Id.adapt("pickaxe/vein_miner");

  private final List<Protector> registered = new ArrayList<>();
  private final PlayerRef player = mock(PlayerRef.class);
  private final Loc at = new Loc(mock(WorldRef.class), 3D, 64D, 3D);

  @AfterEach
  void unregister() {
    registered.forEach(AdaptApi.protection()::unregister);
  }

  @Test
  void aProtectorRegisteredThroughTheApiDeniesABlockBreak() {
    Claims claims = register(new Claims("claims", true));

    assertThat(Protectors.allows(Set.of("claims"), ProtectionCheck.BLOCK_BREAK, player, at, null, MINER)).isFalse();
    assertThat(Protectors.allows(Set.of("claims"), ProtectionCheck.INTERACT, player, at, null, MINER)).isTrue();
    assertThat(claims.asked).containsExactly(MINER, MINER);
  }

  @Test
  void onlyTheNamedProtectorsAreAsked() {
    register(new Claims("claims", true));

    assertThat(Protectors.allows(Set.of("other"), ProtectionCheck.BLOCK_BREAK, player, at, null, MINER)).isTrue();
    assertThat(Protectors.allows(Set.of(), ProtectionCheck.BLOCK_BREAK, player, at, null, MINER)).isTrue();
  }

  @Test
  void defaultNamesFollowEnabledByDefault() {
    Claims claims = register(new Claims("claims", false));
    register(new Claims("locks", true));

    assertThat(Protectors.allNames()).containsExactly("claims", "locks");
    assertThat(Protectors.defaultNames()).containsExactly("locks");
    claims.byDefault = true;
    assertThat(Protectors.defaultNames()).containsExactly("claims", "locks");
  }

  @Test
  void anUnregisteredProtectorIsNoLongerAsked() {
    Claims claims = register(new Claims("claims", true));
    AdaptApi.protection().unregister(claims);

    assertThat(Protectors.allows(Set.of("claims"), ProtectionCheck.BLOCK_BREAK, player, at, null, MINER)).isTrue();
    assertThat(Protectors.allNames()).isEmpty();
  }

  @Test
  void aSecondProtectorWithTheSameNameIsRefused() {
    register(new Claims("claims", true));

    assertThatThrownBy(() -> AdaptApi.protection().register(new Claims("claims", true)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void withoutALocationTheVictimsIsUsed() {
    Claims claims = register(new Claims("claims", true));
    EntityRef victim = mock(EntityRef.class);
    when(victim.location()).thenReturn(at);

    assertThat(Protectors.allows(Set.of("claims"), ProtectionCheck.PVE, player, null, victim, MINER)).isTrue();
    assertThat(claims.locations).containsExactly(at);
  }

  @Test
  void aCheckForNoAdaptationAsksEveryProtectorEnabledByDefault() {
    Claims claims = register(new Claims("claims", true));
    Claims locks = register(new Claims("locks", false));

    assertThat(Protectors.defaultsAllow(ProtectionCheck.BLOCK_BREAK, player, at, null)).isFalse();
    assertThat(Protectors.defaultsAllow(ProtectionCheck.INTERACT, player, at, null)).isTrue();
    assertThat(claims.asked).containsExactly(null, null);
    assertThat(locks.asked).isEmpty();
  }

  @Test
  void aThrowingProtectorDeniesInsteadOfEscaping() {
    register(new Protector() {
      @Override
      public String name() {
        return "fragile";
      }

      @Override
      public boolean enabledByDefault() {
        return true;
      }

      @Override
      public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim, Id adaptation) {
        throw new IllegalStateException("database offline");
      }
    });

    assertThat(Protectors.allows(Set.of("fragile"), ProtectionCheck.BLOCK_BREAK, player, at, null, MINER)).isFalse();
    assertThat(Protectors.defaultsAllow(ProtectionCheck.PVE, player, at, null)).isFalse();
  }

  private Claims register(Claims claims) {
    register((Protector) claims);
    return claims;
  }

  private void register(Protector protector) {
    AdaptApi.protection().register(protector);
    registered.add(protector);
  }

  private static final class Claims implements Protector {
    private final String name;
    private final List<Id> asked = new ArrayList<>();
    private final List<Loc> locations = new ArrayList<>();
    private boolean byDefault;

    private Claims(String name, boolean byDefault) {
      this.name = name;
      this.byDefault = byDefault;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean enabledByDefault() {
      return byDefault;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim, Id adaptation) {
      asked.add(adaptation);
      locations.add(location);
      return check != ProtectionCheck.BLOCK_BREAK;
    }
  }
}
