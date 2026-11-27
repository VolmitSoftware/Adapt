package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.integration.HiddenVein;
import art.arcane.adapt.api.integration.PluginContainer;
import art.arcane.adapt.api.integration.TreeFelling;
import art.arcane.adapt.api.internal.IntegrationRuntime;
import art.arcane.adapt.engine.insight.GlossInsightPort;
import art.arcane.adapt.engine.integration.AdvancedChestsPort;
import art.arcane.adapt.engine.integration.HiddenOrePort;
import art.arcane.adapt.engine.integration.IrisTreeFellerPort;
import art.arcane.adapt.engine.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.engine.adaptation.EntityGuardPort;
import art.arcane.adapt.engine.world.EntityMarkerPort;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import art.arcane.adapt.engine.integration.ReactSuperStackPort;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.data.StackExclusion;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@link IntegrationRuntime} over the ports a platform installs, read on each call so a late install is seen. */
final class IntegrationBridge implements IntegrationRuntime {
  @Override
  public boolean isServerPlayer(PlayerRef player) {
    return AdaptPorts.get(EntityGuardPort.class, EntityGuardPort.NONE).isServerPlayer(player);
  }

  @Override
  public boolean isShielded(EntityRef entity) {
    return AdaptPorts.get(EntityGuardPort.class, EntityGuardPort.NONE).isShielded(entity);
  }

  @Override
  public boolean marked(EntityRef entity, String marker) {
    return AdaptPorts.get(EntityMarkerPort.class, EntityMarkerPort.NONE).marked(entity, marker);
  }

  @Override
  public @Nullable PlayerInput input(PlayerRef player) {
    return AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(player);
  }

  @Override
  public @Nullable Loc announceTeleport(PlayerRef player, Adaptation<?> source, Loc from, Loc to) {
    return AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE).announce(player, source, from, to);
  }

  @Override
  public List<HiddenVein> hiddenVeins(Loc origin, int radius) {
    List<HiddenVein> veins = new ArrayList<>();
    for (HiddenOrePort.VeinTarget target : AdaptPorts.get(HiddenOrePort.class, HiddenOrePort.NONE).veins(origin,
        Math.max(0, radius))) {
      veins.add(new HiddenVein(target.location(), target.display()));
    }
    return List.copyOf(veins);
  }

  @Override
  public List<BlockRef> hiddenVeinSiblings(BlockRef block) {
    return List.copyOf(AdaptPorts.get(HiddenOrePort.class, HiddenOrePort.NONE).veinSiblings(block));
  }

  @Override
  public boolean treeFellingAvailable() {
    return iris().isAvailable();
  }

  @Override
  public boolean isTreeBlock(BlockRef block) {
    return iris().isTreeBlock(block);
  }

  @Override
  public boolean isFellingBreak(BlockBreakEvent event) {
    return iris().isManagedBreak(event);
  }

  @Override
  public boolean fellTree(BlockBreakEvent event, int durabilityPreservationChance, TreeFelling felling) {
    return iris().tryFell(event, durabilityPreservationChance, new IrisTreeFellerPort.RunHooks() {
      @Override
      public boolean requiresSneaking() {
        return felling.requiresSneaking();
      }

      @Override
      public void onActivationAccepted() {
        felling.onActivationAccepted();
      }

      @Override
      public boolean reserveLogCost() {
        return felling.reserveLogCost();
      }

      @Override
      public void commitLogCost() {
        felling.commitLogCost();
      }

      @Override
      public void refundLogCost() {
        felling.refundLogCost();
      }
    });
  }

  @Override
  public PluginContainer pluginContainer(Loc block) {
    return switch (advancedChests().find(block)) {
      case ABSENT -> PluginContainer.NONE;
      case PRESENT -> PluginContainer.PRESENT;
      case FAILED -> PluginContainer.FAILED;
    };
  }

  @Override
  public @Nullable InventoryRef openPluginContainer(PlayerRef player, Loc block) {
    return advancedChests().open(player, block);
  }

  @Override
  public void excludeFromStacking(EntityRef entity) {
    StackExclusion.exclude(entity);
  }

  @Override
  public void includeInStacking(EntityRef entity) {
    StackExclusion.include(entity);
  }

  @Override
  public boolean isSuperStack(ItemRef stack) {
    return superStacks().isSuperStack(stack);
  }

  @Override
  public List<ItemRef> superStackItems(ItemRef stack) {
    return superStacks().items(stack);
  }

  @Override
  public @Nullable ItemRef withSuperStackItems(ItemRef stack, List<ItemRef> items) {
    return superStacks().withItems(stack, items).orElse(null);
  }

  @Override
  public boolean glossAvailable() {
    return gloss().available();
  }

  @Override
  public boolean glossOverlayActive(LivingRef entity) {
    return gloss().overlayActive(entity);
  }

  @Override
  public boolean glossShow(PlayerRef viewer, LivingRef target, List<String> lines, long durationMillis) {
    return gloss().update(viewer, target, lines, durationMillis);
  }

  @Override
  public void glossClear(UUID viewer) {
    gloss().clear(viewer);
  }

  @Override
  public void glossRestrict(boolean restricted) {
    gloss().setRestricted(restricted);
  }

  @Override
  public void glossRefresh() {
    gloss().refresh();
  }

  private static ReactSuperStackPort superStacks() {
    return AdaptPorts.get(ReactSuperStackPort.class, ReactSuperStackPort.NONE);
  }

  private static GlossInsightPort gloss() {
    return AdaptPorts.get(GlossInsightPort.class, GlossInsightPort.NONE);
  }

  private static AdvancedChestsPort advancedChests() {
    return AdaptPorts.get(AdvancedChestsPort.class, AdvancedChestsPort.NONE);
  }

  private static IrisTreeFellerPort iris() {
    return AdaptPorts.get(IrisTreeFellerPort.class, IrisTreeFellerPort.NONE);
  }
}
