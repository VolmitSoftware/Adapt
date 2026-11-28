package art.arcane.adapt.pack.screen;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.menu.LedgerSkin;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackInk;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every colour a tooltip line on a pack frame is written in is an ink that reads on the darkest parchment (the platform
 * writes uncoloured text in the text ink), and every drawn slot is the carrier.
 */
class PackTooltipInkTest {
  private static final Pattern HEX = Pattern.compile("§x((?:§[0-9a-fA-F]){6})");
  private static final double READABLE = 4.5;

  private final FakePlayer player = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
  private final FakePackWindows windows = new FakePackWindows();
  private final PackResources resources = PackSamples.resources();
  private final Set<PlayerRef> book = new HashSet<>(Set.of(player));
  private final PackMenuHost host = new PackMenuHost(new FakeMenuHost(), windows, book::contains, () -> resources);
  private final ScreenFixtures.Server server = new ScreenFixtures.Server();
  private final PackScreens screens = new PackScreens(host, server, server, new ScreenFixtures.Prompts(), new ScreenFixtures.Clock(),
      new ScreenFixtures.Speaker());

  PackTooltipInkTest() {
    server.screens = screens;
  }

  @AfterEach
  void uninstall() {
    AdaptPorts.uninstall(PackScreens.class);
  }

  @Test
  void theCarrierReadsAsABook() {
    assertThat(PackManifest.CARRIER.toString()).isEqualTo("minecraft:book");
  }

  @Test
  void skillsTooltipsAreInked() {
    assertThat(screens.openSkills(player)).isTrue();
    assertInked("skills");
  }

  @Test
  void skillTooltipsAreInked() {
    server.unmutateOffered = true;
    assertThat(screens.openSkill(player, "adapt:agility")).isTrue();
    assertInked("skill");
  }

  @Test
  void adaptationTooltipsAreInkedOnEveryTab() {
    assertThat(screens.openAdaptation(player, "adapt:agility/super_jump")).isTrue();
    assertInked("levels");
    List<PackSlot> tabs = resources.layout().screen("adaptation.levels", windows.support.lowerSlots()).slots("foot.tabs");
    assertThat(tabs).hasSizeGreaterThanOrEqualTo(3);
    windows.window(player).click(tabs.get(1), ClickKind.LEFT);
    assertInked("recipes");
    screens.openAdaptation(player, "adapt:agility/super_jump");
    windows.window(player).click(tabs.get(2), ClickKind.LEFT);
    assertInked("settings");
  }

  @Test
  void mutateLeadTooltipsAreInked() {
    assertThat(screens.openMutateLeads(player)).isTrue();
    assertInked("mutate leads");
  }

  @Test
  void mutateTooltipsAreInked() {
    screens.openMutate(player, "adapt:agility");
    assertInked("mutate pick");
    windows.window(player).click(resources.layout().screen(PackMutateScreen.PICK, windows.support.lowerSlots()).cards().get(0).icon(),
        ClickKind.LEFT);
    assertInked("mutate");
  }

  @Test
  void keptAndToolTooltipsAreInked() {
    String mutated = "adapt:agility+adapt:rift";
    AdaptationView own = new AdaptationView("adapt:mutated/agility/adapt/rift/graft", mutated, "Graft", "Grafted.", ScreenFixtures.PLAIN,
        true, 3, false, List.of(1, 2, 3), List.of(1, 2, 3), List.of(0, 0, 0), List.of(), List.of(List.of("+1"), List.of("+2"), List.of("+3")),
        List.of(), List.of());
    List<List<String>> rows = List.of(List.of("adapt:agility/super_jump"), List.of("adapt:rift/gate"), List.of(own.id()));
    SkillView skill = new SkillView(mutated, "Feral Blinkstrider", "Both.", ScreenFixtures.AGILITY_ICON, 0x55FFFF, true,
        rows, List.of(), List.of(ScreenFixtures.superJump(), ScreenFixtures.gate(), own));
    List<SkillView> skills = new ArrayList<>(server.catalog.catalog().skills());
    skills.add(skill);
    server.catalog = new CatalogIndex(new CatalogView(43L, "en_US", skills), Map.of());
    PlayerView base = server.player;
    Map<String, List<List<String>>> access = new LinkedHashMap<>(base.access().adaptationRows());
    access.put(mutated, rows);
    List<String> listed = new ArrayList<>(base.access().skills());
    listed.add(mutated);
    server.player = new PlayerView(base.header(), base.skills(), base.adaptationLevels(), base.preferences(),
        new PlayerAccess(false, true, true, listed, access));
    server.owners = Map.of(mutated, List.of("adapt:agility", "adapt:rift", mutated));
    server.kept = List.of(new KeptView(mutated, "adapt:agility/wall_jump", 1));
    server.tools = List.of(new ToolView(own.id(), "adapt:agility/tool/hook", "Hook", ScreenFixtures.PLAIN, 2,
        List.of(new ToolView.Ingredient("minecraft:string", 2))));

    assertThat(screens.openSkill(player, mutated)).isTrue();
    assertInked("mutated skill");
    screens.page(player, 1);
    assertInked("shelf");
  }

  @Test
  void ledgerTooltipsAreInked() {
    ChestMenu menu = ChestMenu.builder("Golems", 3)
        .set(0, MenuButton.display(MenuIcon.of(Id.minecraft("stone"), "§fWhite §aGreen", List.of("§7Grey §eYellow", "plain"))))
        .set(11, new MenuButton(MenuIcon.of(Id.minecraft("arrow"), "§bNext", List.of("§8dark")), (MenuClick click) -> { }))
        .build();
    LedgerSkin.Skinned skinned = LedgerSkin.skin(menu, resources, null, (MenuSession session) -> { });
    PackSurface surface = skinned.surface();
    int framed = 0;
    for (Map.Entry<Integer, MenuButton> entry : skinned.menu().buttons().entrySet()) {
      SlotLook look = surface.look(PackSlot.top(entry.getKey()));
      if (look == null || look.tooltipStyle() == null || look.hideTooltip()) {
        continue;
      }
      framed++;
      MenuIcon icon = entry.getValue().icon();
      assertLine("ledger " + entry.getKey() + " name", icon.name());
      for (String line : icon.lore()) {
        assertLine("ledger " + entry.getKey() + " lore", line);
      }
    }
    assertThat(framed).isEqualTo(2);
  }

  private void assertInked(String screen) {
    FakePackWindows.Window window = windows.window(player);
    Map<PackSlot, MenuButton> slots = new LinkedHashMap<>();
    for (Map.Entry<Integer, MenuButton> entry : window.menu.buttons().entrySet()) {
      slots.put(PackSlot.top(entry.getKey()), entry.getValue());
    }
    for (Map.Entry<Integer, MenuButton> entry : window.surface.lower().entrySet()) {
      slots.put(PackSlot.lower(entry.getKey()), entry.getValue());
    }
    int framed = 0;
    for (Map.Entry<PackSlot, MenuButton> entry : slots.entrySet()) {
      SlotLook look = window.surface.look(entry.getKey());
      MenuIcon icon = entry.getValue().icon();
      String where = screen + " " + entry.getKey();
      if (look != null && look.itemModel() != null) {
        assertThat(icon.type()).as(where).isEqualTo(PackManifest.CARRIER);
      }
      if (look == null || look.tooltipStyle() == null || look.hideTooltip()) {
        continue;
      }
      framed++;
      assertLine(where + " name", icon.name());
      for (String line : icon.lore()) {
        assertLine(where + " lore", line);
      }
    }
    assertThat(framed).as(screen).isPositive();
  }

  private static void assertLine(String where, String line) {
    if (line == null || LegacyText.plain(line).isBlank()) {
      return;
    }
    assertThat(HEX.matcher(line).replaceAll("")).as(where + " chat colour: " + line).doesNotContainPattern("§[0-9a-fA-F]");
    Matcher hex = HEX.matcher(line);
    while (hex.find()) {
      int rgb = Integer.parseInt(hex.group(1).replace("§", ""), 16);
      assertThat(PackInk.contrast(rgb, PackInk.PAPER)).as(where + ": " + line).isGreaterThanOrEqualTo(READABLE);
    }
  }
}
