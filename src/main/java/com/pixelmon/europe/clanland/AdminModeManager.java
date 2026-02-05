package com.pixelmon.europe.clanland;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AdminModeManager {
    private final Set<UUID> admins = new HashSet<>();
    private final Set<UUID> visualizing = new HashSet<>();

    public boolean toggle(UUID id) {
        if (admins.contains(id)) { admins.remove(id); return false; }
        admins.add(id); return true;
    }

    public void set(UUID id, boolean val) {
        if (val) admins.add(id); else admins.remove(id);
    }

    public boolean isAdmin(UUID id) { return admins.contains(id); }

    // --- Visualisation ---
    public boolean toggleVisualizer(UUID id) {
        if (visualizing.contains(id)) { visualizing.remove(id); return false; }
        visualizing.add(id); return true;
    }

    public boolean isVisualizing(UUID id) { return visualizing.contains(id); }
}