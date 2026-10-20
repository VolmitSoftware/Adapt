package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class GolemWorkMessages {
  public static final TextKey WAITING = TextKey.of("golemancy.work.waiting", "Waiting for work");
  public static final TextKey MOVING = TextKey.of("golemancy.work.moving", "Moving to work");
  public static final TextKey WORKING = TextKey.of("golemancy.work.working", "Working");
  public static final TextKey DELIVERING = TextKey.of("golemancy.work.delivering", "Delivering cargo");
  public static final TextKey FETCHING_TOOL = TextKey.of("golemancy.work.fetching_tool", "Fetching a tool");
  public static final TextKey NEEDS_TOOL = TextKey.of("golemancy.work.needs_tool", "Needs a suitable tool");
  public static final TextKey NO_OUTPUT = TextKey.of("golemancy.work.no_output", "Link a chest or free output space");
  public static final TextKey STUCK_PAUSE = TextKey.of("golemancy.work.stuck_pause", "Paused after getting stuck");
  public static final TextKey CORE_UNLEARNED = TextKey.of("golemancy.work.core_unlearned", "Learn this golem core to enable work");
  public static final TextKey NO_REACHABLE_WORK = TextKey.of("golemancy.work.no_reachable_work", "Waiting for reachable work");
  public static final TextKey NEEDS_SEEDS = TextKey.of("golemancy.work.needs_seeds", "Add allowed seeds to the home chest");
  public static final TextKey NODE_WAITING = TextKey.of("golemancy.node_status.waiting", "Waiting for a worker");
  public static final TextKey NODE_PAUSED = TextKey.of("golemancy.node_status.paused", "Paused by redstone");

  private GolemWorkMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(WAITING);
    builder.add(MOVING);
    builder.add(WORKING);
    builder.add(DELIVERING);
    builder.add(FETCHING_TOOL);
    builder.add(NEEDS_TOOL);
    builder.add(NO_OUTPUT);
    builder.add(STUCK_PAUSE);
    builder.add(CORE_UNLEARNED);
    builder.add(NO_REACHABLE_WORK);
    builder.add(NEEDS_SEEDS);
    builder.add(NODE_WAITING);
    builder.add(NODE_PAUSED);
  }
}
