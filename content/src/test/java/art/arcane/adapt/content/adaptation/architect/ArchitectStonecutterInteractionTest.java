package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectStonecutterInteractionTest extends AdaptTestBase {
    @ParameterizedTest
    @CsvSource({"true,false,false,true", "true,false,true,true", "false,true,false,true", "false,true,true,false", "false,false,false,false", "false,false,true,false"})
    void carriedStonecutterUsesStorageAndOffhandAccordingToConfiguredRequirement(boolean offhand, boolean storage, boolean required, boolean permitted) {
        ArchitectStonecutterSavant adaptation = spy(TestContent.adaptation(Id.adapt("architect"), ArchitectStonecutterSavant::new));
        adaptation.getConfig().requireOffhand = required;
        PlayerRef player = mock(PlayerRef.class);
        PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
        when(player.inventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(player.location()).thenReturn(new Loc(mock(WorldRef.class), 0, 100, 0));
        ItemRef empty = mock(ItemRef.class);
        when(empty.type()).thenReturn(Id.minecraft("air"));
        when(empty.isEmpty()).thenReturn(true);
        when(inventory.mainHand()).thenReturn(empty);
        ItemRef carried = mock(ItemRef.class);
        when(carried.type()).thenReturn(offhand ? Id.minecraft("stonecutter") : Id.minecraft("air"));
        when(inventory.offHand()).thenReturn(carried);
        when(inventory.contains(Id.minecraft("stonecutter"), 1)).thenReturn(storage);
        doReturn(false).when(adaptation).preferenceEnabled(player, ArchitectStonecutterSavant.OFFHAND);
        doReturn(null).when(adaptation).resolveInteractContext(eq(player), any(Loc.class), any());
        PlayerInteractEvent event = new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, null,
            null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT);
        adaptation.on(event);
        if (permitted) verify(adaptation).resolveInteractContext(eq(player), any(Loc.class), any());
        else verify(adaptation, never()).resolveInteractContext(eq(player), any(Loc.class), any());
    }
}
