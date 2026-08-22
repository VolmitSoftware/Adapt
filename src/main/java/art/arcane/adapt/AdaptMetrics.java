/*
 * Adapt is Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.adapt;

import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

/**
 * Owns every bStats type so the main class never references them. bStats is slimjar-provided:
 * any chart code in Adapt.class makes the verifier resolve the relocated CustomChart during
 * main-class linking, before ApplicationBuilder.build() has injected the library (boot NCDFE).
 */
public final class AdaptMetrics {
  private Metrics metrics;

  private AdaptMetrics(Metrics metrics) {
    this.metrics = metrics;
  }

  public static AdaptMetrics start(Adapt plugin, int pluginId) {
    AdaptMetrics runtime = new AdaptMetrics(new Metrics(plugin, pluginId));
    runtime.registerCharts();
    return runtime;
  }

  // Chart callables run on the bStats daemon thread; keep them off Bukkit world/entity state.
  private void registerCharts() {
    metrics.addCustomChart(new SingleLineChart("registered_skills", () -> SkillRegistry.skills.size()));
    metrics.addCustomChart(new SingleLineChart("learned_adaptations", () -> {
      Adapt adapt = Adapt.instance;
      if (adapt == null) {
        return null;
      }

      AdaptServer server = adapt.getAdaptServer();
      if (server == null) {
        return null;
      }

      return server.getLearnedAdaptationCount();
    }));
    metrics.addCustomChart(new SimplePie("storage_backend", () -> {
      AdaptConfig config = AdaptConfig.get();
      if (config.getRedis().isEnabled()) {
        return "redis";
      }

      return config.getSql().isEnabled() ? "sql" : "json";
    }));
    metrics.addCustomChart(new SimplePie("custom_models", () -> String.valueOf(AdaptConfig.get().isCustomModels())));
    metrics.addCustomChart(new SimplePie("advancements", () -> String.valueOf(AdaptConfig.get().isAdvancements())));
  }

  public void shutdown() {
    Metrics activeMetrics = metrics;
    metrics = null;
    if (activeMetrics != null) {
      try {
        activeMetrics.shutdown();
      } catch (Throwable e) {
        Adapt.verbose("Failed to shut down metrics: " + e.getMessage());
      }
    }
  }
}
