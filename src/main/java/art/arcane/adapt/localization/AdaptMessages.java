package art.arcane.adapt.localization;

import art.arcane.adapt.api.mutation.MutationDomain;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.localization.catalog.AxeMessages;
import art.arcane.adapt.localization.catalog.BlockingMessages;
import art.arcane.adapt.localization.catalog.BrewingMessages;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.localization.catalog.CommandMessages;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.ConfigMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.localization.catalog.HunterMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.ItemsMessages;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.localization.catalog.RangedMessages;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.localization.catalog.SwordMessages;
import art.arcane.adapt.localization.catalog.TamingMessages;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.localization.catalog.UnarmedMessages;
import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;

public final class AdaptMessages {
  private static final MessageCatalog CATALOG = createCatalog();

  private AdaptMessages() {
  }

  public static MessageCatalog catalog() {
    return CATALOG;
  }

  public static MessageKey require(String id) {
    return CATALOG.require(id);
  }

  private static MessageCatalog createCatalog() {
    MessageCatalog.Builder builder = MessageCatalog.builder("en_US");
    builder.addAll(MutationDomain.keys());
    builder.addAll(MutationType.keys());
    builder.addAll(DirectorMessages.keys());
    AdvancementMessages.addTo(builder);
    AgilityMessages.addTo(builder);
    ArchitectMessages.addTo(builder);
    AxeMessages.addTo(builder);
    BlockingMessages.addTo(builder);
    BrewingMessages.addTo(builder);
    ChronosMessages.addTo(builder);
    ConfigMessages.addTo(builder);
    CraftingMessages.addTo(builder);
    DiscoveryMessages.addTo(builder);
    EnchantingMessages.addTo(builder);
    ExcavationMessages.addTo(builder);
    HerbalismMessages.addTo(builder);
    HunterMessages.addTo(builder);
    GuiMessages.addTo(builder);
    ItemsMessages.addTo(builder);
    KineticsMessages.addTo(builder);
    MutationMessages.addTo(builder);
    NetherMessages.addTo(builder);
    PickaxeMessages.addTo(builder);
    RangedMessages.addTo(builder);
    RiftMessages.addTo(builder);
    RuntimeMessages.addTo(builder);
    SeabornMessages.addTo(builder);
    SkillMessages.addTo(builder);
    SnippetsMessages.addTo(builder);
    StealthMessages.addTo(builder);
    SwordMessages.addTo(builder);
    TamingMessages.addTo(builder);
    TragoulMessages.addTo(builder);
    UnarmedMessages.addTo(builder);
    CommandMessages.addTo(builder);
    CommandRuntimeMessages.addTo(builder);
    return builder.build();
  }
}
