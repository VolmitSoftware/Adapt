package art.arcane.adapt.client.protocol;

import art.arcane.adapt.engine.notification.HudSlot;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightKind;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationWords;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceGroupView;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One fixed example of every frame, keyed by the golden file name it encodes to. */
final class FrameSamples {
  static final IconRef ICON = new IconRef("minecraft:feather", 7, "adapt:skill/agility", "adapt:gui/agility");
  static final IconRef PLAIN_ICON = IconRef.item("minecraft:paper");

  private FrameSamples() {
  }

  static Map<String, ServerFrame> server() {
    Map<String, ServerFrame> frames = new LinkedHashMap<>();
    frames.put("server-accept", new ServerFrame.Accept(ClientProtocol.VERSION, new ServerInfo("paper", "26.3", ClientProtocol.ALL)));
    frames.put("server-catalog", new ServerFrame.Catalog(catalog()));
    frames.put("server-player", new ServerFrame.Player(player()));
    frames.put("server-player-update", new ServerFrame.PlayerUpdate(new PlayerDelta(
        new PlayerHeader(13, 0.25D, 5, 13, 2L, false),
        List.of(new SkillLineView("agility", 150D, 4, 0.5D, 25D, 3L)),
        Map.of("adapt:agility/wall_jump", 0),
        List.of(new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, "agility", "enabled"), "OFF")),
        List.of(new PreferenceKey(PreferenceOwner.ADAPTATION, "adapt:agility/wall_jump", "enabled")),
        new PlayerAccess(false, false, true, List.of("agility"), Map.of("agility", List.of(List.of("adapt:agility/wall_jump"))))
    )));
    frames.put("server-open-route", new ServerFrame.OpenRoute(new Route.Adaptation("adapt:agility/wall_jump")));
    frames.put("server-panel-open", new ServerFrame.PanelOpen(panel()));
    frames.put("server-panel-update", new ServerFrame.PanelUpdate(panel()));
    frames.put("server-panel-close", new ServerFrame.PanelClose(300));
    frames.put("server-hud-publish", new ServerFrame.HudPublish("adapt:xp", -5, 3000L,
        List.of(HudSlot.LEFT, HudSlot.RIGHT), "§b+12 Agility"));
    frames.put("server-hud-clear", new ServerFrame.HudClear("adapt:xp"));
    frames.put("server-hud-status", new ServerFrame.HudStatus("adapt:rift/blink", HudGlyph.CHARGES, "rift", ICON,
        "Blink", 1_700_000_000_000L, 8000L, 2, 5, false));
    frames.put("server-hud-clear-all", new ServerFrame.HudClearAll());
    frames.put("server-xp-earned", new ServerFrame.XpEarned("agility", 12.5D, 1012.5D, 9, 0.75D));
    frames.put("server-toast", new ServerFrame.Toast(ToastKind.LEVEL_UP, ICON, "Agility 10", "+1 knowledge", ""));
    frames.put("server-toast-challenge", new ServerFrame.Toast(ToastKind.CHALLENGE, ICON, "Marathon", "Run 50 km", ""));
    frames.put("server-toast-skill", new ServerFrame.Toast(ToastKind.SIGNATURE, ICON, "Called Shot", "Mark a target",
        "adapt:ranged+adapt:stealth"));
    frames.put("server-custom-items", new ServerFrame.CustomItems(List.of(
        new ItemDescriptor("adapt:bound_ender_pearl", "minecraft:ender_pearl", "Bound Pearl", List.of("Linked"),
            "adapt:bound_ender_pearl", 0, new byte[] {1, 2, 3, (byte) 0xFF}),
        new ItemDescriptor("adapt:experience_orb", "minecraft:paper", "Orb", List.of(), null, 42, new byte[0])
    )));
    frames.put("server-feedback", new ServerFrame.Feedback(FeedbackKind.FAILURE, "Not enough knowledge"));
    frames.put("server-insight-target", insight());
    frames.put("server-insight-vitals", new ServerFrame.InsightVitals(4242, 12.5D, 4D));
    frames.put("server-insight-marks", new ServerFrame.InsightMarks(List.of(
        new ServerFrame.InsightMarks.Mark(77, InsightKind.HOSTILE, 0.5D),
        new ServerFrame.InsightMarks.Mark(-3, InsightKind.PLAYER, 1D))));
    frames.put("server-insight-clear", new ServerFrame.InsightClear());
    frames.put("server-mutate-offers", new ServerFrame.MutateOffers(List.of("swords", "stealth")));
    frames.put("server-mutation-kept", new ServerFrame.MutationKept("Kept without learning",
        List.of(new KeptView("adapt:swords+adapt:stealth", "swords-machete", 1), new KeptView("adapt:swords+adapt:stealth", "stealth-sight", 2))));
    frames.put("server-mutation-tools", new ServerFrame.MutationTools(List.of(new ToolView("adapt:swords/upgrade/tempest_flow",
        "adapt:swords/tool/whetstone", "Whetstone", new IconRef("minecraft:flint", 4902919, null, "adapt:textures/gui/tool/tool-swords-whetstone.png"),
        2, List.of(new ToolView.Ingredient("minecraft:flint", 2), new ToolView.Ingredient("#minecraft:stone_crafting_materials", 1))))));
    frames.put("server-mutation-spread", new ServerFrame.MutationSpread(spread()));
    frames.put("server-xp-guides", new ServerFrame.XpGuides("Earning XP", List.of(
        new XpGuideView("swords", List.of(), List.of(new XpGuideView.Section("swords", "Swords", List.of("Hit mobs with a sword", "Kill mobs with a sword")))),
        new XpGuideView("adapt:swords+adapt:stealth", List.of("swords", "stealth"), List.of(
            new XpGuideView.Section("swords", "Swords", List.of("Hit mobs with a sword")),
            new XpGuideView.Section("stealth", "Stealth", List.of("Sneak")),
            new XpGuideView.Section("", "Mutation", List.of("Both parents fill one bar")))))));
    frames.put("server-spending", new ServerFrame.Spending(true, List.of(
        new SpendView("adapt:swords+adapt:stealth", "adapt:swords/upgrade/witherblade", 2, 45, 1, SpendView.Source.LEARNED),
        new SpendView("adapt:swords+adapt:stealth", "swords-machete", 1, 0, 0, SpendView.Source.KEPT))));
    frames.put("server-group-labels", new ServerFrame.GroupLabels(List.of(
        new GroupLabelView("hunter", List.of("When Struck", "The Hunt")),
        new GroupLabelView("kinetics", List.of("Spear", "Mace", "Movement")))));
    frames.put("server-short-names", new ServerFrame.ShortNames(List.of(
        new ShortNameView("adapt:pickaxe/drop_to_inventory", "Drop to Inventory"),
        new ShortNameView("adapt:hunter/luck", "Luck"))));
    frames.put("server-adaptation-recipes", new ServerFrame.AdaptationRecipes(List.of(
        new RecipeView("adapt:brewing/haste", RecipeView.Kind.BREWING, 1, "minecraft:haste", 0, List.of(
            new RecipeView.Item(new IconRef("minecraft:potion", 0, null,
                "minecraft:textures/item/potion_overlay.png#33ebff|minecraft:textures/item/potion.png"), "",
                "item.minecraft.potion.effect.swiftness", 1, List.of(new RecipeView.Effect("minecraft:speed", 0, 3600, false))),
            new RecipeView.Item(IconRef.item("minecraft:amethyst_shard"), "", "", 1, List.of())),
            new RecipeView.Item(new IconRef("minecraft:potion", 0, null,
                "minecraft:textures/item/potion_overlay.png#ffff00|minecraft:textures/item/potion.png"), "Bottled Haste", "", 1,
                List.of(new RecipeView.Effect("minecraft:haste", 1, 600, false)))),
        new RecipeView("adapt:architect/chalk_line", RecipeView.Kind.SHAPED, 2, "minecraft:string", 2, List.of(
            new RecipeView.Item(IconRef.item("minecraft:string"), "", "", 1, List.of()), RecipeView.Item.NONE, RecipeView.Item.NONE,
            new RecipeView.Item(IconRef.item("minecraft:stick"), "", "", 1, List.of())),
            new RecipeView.Item(new IconRef("minecraft:stick", 4902922, null, null), "Chalk Polyline Wand", "", 1, List.of())))));
    frames.put("server-asset-manifest", new ServerFrame.AssetManifest(List.of(
        new AssetRef("adapt:textures/gui/skill/agility.png", "da39a3ee5e6b4b0d3255bfef95601890afd80709"),
        new AssetRef("alchemist:themes/alchemy.json", "0123456789abcdef0123456789abcdef01234567"))));
    frames.put("server-asset", new ServerFrame.Asset("alchemist:textures/gui/skill/alchemy.png",
        "0123456789abcdef0123456789abcdef01234567", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}));
    return frames;
  }

  static Map<String, ClientFrame> client() {
    Map<String, ClientFrame> frames = new LinkedHashMap<>();
    frames.put("client-hello", new ClientFrame.Hello(ClientProtocol.VERSION, ClientProtocol.ALL));
    frames.put("client-hello-withdraw", new ClientFrame.Hello(0, 0));
    frames.put("client-learn", new ClientFrame.Learn("adapt:agility/wall_jump", 3, 42L));
    frames.put("client-unlearn", new ClientFrame.Unlearn("adapt:agility/wall_jump", 1, 42L));
    frames.put("client-set-preference", new ClientFrame.SetPreference(PreferenceOwner.ADAPTATION, "adapt:rift/blink",
        "momentum", "STOP"));
    frames.put("client-reset-preference", new ClientFrame.ResetPreference(PreferenceOwner.SKILL, "agility"));
    frames.put("client-open", new ClientFrame.Open(new Route.Skill("agility")));
    frames.put("client-open-skills", new ClientFrame.Open(new Route.Skills()));
    frames.put("client-panel-click", new ClientFrame.PanelClick(300, 4, 13, ClickKind.SHIFT_RIGHT));
    frames.put("client-panel-input", new ClientFrame.PanelInput(300, 4, 22, "über 64"));
    frames.put("client-panel-closed", new ClientFrame.PanelClosed(300));
    frames.put("client-jump-key", new ClientFrame.JumpKey(true));
    frames.put("client-mutate", new ClientFrame.Mutate("swords"));
    frames.put("client-mutation-request", new ClientFrame.MutationRequest("swords", "stealth", true));
    frames.put("client-open-mutate", new ClientFrame.Open(new Route.Mutate("swords")));
    frames.put("client-unmutate", new ClientFrame.Unmutate("swords"));
    frames.put("client-asset-request", new ClientFrame.AssetRequest(List.of("alchemist:textures/gui/skill/alchemy.png",
        "alchemist:themes/alchemy.json")));
    return frames;
  }

  static MutationSpreadView spread() {
    MutationEntry upgrade = new MutationEntry("adapt:swords/upgrade/witherblade", ICON, "Witherblade", "Wither on hit.\n13 / 13 / 16 knowledge", 0);
    MutationEntry kept = new MutationEntry("swords-machete", ICON, "Machete I", "Cut through foliage.", 1);
    MutationEntry tool = new MutationEntry("ledger", IconRef.item("minecraft:book"), "Ledger", "Tool", 0);
    MutationEntry lost = new MutationEntry("stealth-sight", ICON, "Ghost Sight II", "", 2);
    MutationSide lead = new MutationSide("swords", 22, List.of(upgrade), List.of(kept), List.of(tool), List.of(lost),
        "5 Swords levels · 9 Swords knowledge");
    return new MutationSpreadView("swords", "stealth", "Silent Duelist", "Level 17 · 37 knowledge", List.of(upgrade), lead,
        MutationSide.NONE, List.of(new MutationCandidate("stealth", 17, MutationCandidate.State.ELIGIBLE, ""),
        new MutationCandidate("axes", 9, MutationCandidate.State.LEVEL_TOO_LOW, "Needs level 15")),
        new MutationWords("Mutate", "Lead", "Partner", "Gains", "Loses", "Hold to mutate", "This cannot be undone", "Swap lead",
            "Level {level}"),
        "adapt:swords+adapt:stealth");
  }

  static ServerFrame.InsightTarget insight() {
    return new ServerFrame.InsightTarget(4242, "minecraft:wolf", "Rex", InsightKind.PASSIVE, 16D, 20D, 0D, 4D,
        InsightBond.OWNED, "Steve", List.of("adapt:stable_hand"),
        List.of(new ServerFrame.InsightTarget.Stat("minecraft:attack_damage", 4D),
            new ServerFrame.InsightTarget.Stat("minecraft:movement_speed", 0.3D)),
        List.of(new ServerFrame.InsightTarget.Effect("minecraft:speed", 1, 600, false),
            new ServerFrame.InsightTarget.Effect("minecraft:poison", 0, -1, true)));
  }

  static CatalogView catalog() {
    PreferenceView enabled = new PreferenceView("enabled", "Enabled", "ON", List.of(
        new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 1),
        new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 1)
    ), true);
    AdaptationView wallJump = new AdaptationView(
        "adapt:agility/wall_jump",
        "agility",
        "Wall Jump",
        "Jump off walls.",
        ICON,
        true,
        2,
        false,
        List.of(5, 7),
        List.of(5, 7),
        List.of(1, 1),
        List.of(new RequirementView("adapt:agility/super_jump", 1)),
        List.of(List.of("+1 jump"), List.of("+2 jumps", "Less fall damage")),
        List.of(enabled),
        List.of(new PreferenceGroupView("effects", "Effects", PLAIN_ICON,
            List.of(new PreferenceGroupView.Entry("enabled", PLAIN_ICON))))
    );
    SkillView agility = new SkillView(
        "agility",
        "Agility",
        "Move faster.",
        ICON,
        0x55FFFF,
        true,
        List.of(List.of("adapt:agility/wall_jump")),
        List.of(enabled),
        List.of(wallJump)
    );
    return new CatalogView(42L, "en_US", List.of(agility));
  }

  static PlayerView player() {
    return new PlayerView(
        new PlayerHeader(12, 0.5D, 3, 12, 1L, true),
        List.of(new SkillLineView("agility", 100D, 3, 0.25D, 50D, 2L)),
        Map.of("adapt:agility/wall_jump", 2, "adapt:agility/super_jump", 1),
        List.of(
            new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, "agility", "enabled"), "ON"),
            new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, "adapt:agility/wall_jump", "enabled"), "ON")
        ),
        new PlayerAccess(true, true, false, List.of("agility"),
            Map.of("agility", List.of(List.of("adapt:agility/wall_jump"), List.of("adapt:agility/super_jump")), "rift", List.of()))
    );
  }

  static PanelView panel() {
    return new PanelView(300, 4, "Settings", 3, PanelLayout.FORM, new PanelPaging(1, 3, 40), List.of("Adapt", "Core"),
        List.of(
            new PanelTile(13, ICON, 1, "Sounds", List.of("Plays sounds", ""), true, TileHint.of(HintKind.TOGGLE, "ON"),
                TileRole.SETTING, "Audio", "ON", List.of("Plays sounds"), List.of(new TileAction(ClickKind.LEFT, "Toggle"))),
            new PanelTile(14, PLAIN_ICON, 1, "Range", List.of(), false,
                new TileHint(HintKind.NUMBER, "2.5", true, List.of(), 0.5D, 8D, 0.5D), TileRole.SETTING, "Audio", "2.5", null,
                List.of()),
            new PanelTile(15, PLAIN_ICON, 1, "Mode", List.of(), false,
                new TileHint(HintKind.CHOICE, "FAST", true, List.of("SLOW", "FAST"), null, null, null), TileRole.SETTING, "",
                "FAST", null, List.of(new TileAction(ClickKind.LEFT, "Next"), new TileAction(ClickKind.RIGHT, "Previous"))),
            new PanelTile(22, PLAIN_ICON, 64, "Back", List.of(), false, null, TileRole.NAVIGATION, "", "", null, List.of())
        ));
  }
}
