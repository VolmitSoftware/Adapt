package com.example.geology;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonInfo;
import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.command.ArgumentTypes;
import art.arcane.adapt.api.command.CommandArgument;
import art.arcane.adapt.api.command.CommandInvocation;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.event.AdaptationLearnEvent;
import art.arcane.adapt.api.event.SkillXpEvent;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Management through the public API as {@code /adapt geology <action>}, the same command on every platform: an
 * operator gets one {@code GEOLOGY <action> ...} line back. The event handlers count what the status reports.
 */
public final class GeologyConsole {
  static final String PERMISSION = "example.geology.admin";

  private static final CommandArgument<Double> AMOUNT =
      CommandArgument.required("amount", ArgumentTypes.decimal(0D, 1_000_000D), Text.key(key("argument.amount")));
  private static final CommandArgument<Integer> KNOWLEDGE =
      CommandArgument.required("amount", ArgumentTypes.integer(0, 1_000_000), Text.key(key("argument.amount")));
  private static final CommandArgument<Id> ADAPTATION =
      CommandArgument.required("adaptation", ArgumentTypes.adaptation(), Text.key(key("argument.adaptation")));
  private static final CommandArgument<Integer> LEVEL =
      CommandArgument.required("level", ArgumentTypes.integer(1, 100), Text.key(key("argument.level")));
  private static final CommandArgument<Boolean> FORCE =
      CommandArgument.optional("force", ArgumentTypes.bool(), Text.key(key("argument.force")));
  private static final CommandArgument<CoreSample.Delivery> DELIVERY =
      CommandArgument.required("delivery", ArgumentTypes.enumeration(CoreSample.Delivery.class), Text.key(key("argument.delivery")));
  private static final CommandArgument<Id> PARTNER =
      CommandArgument.required("partner", ArgumentTypes.skill(), Text.key(key("argument.partner")));
  private static final CommandArgument<Id> SKILL =
      CommandArgument.required("skill", ArgumentTypes.skill(), Text.key(key("argument.skill")));
  private static final CommandArgument<String> LOCALE =
      CommandArgument.optional("locale", ArgumentTypes.choice("en_US", "de_DE"), Text.key(key("argument.locale")));

  private final AtomicInteger xpEvents = new AtomicInteger();
  private final AtomicReference<String> lastXp = new AtomicReference<>("none");
  private final AtomicInteger learnEvents = new AtomicInteger();

  @Handle(priority = EventPriority.MONITOR)
  public void on(SkillXpEvent event) {
    if (event.skill().equals(GeologyAddon.GEOLOGY)) {
      xpEvents.incrementAndGet();
      lastXp.set(event.source() + "=" + event.amount());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(AdaptationLearnEvent event) {
    if (event.adaptation().namespace().equals(GeologyAddon.NAMESPACE)) {
      learnEvents.incrementAndGet();
    }
  }

  private final Supplier<Optional<AddonHandle>> handle;
  private final Supplier<Optional<PlayerSection<FieldNotes>>> notes;

  /** A console whose {@code uninstall} subcommand uses the addon's own {@code handle} and {@code notes} section. */
  public GeologyConsole(Supplier<Optional<AddonHandle>> handle, Supplier<Optional<PlayerSection<FieldNotes>>> notes) {
    this.handle = handle;
    this.notes = notes;
  }

  /** {@code /adapt geology}: one subcommand per action, all for operators. */
  public AdaptCommand command() {
    return AdaptCommand.named("geology")
        .help(Text.key(key("geology")))
        .permission(PERMISSION)
        .then(action("status", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            status(player, profile, profile.skill(GeologyAddon.GEOLOGY))).build())
        .then(action("catalog", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> catalog()).build())
        .then(action("text", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            text(invocation.find(LOCALE).orElse(player.locale()))).argument(LOCALE).build())
        .then(action("xp", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> {
          SkillProgress progress = profile.skill(GeologyAddon.GEOLOGY);
          progress.award(XpAward.of(invocation.get(AMOUNT)).from("console"));
          return "xp=" + progress.xp() + " level=" + progress.level();
        }).argument(AMOUNT).build())
        .then(action("knowledge", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> {
          SkillProgress progress = profile.skill(GeologyAddon.GEOLOGY);
          progress.addKnowledge(invocation.get(KNOWLEDGE));
          return "knowledge=" + progress.knowledge();
        }).argument(KNOWLEDGE).build())
        .then(action("learn", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> {
          LearnOptions options = invocation.find(FORCE).orElse(false) ? LearnOptions.forced() : LearnOptions.paying();
          LearnResult result = profile.learn(invocation.get(ADAPTATION), invocation.get(LEVEL), options);
          return "status=" + result.status() + " from=" + result.fromLevel() + " to=" + result.toLevel()
              + " knowledge=" + result.knowledge();
        }).argument(ADAPTATION).argument(LEVEL).argument(FORCE).build())
        .then(action("unlearn", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            "status=" + profile.unlearn(invocation.get(ADAPTATION), 0, LearnOptions.forced()).status())
            .argument(ADAPTATION).build())
        .then(action("delivery", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            "set=" + profile.preferences().set(CoreSample.ID, CoreSample.DELIVERY, invocation.get(DELIVERY)) + " now="
                + profile.preferences().get(CoreSample.ID, CoreSample.DELIVERY)).argument(DELIVERY).build())
        .then(action("mutate", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> {
          MutationResult result = profile.mutate(GeologyAddon.GEOLOGY, invocation.get(PARTNER));
          return "status=" + result.status() + " pair=" + result.pair().map(Object::toString).orElse("none");
        }).argument(PARTNER).build())
        .then(action("lead", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> {
          MutationResult result = profile.mutate(invocation.get(PARTNER), GeologyAddon.GEOLOGY);
          return "status=" + result.status() + " pair=" + result.pair().map(Object::toString).orElse("none");
        }).argument(PARTNER).build())
        .then(action("train", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> {
          SkillProgress progress = profile.skill(invocation.get(SKILL));
          progress.award(XpAward.of(invocation.get(AMOUNT)).from("console"));
          return "xp=" + progress.xp() + " level=" + progress.level();
        }).argument(SKILL).argument(AMOUNT).build())
        .then(action("mutation", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            mutation(profile)).build())
        .then(action("unmutate", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            "status=" + profile.unmutate(GeologyAddon.GEOLOGY).status()).build())
        .then(action("notes", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) -> notes.get()
            .map((PlayerSection<FieldNotes> section) -> {
              FieldNotes written = profile.section(section);
              written.entries++;
              profile.requestSave();
              return "entries=" + written.entries + " section=" + section.id();
            }).orElse("section=missing")).build())
        .then(action("orphans", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            "orphans=" + profile.orphans()).build())
        .then(action("addon", (PlayerRef player, PlayerProfile profile, CommandInvocation invocation) ->
            AdaptApi.addons().find(GeologyAddon.NAMESPACE).map(GeologyConsole::handle).orElse("missing")).build())
        .then(AdaptCommand.named("uninstall")
            .help(Text.key(key("uninstall")))
            .executes((CommandInvocation invocation) -> handle.get().ifPresent(AddonHandle::uninstall))
            .build())
        .build();
  }

  /** A player-only subcommand that answers {@code GEOLOGY <name> <reply>} on the player's own thread. */
  private static AdaptCommand.Builder action(String name, Reply reply) {
    return AdaptCommand.named(name)
        .help(Text.key(key(name)))
        .playerOnly()
        .executes((CommandInvocation invocation) -> {
          PlayerRef player = invocation.caller().player().orElseThrow();
          AdaptApi.scheduler().runEntity(player, () -> invocation.caller().send(Text.literal("GEOLOGY " + name + " "
              + reply.answer(player, AdaptApi.players().require(player), invocation))));
        });
  }

  private static String key(String path) {
    return GeologyAddon.NAMESPACE + ".command." + path;
  }

  @FunctionalInterface
  private interface Reply {
    String answer(PlayerRef player, PlayerProfile profile, CommandInvocation invocation);
  }

  private String status(PlayerRef player, PlayerProfile profile, SkillProgress progress) {
    StringJoiner levels = new StringJoiner(",");
    for (Map.Entry<Id, Integer> entry : progress.adaptations().entrySet()) {
      levels.add(entry.getKey() + "=" + entry.getValue());
    }
    double armor = AdaptAttributeService.get().value(player, Attributes.ARMOR);
    return "available=" + progress.isAvailable() + " level=" + progress.level() + " xp=" + progress.xp()
        + " knowledge=" + progress.knowledge() + " adaptations=[" + levels + "] aftershock="
        + profile.level(Aftershock.ID) + " armor=" + armor + " rocks=" + profile.stat(GeologySkill.ROCKS_STAT)
        + " samples=" + profile.stat(CoreSample.SAMPLES_STAT) + " prospects=" + profile.stat(Prospect.USES_STAT)
        + " delivery="
        + profile.preferences().get(CoreSample.ID, CoreSample.DELIVERY) + " xpEvents=" + xpEvents.get() + " lastXp="
        + lastXp.get() + " learnEvents=" + learnEvents.get() + " mutation="
        + profile.mutationOf(GeologyAddon.GEOLOGY).map(Object::toString).orElse("none") + " permission="
        + player.hasPermission("adapt.use.example.geology.prospect") + " locale=" + player.locale();
  }

  private String mutation(PlayerProfile profile) {
    Optional<SkillPair> pair = profile.mutationOf(GeologyAddon.GEOLOGY);
    if (pair.isEmpty()) {
      return "pair=none";
    }
    SkillProgress line = profile.skill(pair.get().lead());
    StringJoiner levels = new StringJoiner(",");
    for (Map.Entry<Id, Integer> entry : line.adaptations().entrySet()) {
      levels.add(entry.getKey() + "=" + entry.getValue());
    }
    return "pair=" + pair.get() + " id=" + pair.get().id() + " level=" + line.level() + " xp=" + line.xp()
        + " knowledge=" + line.knowledge() + " adaptations=[" + levels + "]";
  }

  private String catalog() {
    ContentCatalog catalog = AdaptApi.content();
    List<String> skills = new ArrayList<>();
    for (Skill<?> skill : catalog.skills()) {
      skills.add(skill.getId().toString());
    }
    List<String> geology = new ArrayList<>();
    catalog.skill(GeologyAddon.GEOLOGY).ifPresent((Skill<?> skill) -> {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        geology.add(adaptation.getId().path());
      }
    });
    boolean onPickaxe = catalog.skill(GeologyAddon.PICKAXE)
        .map((Skill<?> skill) -> skill.getAdaptations().stream().anyMatch((Adaptation<?> adaptation) -> adaptation.getId().equals(Aftershock.ID)))
        .orElse(false);
    Path data = AdaptApi.platform().host().dataDirectory();
    return "revision=" + catalog.revision() + " skills=" + skills.size() + " listed=" + skills + " geology=" + geology
        + " owner=" + catalog.owner(GeologyAddon.GEOLOGY).orElse("none")
        + " aftershockOwner=" + catalog.owner(Aftershock.ID).orElse("none")
        + " pickaxeInCatalog=" + catalog.skill(GeologyAddon.PICKAXE).isPresent() + " aftershockOnPickaxe=" + onPickaxe
        + " adaptationsTotal=" + catalog.adaptations().size()
        + " skillConfig=" + Files.isRegularFile(data.resolve("skills/example/geology.toml"))
        + " prospectConfig=" + Files.isRegularFile(data.resolve("adaptations/example/geology/prospect.toml"))
        + " aftershockConfig=" + Files.isRegularFile(data.resolve("adaptations/example/pickaxe/aftershock.toml"))
        + " assets=" + AssetBundle.resources(GeologyAddon.class, "assets/example").entries().size()
        + " locales=" + MessageBundle.resources(GeologyAddon.class, "example/lang").locales();
  }

  private static String text(String locale) {
    return "locale=" + locale + " skill=" + Text.key(TextKeys.skill(GeologyAddon.GEOLOGY) + ".name").resolve(locale)
        + " | prospect=" + Text.key(TextKeys.adaptation(Prospect.ID) + ".name").resolve(locale)
        + " | aftershock=" + Text.key(TextKeys.adaptation(Aftershock.ID) + ".name").resolve(locale)
        + " | lens=" + Text.key(TextKeys.item(GeodeLens.ITEM) + ".name").resolve(locale)
        + " | found=" + Text.key(TextKeys.adaptation(Prospect.ID) + ".hud.found").with("count", 3).resolve(locale);
  }

  private static String handle(AddonInfo handle) {
    return "state=" + handle.state() + " rejection=" + handle.rejection().orElse("none");
  }
}
