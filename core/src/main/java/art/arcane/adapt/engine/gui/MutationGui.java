package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.mutation.sound.SoundStage;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.client.server.AdaptScreens;
import art.arcane.adapt.command.PlayerCommandSource;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.api.AdaptApi;

import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationSpreadViews;
import art.arcane.adapt.util.common.inventorygui.GuiFrame;
import art.arcane.adapt.util.common.misc.MenuSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/**
 * The Mutate flow as ordinary menus: a skill to lead (from {@code /adapt mutate}), then a menu of partners, then a
 * confirm menu with a lead swap, one list of what is lost, one of what is gained, and a two-step confirm. Players with
 * the client mod get the same menus as native panels unless their client draws its own spread.
 */
public final class MutationGui {
  private static final int COLUMNS = 7;
  private static final Id MUTATE = Id.minecraft("nether_star");
  private static final Id ARMED = Id.minecraft("red_stained_glass_pane");
  private static final Id LOSE = Id.minecraft("barrier");
  private static final Id GAIN = Id.minecraft("emerald");
  private static final Id SWAP = Id.minecraft("compass");
  private static final Id KEPT = Id.minecraft("chest");
  private static final Id TOOLS = Id.minecraft("iron_pickaxe");
  private static final Id UNMUTATE = Id.minecraft("grindstone");
  /** Lets a player undo anyone's mutations by command, and their own from the skill page without debug mode. */
  public static final String UNMUTATE_PERMISSION = "adapt.mutate.reset";

  private MutationGui() {
  }

  /** The Mutate button for a skill page, or null when the page does not offer it to this player. */
  public static GuiElement entry(Skill<?> skill, PlayerRef player, PlayerData data) {
    SkillRegistry registry = registry();
    if (registry == null || !MutationOffers.offered(data, skill, registry.getSkills(), config())) {
      return null;
    }
    return mutateButton("mutate").onLeftClick((MenuClick e) -> AdaptScreens.openMutation(player, skill));
  }

  /** The Mutate button for the skills menu, opening the skills that can lead; null while none can. */
  public static GuiElement leadsEntry(PlayerRef player, PlayerData data) {
    SkillRegistry registry = registry();
    if (registry == null || MutationOffers.leads(data, registry.getSkills(), config()).isEmpty()) {
      return null;
    }
    return mutateButton("mutate-leads").onLeftClick((MenuClick e) -> openLeads(player));
  }

  private static GuiElement mutateButton(String id) {
    return GuiTheme.element(id, MUTATE, "gui", "mutation", "mutate")
        .setName(TextFormat.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.MUTATE))
        .setRole(MenuRole.ACTION);
  }

  /** Skills the player can mutate now (the pack lead picker for pack viewers); picking one opens its Mutate flow. */
  public static void openLeads(PlayerRef player) {
    openLeads(player, 0);
  }

  private static void openLeads(PlayerRef player, int page) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null || AdaptScreens.openMutationLeads(player)) {
      return;
    }
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> openLeads(player, page));
      return;
    }
    List<Skill<?>> leads = MutationOffers.leads(adaptPlayer.getData(), registry.getSkills(), config());
    GuiElement heading = GuiTheme.element("mutation-heading", MUTATE, "gui", "mutation", "heading")
        .setName(TextFormat.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.PICK_LEAD))
        .setRole(MenuRole.HEADER);
    skillMenu(player, "mutation/pick", heading, adaptPlayer.getData(), leads, page,
        (Skill<?> lead) -> AdaptScreens.openMutation(player, lead), () -> AdaptScreens.openSkills(player),
        (int target) -> openLeads(player, target));
  }

  /** The partners that can join {@code lead}; picking one opens the confirm step. */
  public static void openPartners(PlayerRef player, Skill<?> lead) {
    openPartners(player, lead, 0);
  }

  private static void openPartners(PlayerRef player, Skill<?> lead, int page) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null) {
      return;
    }
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> openPartners(player, lead, page));
      return;
    }
    PlayerData data = adaptPlayer.getData();
    List<Skill<?>> partners = MutationOffers.partners(data, lead, registry.getSkills(), config());
    GuiElement heading = skillTile("mutation-lead", lead, data)
        .setName(TextFormat.WHITE + AdaptLanguage.text(MutationMessages.LEAD, trusted("skill", lead.getLocalizedName())))
        .setRole(MenuRole.HEADER);
    skillMenu(player, "mutation/partners", heading, data, partners, page,
        (Skill<?> partner) -> open(player, lead, partner, false), () -> ComponentHost.of(lead).openGui(player),
        (int target) -> openPartners(player, lead, target));
  }

  /**
   * The confirm step: the result in the header; the lead, the swap and the partner; one item listing everything lost
   * and one listing everything gained; Mutate in the footer, which arms on the first click and mutates on the
   * second. A partner the rules refuse, or none, shows the partners instead.
   */
  public static void open(PlayerRef player, Skill<?> lead, Skill<?> partner, boolean armed) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null) {
      return;
    }
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> open(player, lead, partner, armed));
      return;
    }
    PlayerData data = adaptPlayer.getData();
    if (partner == null || SkillMutations.check(data, lead, partner, config()) != SkillMutations.Result.MUTATED) {
      openPartners(player, lead, 0);
      return;
    }
    MutationOffers.Preview preview = MutationOffers.preview(data, lead, partner);
    MutationSpreadView spread = MutationSpreadViews.build(registry, data, lead, partner, config(), "");
    MenuSounds.play(player, armed ? MenuSounds.Action.ARM : MenuSounds.Action.OPEN);
    GuiWindow window = new GuiWindow(player);
    GuiFrame.apply(window, "mutation/preview", 1);
    window.setLayout(MenuLayout.GRID);
    CustomModel leadModel = GuiConfig.skillModel(SkillHost.lineKey(lead), lead.getIcon().item(), ComponentHost.of(lead).getModel());
    GuiFrame.subject(window, new GuiElement("mutation-result")
        .setMaterial(leadModel.material())
        .setModel(leadModel)
        .setEnchanted(true)
        .setName(TextFormat.RESET + "" + TextFormat.BOLD + SkillHost.legacyColor(lead.getColor()) + preview.name())
        .addLore(TextFormat.WHITE + AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", preview.level())))
        .addLore(TextFormat.AQUA + AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", preview.knowledge())))
        .setRole(MenuRole.HEADER));
    GuiFrame.body(window, 0, 1, skillTile("mutation-lead", lead, data)
        .addLore(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.LEAD_ROLE))
        .setRole(MenuRole.INFO));
    GuiFrame.body(window, 0, 2, GuiTheme.element("mutation-swap", SWAP, "gui", "mutation", "swap")
        .setName(TextFormat.WHITE + AdaptLanguage.text(MutationMessages.SWAP_LEAD))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> open(player, partner, lead, false)));
    GuiFrame.body(window, 0, 3, skillTile("mutation-partner", partner, data)
        .addLore(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.PARTNER_ROLE))
        .setRole(MenuRole.INFO));
    GuiFrame.body(window, 0, 5, summaryItem("mutation-lose", LOSE, TextFormat.RED + AdaptLanguage.text(MutationMessages.YOU_LOSE),
        MutationSummary.losses(spread, registry), registry)
        .addLore(" ")
        .addLore(TextFormat.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
        .onLeftClick((MenuClick e) -> openDetails(player, lead, partner, false)));
    GuiFrame.body(window, 0, 7, summaryItem("mutation-gain", GAIN, TextFormat.GREEN + AdaptLanguage.text(MutationMessages.YOU_GAIN),
        MutationSummary.gains(spread, registry), registry)
        .onLeftClick((MenuClick e) -> openDetails(player, lead, partner, true)));
    GuiFrame.back(window, () -> openPartners(player, lead, 0));
    GuiFrame.footer(window, GuiFrame.Slot.ACTION, armed
        ? GuiTheme.element("mutation-confirm", ARMED, "gui", "mutation", "armed")
            .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.CONFIRM_AGAIN))
            .addLore(TextFormat.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
            .setHint(MenuHint.destructive(AdaptLanguage.text(MutationMessages.CONFIRM_AGAIN)))
            .setRole(MenuRole.ACTION)
            .onLeftClick((MenuClick e) -> {
              window.close();
              complete(player, lead, partner);
            })
        : mutateButton("mutation-confirm")
            .addLore(TextFormat.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
            .onLeftClick((MenuClick e) -> open(player, lead, partner, true)));
    window.setTitle(preview.name());
    window.open();
  }

  /** Everything lost, or everything gained, as a list: one row per heading with the heading's icon in column 0. */
  static void openDetails(PlayerRef player, Skill<?> lead, Skill<?> partner, boolean gains) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null) {
      return;
    }
    MutationSpreadView spread = MutationSpreadViews.build(registry, adaptPlayer.getData(), lead, partner, config(), "");
    List<MutationSummary.Section> sections = gains ? MutationSummary.gains(spread, registry) : MutationSummary.losses(spread, registry);
    List<MutationSummary.Section> listed = new ArrayList<>(sections.size());
    List<List<MutationEntry>> groups = new ArrayList<>(sections.size());
    for (MutationSummary.Section section : sections) {
      if (!section.entries().isEmpty()) {
        listed.add(section);
        groups.add(section.entries());
      }
    }
    GuiFrame.Layout<MutationEntry> layout = GuiFrame.groups(groups, true);
    MenuSounds.play(player, MenuSounds.Action.OPEN);
    String title = gains ? TextFormat.GREEN + AdaptLanguage.text(MutationMessages.YOU_GAIN)
        : TextFormat.RED + AdaptLanguage.text(MutationMessages.YOU_LOSE);
    detailsPage(player, lead, partner, gains, title, sections, listed, layout, registry, 0);
  }

  private static void detailsPage(PlayerRef player, Skill<?> lead, Skill<?> partner, boolean gains, String title,
                                  List<MutationSummary.Section> sections, List<MutationSummary.Section> listed,
                                  GuiFrame.Layout<MutationEntry> layout,
                                  SkillRegistry registry, int page) {
    int currentPage = layout.clampedPage(page);
    GuiWindow window = new GuiWindow(player);
    GuiFrame.apply(window, "mutation/details", layout.bodyRows());
    window.setLayout(MenuLayout.GRID);
    GuiFrame.subject(window, summaryItem(gains ? "mutation-gain" : "mutation-lose", gains ? GAIN : LOSE, title, sections, registry).setRole(MenuRole.HEADER));
    GuiFrame.body(window, layout.page(currentPage), (GuiFrame.Cell<MutationEntry> cell) -> cell.heading()
        ? sectionHeading(listed.get(cell.group()), registry)
        : entryElement(cell.item()));
    GuiFrame.back(window, () -> open(player, lead, partner, false));
    int total = 0;
    for (MutationSummary.Section section : listed) {
      total += section.entries().size();
    }
    GuiFrame.pager(window, currentPage, layout.pageCount(), total,
        (int target) -> detailsPage(player, lead, partner, gains, title, sections, listed, layout, registry, target));
    window.setTitle(title);
    window.open();
  }

  private static GuiElement summaryItem(String id, Id icon, String name, List<MutationSummary.Section> sections, SkillRegistry registry) {
    return GuiTheme.element(id, icon, "gui", "mutation", id)
        .setName(name)
        .addLore(MutationSummary.lore(sections, registry))
        .setRole(MenuRole.INFO);
  }

  private static GuiElement sectionHeading(MutationSummary.Section section, SkillRegistry registry) {
    GuiElement heading = switch (section.kind()) {
      case PARENT -> {
        Skill<?> skill = registry.getSkill(section.skillId());
        yield skill == null ? new GuiElement("section").setMaterial(Id.minecraft("book")) : SkillCards.icon("section-" + section.skillId(), skill);
      }
      case RESULT -> new GuiElement("section-result").setMaterial(MUTATE).setEnchanted(true);
      case KEPT -> new GuiElement("section-kept").setMaterial(KEPT);
      case TOOLS -> new GuiElement("section-tools").setMaterial(TOOLS);
    };
    heading.setName(section.heading()).setRole(MenuRole.INFO);
    if (!section.detail().isEmpty()) {
      heading.addLore(TextFormat.WHITE + section.detail());
    }
    return heading;
  }

  private static GuiElement entryElement(MutationEntry entry) {
    Id item = Id.tryParse(entry.icon().itemId());
    Id model = entry.icon().itemModelId() == null ? null : Id.tryParse(entry.icon().itemModelId());
    GuiElement element = new GuiElement("entry-" + entry.id())
        .setName(TextFormat.WHITE + entry.name())
        .setEnchanted(entry.level() > 0)
        .setRole(MenuRole.INFO);
    if (item == null) {
      element.setMaterial(Id.minecraft("paper"));
    } else {
      element.setModel(new CustomModel(item, entry.icon().customModelData(), model == null ? CustomModel.EMPTY_KEY : model, null));
    }
    if (!entry.detail().isEmpty()) {
      element.addLore(TextFormat.GRAY + entry.detail());
    }
    return element;
  }

  /** A skill's icon with its name and the player's level in it. */
  private static GuiElement skillTile(String id, Skill<?> skill, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(SkillHost.lineKey(skill));
    return SkillCards.icon(id, skill)
        .addLore(TextFormat.WHITE + AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", line == null ? 0 : line.getLevel())));
  }

  /** The knowledge each level costs, such as "15 / 14 / 18". */
  public static String costs(Adaptation<?> adaptation) {
    List<String> costs = new ArrayList<>(adaptation.getMaxLevel());
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      costs.add(String.valueOf(adaptation.getCostFor(level)));
    }
    return String.join(" / ", costs);
  }

  /**
   * Mutates with every rule checked again, then says the result, and on success plays the mutation sound and opens
   * the mutated skill. Returns the merge result.
   */
  public static MutationResult.Status complete(PlayerRef player, Skill<?> lead, Skill<?> partner) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (adaptPlayer == null) {
      return MutationResult.Status.UNKNOWN_SKILL;
    }
    MutationResult.Status result = merge(player, adaptPlayer, lead, partner);
    String text = resultText(result, lead, partner);
    (result == MutationResult.Status.MUTATED ? FConst.success(text) : FConst.error(text))
        .send(new PlayerCommandSource(player));
    if (result != MutationResult.Status.MUTATED) {
      return result;
    }
    SkillRegistry registry = registry();
    MutatedSkill mutated = registry == null ? null
        : registry.getMutatedSkill(adaptPlayer.getData().mutationOf(SkillHost.lineKey(lead)));
    if (mutated != null) {
      AdaptScreens.openSkill(player, mutated);
    }
    return result;
  }

  /** Mutates with every rule checked again and plays the mutation sound when it went through. */
  public static MutationResult.Status merge(PlayerRef player, AdaptPlayer adaptPlayer, Skill<?> lead, Skill<?> partner) {
    MutationResult.Status result = PlayerApi.profile(adaptPlayer).mutate(lead.getId(), partner.getId()).status();
    if (result == MutationResult.Status.MUTATED) {
      SkillUpgrades.SOUNDS.mutation(Kits.of(lead).sounds(), Kits.of(partner).sounds(), SoundStage.following(lead, player));
    }
    return result;
  }

  /** Whether {@code player} may undo a mutation: in Adapt's debug mode, or with {@link #UNMUTATE_PERMISSION}. */
  public static boolean mayUnmutate(PlayerRef player) {
    return player != null && (AdaptDebugMode.isActive(player) || player.hasPermission(UNMUTATE_PERMISSION));
  }

  /** Whether a skill's menu offers Unmutate to {@code player}: a mutated skill, viewed in debug mode. */
  public static boolean offersUnmutate(Skill<?> skill, PlayerRef player) {
    return skill instanceof MutatedSkill && AdaptDebugMode.isActive(player);
  }

  /**
   * Puts Unmutate in the footer's action slot of a mutated skill's menu when {@link #offersUnmutate} allows it. The
   * first click arms it and the second undoes the mutation and opens the lead skill's menu.
   */
  public static void placeUnmutate(GuiWindow window, Skill<?> skill, PlayerRef player) {
    if (offersUnmutate(skill, player)) {
      GuiFrame.footer(window, GuiFrame.Slot.ACTION, unmutateButton(window, (MutatedSkill) skill, player, false));
    }
  }

  private static GuiElement unmutateButton(GuiWindow window, MutatedSkill mutated, PlayerRef player, boolean armed) {
    if (!armed) {
      return GuiTheme.element("mutation-unmutate", UNMUTATE, "gui", "mutation", "unmutate")
          .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.UNMUTATE))
          .setRole(MenuRole.ACTION)
          .onLeftClick((MenuClick e) -> {
            MenuSounds.play(player, MenuSounds.Action.ARM);
            GuiFrame.footer(window, GuiFrame.Slot.ACTION, unmutateButton(window, mutated, player, true));
            window.update();
          });
    }
    return GuiTheme.element("mutation-unmutate", ARMED, "gui", "mutation", "armed")
        .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.UNMUTATE_CONFIRM))
        .setHint(MenuHint.destructive(AdaptLanguage.text(MutationMessages.UNMUTATE_CONFIRM)))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> {
          window.close();
          AdaptPlayer adaptPlayer = adaptPlayer(player);
          SkillPair mutation = mutated.getPair();
          if (adaptPlayer == null || !mayUnmutate(player) || !unmutate(player, adaptPlayer, mutation)) {
            return;
          }
          FConst.success(unmutatedText(mutation, player.name())).send(new PlayerCommandSource(player));
          SkillRegistry registry = registry();
          Skill<?> lead = registry == null ? null : registry.getSkill(mutation.lead());
          if (lead != null) {
            ComponentHost.of(lead).openGui(player);
          }
        });
  }

  /**
   * Undoes {@code mutation} for an online player through their profile, which tells their client to refresh. False
   * when they do not hold it or a listener refused.
   */
  public static boolean unmutate(PlayerRef player, AdaptPlayer adaptPlayer, SkillPair mutation) {
    return PlayerApi.profile(adaptPlayer).unmutate(mutation.lead()).succeeded();
  }

  /** What an unmutate says: the mutation removed and both parents back. */
  public static String unmutatedText(SkillPair mutation, String playerName) {
    SkillRegistry registry = registry();
    return AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNMUTATED,
        trusted("mutation", mutationName(registry, mutation)),
        untrusted("player", playerName),
        trusted("lead", skillName(registry, mutation.lead())),
        trusted("partner", skillName(registry, mutation.partner())));
  }

  /** The pair's name in the viewer's language; its line key while either parent is not registered. */
  private static String mutationName(SkillRegistry registry, SkillPair pair) {
    Skill<?> lead = registry == null ? null : registry.getSkill(pair.lead());
    Skill<?> partner = registry == null ? null : registry.getSkill(pair.partner());
    return lead == null || partner == null ? pair.key() : mutationName(lead, partner);
  }

  private static String mutationName(Skill<?> lead, Skill<?> partner) {
    SkillRegistry registry = registry();
    PairRegistry claims = registry == null ? new PairRegistry() : registry.getPairRegistry();
    return AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, new MutatedText.Parents(lead, partner, claims)));
  }

  private static String skillName(SkillRegistry registry, Id skillId) {
    Skill<?> skill = registry == null ? null : registry.getSkill(skillId);
    return skill == null ? skillId.toString() : skill.getLocalizedName();
  }

  /** The player-facing text of a merge result. */
  public static String resultText(MutationResult.Status result, Skill<?> lead, Skill<?> partner) {
    AdaptConfig.Mutations config = config();
    return switch (result) {
      case MUTATED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_CREATED,
          trusted("lead", lead.getLocalizedName()), trusted("partner", partner.getLocalizedName()),
          trusted("mutation", mutationName(lead, partner)));
      case UNKNOWN_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNKNOWN_SKILL);
      case UNAVAILABLE_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNAVAILABLE_SKILL);
      case SAME_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_SAME_SKILL);
      case ALREADY_MUTATED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_ALREADY_MUTATED);
      case LEVEL_TOO_LOW -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LEVEL_TOO_LOW,
          trusted("level", config.getRequiredSkillLevel()));
      case LIMIT_REACHED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LIMIT_REACHED,
          trusted("limit", config.getMaxMutations()));
      case CANCELLED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_CANCELLED);
      case UNMUTATED, NOT_MUTATED -> throw new IllegalArgumentException("Not a merge result: " + result);
    };
  }

  /** A picking menu of skills: the heading, the skills seven a row, Back and the pager. */
  private static void skillMenu(PlayerRef player, String tag, GuiElement heading, PlayerData data, List<Skill<?>> skills,
                                int page, Consumer<Skill<?>> pick, Runnable back, IntConsumer turn) {
    GuiFrame.Layout<Skill<?>> layout = GuiFrame.grid(skills, COLUMNS);
    int currentPage = layout.clampedPage(page);
    MenuSounds.play(player, MenuSounds.Action.OPEN);
    GuiWindow window = new GuiWindow(player);
    GuiFrame.apply(window, tag, layout.bodyRows());
    window.setLayout(MenuLayout.GRID);
    GuiFrame.subject(window, heading);
    GuiFrame.body(window, layout.page(currentPage), (GuiFrame.Cell<Skill<?>> cell) ->
        skillTile("mutation-skill-" + SkillHost.lineKey(cell.item()), cell.item(), data)
            .setRole(MenuRole.NAVIGATION)
            .onLeftClick((MenuClick e) -> pick.accept(cell.item())));
    GuiFrame.back(window, back);
    GuiFrame.pager(window, currentPage, layout.pageCount(), skills.size(), turn);
    window.setTitle(AdaptLanguage.text(MutationMessages.MUTATE));
    window.open();
  }

  private static AdaptPlayer adaptPlayer(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    return core == null || core.getAdaptServer() == null || player == null ? null : core.getAdaptServer().getPlayer(player);
  }

  private static SkillRegistry registry() {
    AdaptCore core = AdaptCore.current();
    return core == null || core.getAdaptServer() == null ? null : core.getAdaptServer().getSkillRegistry();
  }

  private static AdaptConfig.Mutations config() {
    return AdaptConfig.get().getMutations();
  }
}
