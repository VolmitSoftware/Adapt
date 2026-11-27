package com.example.geology.fabric;

import com.example.geology.GeologyAddon;
import net.fabricmc.api.ModInitializer;

public final class GeologyFabric implements ModInitializer {
  @Override
  public void onInitialize() {
    GeologyAddon.install();
  }
}
