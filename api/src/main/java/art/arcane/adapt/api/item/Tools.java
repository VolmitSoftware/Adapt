package art.arcane.adapt.api.item;

import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * What kind of tool an item stack is, and which tool work breaking a block counts as, the same on every platform.
 * Pickaxes, Axes and Excavation, their adaptations and {@link ItemKinds} all ask here.
 *
 * <p>An item is a kind when the first of these rules answers: the operator's excluded list for the kind, a stack used up
 * to its maximum damage, the operator's extra list
 * ({@link #rules(ToolKind, ToolRules)}), the vanilla and mod loader tool tags ({@link #automaticTags}; a damageable
 * modded tool they name that no longer mines the kind's reference block, such as a broken Tinkers' Construct tool,
 * counts as broken), the loader's digging action ({@code ToolActions}, {@code ItemAbilities}), being the correct tool for the kind's
 * reference block (stone, an oak log, dirt), and last the vanilla tools. A multi-tool can be several kinds; which one a
 * block break feeds comes from the block, see {@link #serves}.
 *
 * <p>Without a running core only the vanilla rule answers.
 */
@ApiStatus.Experimental
public final class Tools {
  private static final Object LOCK = new Object();
  private static volatile @Nullable Bound current;

  private Tools() {
  }

  /** Sets the operator lists and rule switches for {@code kind}; the skill that owns the kind calls it on load. */
  public static void rules(ToolKind kind, ToolRules rules) {
    ToolResolver active = resolver();
    if (active != null) {
      active.rules(kind, rules);
    }
  }

  /** The rules in force for {@code kind}: {@link ToolRules#AUTOMATIC} until a skill sets its own. */
  public static ToolRules rules(ToolKind kind) {
    ToolResolver active = resolver();
    return active == null ? ToolRules.AUTOMATIC : active.rules(kind);
  }

  /** Which rule makes the stack {@code kind}, or {@link ToolMatch#NONE} or {@link ToolMatch#EXCLUDED}. */
  public static ToolMatch match(@Nullable ItemRef item, ToolKind kind) {
    if (item == null || item.isEmpty()) {
      return ToolMatch.NONE;
    }
    ToolResolver active = resolver();
    if (active == null) {
      if (ToolResolver.usedUp(item)) {
        return ToolMatch.BROKEN;
      }
      return ToolResolver.vanilla(item.type(), kind) ? ToolMatch.VANILLA : ToolMatch.NONE;
    }
    return active.match(item, kind);
  }

  /** True when the stack counts as {@code kind}. */
  public static boolean is(@Nullable ItemRef item, ToolKind kind) {
    return match(item, kind).matches();
  }

  /** Every kind the stack counts as; a paxel is a pickaxe, an axe and a shovel. */
  public static Set<ToolKind> kindsOf(@Nullable ItemRef item) {
    ToolResolver active = resolver();
    if (active != null && item != null) {
      return active.kinds(item);
    }
    Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
    for (ToolKind kind : ToolKind.values()) {
      if (is(item, kind)) {
        out.add(kind);
      }
    }
    return Set.copyOf(out);
  }

  /**
   * True when breaking {@code block} with {@code item} is {@code kind}'s work. The block must not be excluded for the
   * kind. A stack that is the kind serves when the block is in the kind's mineable tag, a loader ore or stone tag for
   * pickaxes, or the operator's extra blocks; a stack that is only one of pickaxe, axe and shovel also serves on any
   * other block, as vanilla tools always have. A stack that is not the kind serves only on such a claimed block it is
   * the correct tool for.
   */
  public static boolean serves(@Nullable ItemRef item, @Nullable BlockStateRef block, ToolKind kind) {
    if (item == null || block == null) {
      return false;
    }
    ToolResolver active = resolver();
    if (active == null) {
      return is(item, kind);
    }
    return active.serves(item, block, kind);
  }

  /** True when {@code block} is {@code kind}'s work: its mineable or loader tags or the operator's extra blocks. */
  public static boolean claims(ToolKind kind, Id block) {
    ToolResolver active = resolver();
    return active != null && active.claims(kind, block);
  }

  /** True when the stack is the correct tool for the block, or a kind whose work the block is. */
  public static boolean isEffective(@Nullable ItemRef item, @Nullable BlockStateRef block) {
    ToolResolver active = resolver();
    if (active == null) {
      return item != null && block != null && block.isCorrectTool(item);
    }
    return active.isEffective(item, block);
  }

  /** True when the stack's item type is in the item tag. */
  public static boolean isTagged(@Nullable ItemRef item, Id tag) {
    ToolResolver active = resolver();
    return active != null && active.isTagged(item, tag);
  }

  /** True when the block type is in the block tag. */
  public static boolean isBlockTagged(@Nullable Id block, Id tag) {
    ToolResolver active = resolver();
    return active != null && block != null && active.isBlockTagged(block, tag);
  }

  /** The vanilla and mod loader item tags the tag rule reads for {@code kind}. */
  public static Set<Id> automaticTags(ToolKind kind) {
    return ToolResolver.toolTags(kind);
  }

  /** The block tags that make a block {@code kind}'s work, before the operator's lists. */
  public static Set<Id> workTags(ToolKind kind) {
    return ToolResolver.blockTags(kind);
  }

  /** Forgets cached tag answers; call it when resources reload. */
  public static void invalidate() {
    ToolResolver active = resolver();
    if (active != null) {
      active.invalidate();
    }
  }

  private static @Nullable ToolResolver resolver() {
    EngineBridge bridge;
    try {
      bridge = Engine.isRunning() ? Engine.bridge() : null;
    } catch (IllegalStateException stopped) {
      bridge = null;
    }
    if (bridge == null) {
      return null;
    }
    Bound bound = current;
    if (bound != null && bound.bridge() == bridge) {
      return bound.resolver();
    }
    synchronized (LOCK) {
      bound = current;
      if (bound == null || bound.bridge() != bridge) {
        bound = new Bound(bridge, new ToolResolver(new HostPort(bridge)));
        current = bound;
      }
      return bound.resolver();
    }
  }

  private record Bound(EngineBridge bridge, ToolResolver resolver) {
  }

  private record HostPort(EngineBridge bridge) implements ToolResolver.Port {
    @Override
    public RegistryLookup registries() {
      return host().registries();
    }

    @Override
    public Set<ToolKind> toolActions(ItemRef item) {
      return host().items().toolActions(item);
    }

    @Override
    public @Nullable BlockStateRef state(Id block) {
      return host().items().blockState(block);
    }

    private PlatformHost host() {
      return bridge.platform().host();
    }
  }
}
