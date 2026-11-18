package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.ArmorStandStyle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StealthShadowDecoyAnchorTest extends AdaptTestBase {
  @Test
  void fallbackShowsTheArmorStandWhenNoPacketPlayerIsAvailable() {
    StealthShadowDecoy adaptation = new StealthShadowDecoy();
    LivingRef anchor = mock(LivingRef.class);
    PlayerRef owner = mock(PlayerRef.class);
    when(owner.name()).thenReturn("Owner");
    when(owner.inventory()).thenReturn(mock(PlayerInventoryRef.class));
    when(AdaptCore.platform().host().items()).thenReturn(mock(ItemOps.class));
    ViewerHost viewers = AdaptCore.platform().viewers();

    adaptation.configureLegacyVisual(anchor, owner);

    verify(viewers).setVisibleByDefault(anchor, true);
    verify(anchor).setInvisible(false);
  }

  @Test
  void anchorRemainsAttackableToMobsWithoutSpawningOnClients() {
    StealthShadowDecoy adaptation = new StealthShadowDecoy();
    WorldRef world = mock(WorldRef.class);
    PlayerRef owner = mock(PlayerRef.class);
    LivingRef anchor = mock(LivingRef.class);
    Loc location = new Loc(world, 0, 64, 0);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    when(anchor.data()).thenReturn(mock(DataHolder.class));
    ArgumentCaptor<ArmorStandStyle> style = ArgumentCaptor.forClass(ArmorStandStyle.class);
    when(world.spawnArmorStand(eq(location), any(ArmorStandStyle.class), ArgumentMatchers.<Consumer<EntityRef>>any())).thenAnswer(invocation -> {
      Consumer<EntityRef> initializer = invocation.getArgument(2);
      initializer.accept(anchor);
      return anchor;
    });
    ViewerHost viewers = AdaptCore.platform().viewers();

    assertThat(adaptation.spawnAnchor(owner, location, false)).isSameAs(anchor);

    verify(world).spawnArmorStand(eq(location), style.capture(), ArgumentMatchers.<Consumer<EntityRef>>any());
    verify(anchor).setInvisible(false);
    verify(viewers).setVisibleByDefault(anchor, false);
    verify(anchor, never()).setInvisible(true);
    assertThat(style.getValue().marker()).isFalse();
    verify(anchor).setInvulnerable(false);
  }
}
