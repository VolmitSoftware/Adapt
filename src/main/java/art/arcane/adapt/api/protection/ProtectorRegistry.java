/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.protection;

import art.arcane.adapt.Adapt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProtectorRegistry {
    private final List<Protector> protectors = new ArrayList<>();
    private volatile List<Protector> allProtectorsSnapshot = List.of();
    private volatile List<Protector> defaultProtectorsSnapshot = List.of();

    public synchronized void registerProtector(Protector protector) {
        if (protector == null || protectors.contains(protector)) {
            return;
        }

        Adapt.verbose("Protector: \"" + protector.getName() + "\" registered.");
        protectors.add(protector);
        rebuildSnapshots();
    }

    public synchronized void unregisterProtector(Protector protector) {
        if (protector == null) {
            return;
        }

        protector.unregister();
        if (protectors.remove(protector)) {
            rebuildSnapshots();
        }
    }

    public List<Protector> getDefaultProtectors() {
        return defaultProtectorsSnapshot;
    }

    public List<Protector> getAllProtectors() {
        return allProtectorsSnapshot;
    }

    public synchronized void unregisterAll() {
        protectors.forEach(Protector::unregister);
        protectors.clear();
        rebuildSnapshots();
    }

    private void rebuildSnapshots() {
        List<Protector> all = new ArrayList<>(protectors.size());
        List<Protector> defaults = new ArrayList<>(Math.max(1, protectors.size() / 2));

        for (Protector protector : protectors) {
            if (protector == null) {
                continue;
            }
            all.add(protector);
            if (protector.isEnabledByDefault()) {
                defaults.add(protector);
            }
        }

        allProtectorsSnapshot = Collections.unmodifiableList(all);
        defaultProtectorsSnapshot = Collections.unmodifiableList(defaults);
    }
}
