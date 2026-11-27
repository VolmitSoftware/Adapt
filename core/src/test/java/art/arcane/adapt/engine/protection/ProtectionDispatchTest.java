package art.arcane.adapt.engine.protection;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtectionDispatchTest {
  private static final Id MINER = Id.adapt("pickaxe/vein_miner");

  private final PlayerRef player = mock(PlayerRef.class);
  private final Loc at = new Loc(mock(WorldRef.class), 3D, 64D, 3D);
  private final Recording claims = new Recording("claims");
  private final Adaptation<?> miner = mock(Adaptation.class);
  private final Platform platform = new Platform();
  private final ProtectionDispatch dispatch = new ProtectionDispatch(platform, Map.of(MINER, miner)::get);

  @AfterEach
  void unregister() {
    AdaptApi.protection().unregister(claims);
  }

  @Test
  void aKnownAdaptationAnswersThroughItsOwnProtectorSet() {
    AdaptApi.protection().register(claims);
    when(miner.allows(ProtectionCheck.BLOCK_BREAK, player, at, null)).thenReturn(true);

    assertThat(dispatch.allows(player, ProtectionCheck.BLOCK_BREAK, at, MINER)).isTrue();
    assertThat(claims.asked).isEmpty();
  }

  @Test
  void aCheckForNoAdaptationAsksTheDefaultProtectorsIncludingAddonOnes() {
    AdaptApi.protection().register(claims);

    assertThat(dispatch.allows(player, ProtectionCheck.BLOCK_BREAK, at, null)).isFalse();
    assertThat(dispatch.allows(player, ProtectionCheck.INTERACT, at, Id.of("other", "unknown"))).isTrue();
    assertThat(claims.asked).containsExactly(ProtectionCheck.BLOCK_BREAK, ProtectionCheck.INTERACT);
  }

  @Test
  void thePlatformRefusingAnOfflineActorWins() {
    AdaptApi.protection().register(claims);
    platform.present = false;

    assertThat(dispatch.allows(player, ProtectionCheck.INTERACT, at, null)).isFalse();
    assertThat(dispatch.allowsDamage(player, mock(EntityRef.class), MINER)).isFalse();
    assertThat(claims.asked).isEmpty();
  }

  @Test
  void damageIsAPvpCheckForAPlayerAndAPveCheckOtherwise() {
    AdaptApi.protection().register(claims);
    PlayerRef other = mock(PlayerRef.class);
    EntityRef cow = mock(EntityRef.class);
    when(other.location()).thenReturn(at);
    when(cow.location()).thenReturn(at);

    dispatch.allowsDamage(player, other, null);
    dispatch.allowsDamage(player, cow, null);

    assertThat(claims.asked).containsExactly(ProtectionCheck.PVP, ProtectionCheck.PVE);
  }

  @Test
  void damageForAKnownAdaptationUsesItsTargetRules() {
    EntityRef cow = mock(EntityRef.class);
    when(miner.canDamageTarget(player, cow)).thenReturn(true);

    assertThat(dispatch.allowsDamage(player, cow, MINER)).isTrue();
    verify(miner).canDamageTarget(player, cow);
  }

  private static final class Recording implements Protector {
    private final String name;
    private final List<ProtectionCheck> asked = new ArrayList<>();

    private Recording(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean enabledByDefault() {
      return true;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim, Id adaptation) {
      asked.add(check);
      return check != ProtectionCheck.BLOCK_BREAK;
    }
  }

  private static final class Platform implements ProtectionHost {
    private boolean present = true;

    @Override
    public boolean allows(PlayerRef actor, ProtectionCheck check, Loc at, Id adaptation) {
      return present;
    }

    @Override
    public boolean allowsDamage(PlayerRef actor, EntityRef target, Id adaptation) {
      return present;
    }

    @Override
    public boolean attemptBreak(PlayerRef actor, BlockRef block) {
      return false;
    }
  }
}
