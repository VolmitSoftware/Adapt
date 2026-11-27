package art.arcane.adapt.content.golem;

import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.content.adaptation.golemancy.GolemRancherCore;
import art.arcane.adapt.content.golem.command.GolemCommand;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeCodec;
import art.arcane.adapt.content.golem.node.NodeFaces;
import art.arcane.adapt.content.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.content.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.engine.addon.AddonContent;
import art.arcane.adapt.engine.addon.EngineRegistrar;
import art.arcane.adapt.engine.addon.EnginePlayerSection;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Golemancy's keys in saves, world data, item and entity data and permissions; a change here breaks existing saves. */
class GolemPersistedKeysTest {
  @Test
  void theBuiltinAddonDeclaresTheRosterAndSlicesUnderTheirSavedIds() {
    EngineRegistrar registrar = new EngineRegistrar(Id.ADAPT);
    new BuiltinAddon().register(registrar);
    AddonContent content = registrar.content();

    assertThat(content.sections()).extracting((EnginePlayerSection<?> section) -> section.name())
        .containsExactly("adapt:golemancy");
    assertThat(content.slices()).extracting((BlockSlice<?> slice) -> slice.id().toString())
        .containsExactly("adapt:node", "adapt:plinth_slot");
    assertThat(GolemRoster.section()).isSameAs(content.sections().getFirst());
    assertThat(NodeCodec.slice().type()).isEqualTo(NodeFaces.class);
    assertThat(PlinthSlotCodec.slice().type()).isEqualTo(PlinthSlotItem.class);
  }

  @Test
  void itemAndEntityDataKeysKeepTheirIds() throws ReflectiveOperationException {
    Class<?> snowball = Class.forName("art.arcane.adapt.content.adaptation.golemancy.GolemSnowball");
    assertThat(List.of(
        key(GolemItems.class, "KIND_KEY"),
        key(GolemItems.class, "DATA_KEY"),
        key(GolemItems.class, "CROOK_CHANNEL_KEY"),
        key(GolemState.class, "KEY"),
        key(GolemRitualMarker.class, "KEY"),
        key(HostileDamagePool.class, "POOL_KEY"),
        key(GolemRegistry.class, "PACKING_TOKEN_KEY"),
        key(GolemRegistry.class, "PACKING_KEY"),
        key(GolemRegistry.class, "BOUND_ONCE_KEY"),
        key(GolemRancherCore.class, "FED_TICK_KEY"),
        key(snowball, "KEY"),
        key(snowball, "TARGET_KEY"),
        key(snowball, "EXTRA_KEY")))
        .containsExactly("adapt:golem_item", "adapt:golem_item_data", "adapt:crook_channel", "adapt:golem", "adapt:golem_ritual",
            "adapt:golem_hostile_pool", "adapt:golem_packing_token", "adapt:golem_packing", "adapt:golem_bound_once",
            "adapt:golem_fed_tick", "adapt:golem_snowball", "adapt:golem_snowball_target", "adapt:golem_snowball_extra");
  }

  @Test
  void theCustomItemAndAdminPermissionKeepTheirIds() {
    assertThat(GolemItems.ITEM.id().toString()).isEqualTo("adapt:golemancy");
    assertThat(GolemCommand.ADMIN_PERMISSION).isEqualTo("adapt.golemancy.admin");
  }

  private static String key(Class<?> owner, String field) throws ReflectiveOperationException {
    Field declared = owner.getDeclaredField(field);
    declared.setAccessible(true);
    return ((DataKey<?>) declared.get(null)).id().toString();
  }
}
