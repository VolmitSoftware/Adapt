package art.arcane.adapt.engine.mutation.tool;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.VanillaUse;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.engine.item.AdaptItemGuard;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.CrafterCraftEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.format.C;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Runs a mutation tool used in the main hand by a holder at the tool's required level; anyone else gets its vanilla
 * use denied and is told, at most once a second, what it needs. Every other vanilla way to place or spend a tool is
 * refused by {@link AdaptItemGuard}. Crafters never make tools, since nobody's level gates them.
 */
public final class MutationToolGuard {
  private static final long REFUSAL_GAP_MILLIS = 1000L;
  private final Map<UUID, Long> refused = new ConcurrentHashMap<>();

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    ItemRef item = e.item();
    if (e.protectionProbe() || !MutationTools.isTool(item)) {
      return;
    }
    PlayerRef player = e.player();
    MutationTool<?> tool = MutationTools.of(item);
    int level = tool == null || e.hand() == Hand.OFF_HAND ? 0 : tool.level(player);
    if (tool == null || level <= 0 || !tool.item().allows(item, VanillaUse.USE)) {
      e.setUseItem(UseResult.DENY);
    }
    if (tool == null || e.hand() == Hand.OFF_HAND) {
      return;
    }
    if (level <= 0) {
      if (e.action() == InteractAction.RIGHT_CLICK_AIR || e.action() == InteractAction.RIGHT_CLICK_BLOCK) {
        refuse(player, tool);
      }
      return;
    }
    tool.use(new ToolUse(player, item, level, e.action(), e.clickedBlock(), e.clickedFace(), null));
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    ItemRef item = player.inventory().hand(e.hand() == null ? Hand.MAIN_HAND : e.hand());
    if (e.protectionProbe() || !MutationTools.isTool(item)) {
      return;
    }
    MutationTool<?> tool = MutationTools.of(item);
    int level = tool == null || e.hand() == Hand.OFF_HAND ? 0 : tool.level(player);
    if (tool == null || level <= 0 || !tool.item().allows(item, VanillaUse.USE)) {
      e.setCancelled(true);
    }
    if (tool == null || e.hand() == Hand.OFF_HAND) {
      return;
    }
    if (level <= 0) {
      refuse(player, tool);
      return;
    }
    tool.use(new ToolUse(player, item, level, InteractAction.RIGHT_CLICK_AIR, null, null, e.clicked()));
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(CrafterCraftEvent e) {
    if (MutationTools.isToolRecipe(e.recipeKey())) {
      e.setCancelled(true);
    }
  }

  private void refuse(PlayerRef player, MutationTool<?> tool) {
    long now = System.currentTimeMillis();
    Long last = refused.get(player.uuid());
    if (last != null && now - last < REFUSAL_GAP_MILLIS) {
      return;
    }
    refused.put(player.uuid(), now);
    Adaptation<?> owner = tool.owner();
    Skill<?> skill = owner.getSkill();
    AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(RuntimeMessages.REQUIRES_ADAPTATION,
        trusted("adaptation", owner.getDisplayName()),
        trusted("skill", skill == null ? AdaptLanguage.text(RuntimeMessages.UNKNOWN_SKILL) : skill.getDisplayName())));
  }
}
