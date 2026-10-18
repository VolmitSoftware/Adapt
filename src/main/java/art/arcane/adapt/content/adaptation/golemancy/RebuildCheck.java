package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;

record RebuildCheck(boolean readable, boolean ownsItem, boolean rosterKnown, GolemRoster.Status status, boolean sealed, int level, int shatterLevel,
                    GolemRegistry.BindBlock registryBlock, boolean playerCreated, boolean allowVillageGolems, boolean builderExclusive,
                    boolean bodyUnlocked, boolean canInteract) {
}
