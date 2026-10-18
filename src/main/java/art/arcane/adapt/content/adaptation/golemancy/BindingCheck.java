package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRegistry;

record BindingCheck(GolemRegistry.BindBlock registryBlock, boolean playerCreated, boolean allowVillageGolems,
                    boolean builderExclusive, boolean bodyUnlocked, boolean coreUnlocked, boolean canInteract,
                    int ownedGolems, int golemCap, int playerLevel, int tithe) {
}
