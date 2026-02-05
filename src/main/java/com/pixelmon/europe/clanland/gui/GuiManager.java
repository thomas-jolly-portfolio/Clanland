package com.pixelmon.europe.clanland.gui;

import com.pixelmon.europe.clanland.*;
import com.pixelmon.europe.clanland.FlagManager.GlobalFlag;
import com.pixelmon.europe.clanland.FlagManager.Role;
import com.pixelmon.europe.clanland.FlagManager.Action;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.util.ChunkPos;
import com.pixelmon.europe.clanland.util.ItemBuilder;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuiManager implements Listener {

    private static final int CENTER_SLOT = 22;
    private final ClanlandPlugin plugin;
    private final ClaimManager claimManager;
    private final FlagManager flagManager;
    private final UClansHook uclans;
    private final AdminModeManager admin;

    public GuiManager(ClanlandPlugin plugin, ClaimManager cm, FlagManager fm, UClansHook uc, AdminModeManager admin) {
        this.plugin = plugin;
        this.claimManager = cm;
        this.flagManager = fm;
        this.uclans = uc;
        this.admin = admin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ========================= MAIN ========================= */
    public void openMain(Player p) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("main-gui");
        String title = plugin.color(sec.getString("title", "&9Clanland"));
        Inventory inv = Bukkit.createInventory(p, 27, title);

        placeConfiguredItem(p, inv, sec.getConfigurationSection("items.claim"));
        placeConfiguredItem(p, inv, sec.getConfigurationSection("items.flags"));
        placeConfiguredItem(p, inv, sec.getConfigurationSection("items.info"));

        // NOUVEAUX BOUTONS
        placeConfiguredItem(p, inv, sec.getConfigurationSection("items.visualize"));
        placeConfiguredItem(p, inv, sec.getConfigurationSection("items.messages"));

        p.openInventory(inv);
    }

    private String papi(Player p, String s) {
        if (s == null) return "";
        try { if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) return PlaceholderAPI.setPlaceholders(p, s); }
        catch (Throwable ignored) {}
        return s;
    }

    private void placeConfiguredItem(Player p, Inventory inv, ConfigurationSection s) {
        if (s == null) return;
        int slot = s.getInt("slot", 13);
        Material mat;
        try { mat = Material.valueOf(s.getString("material", "STONE").toUpperCase()); } catch (Exception e) { mat = Material.STONE; }
        int data = s.getInt("data", 0);
        String name = plugin.color(papi(p, s.getString("name", "&7Item")));
        List<String> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) lore.add(plugin.color(papi(p, line)));
        inv.setItem(slot, new ItemBuilder(mat, data).name(name).lore(lore).build());
    }

    /* ========================= CLAIM MAP ========================= */
    public void openClaimMap(Player p, String clanId) {
        String title = plugin.color(plugin.getConfig().getString("claim-gui.title", "&9Carte des chunks"));
        Inventory inv = Bukkit.createInventory(p, 54, title);
        int r = Math.max(1, Math.min(2, plugin.getConfig().getInt("claim-gui.radius", 1)));
        ChunkPos center = ChunkPos.of(p.getLocation());

        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int slot = slotOf(dx, dz);
                if (slot < 0 || slot >= 54) continue;
                ChunkPos pos = new ChunkPos(center.world, center.x + dx, center.z + dz);
                inv.setItem(slot, buildTileFor(p, pos, clanId));
            }
        }
        inv.setItem(45, new ItemBuilder(Material.ARROW, 0).name("&7Retour").build());
        p.openInventory(inv);
    }

    private int slotOf(int dx, int dz) { return CENTER_SLOT + dx + (dz * 9); }

    private ItemStack buildTileFor(Player viewer, ChunkPos pos, String viewerClanId) {
        String ownerId = claimManager.getOwnerIdAt(pos);
        Material mat = Material.STAINED_GLASS_PANE;
        int data = 5; // vert
        String name; List<String> lore;
        ConfigurationSection tt = plugin.getConfig().getConfigurationSection("claim-gui.tooltip");

        if (ownerId == null) {
            name = tt.getString("free.name", "&aLibre ({x};{z})");
            name = name.replace("{x}", String.valueOf(pos.x)).replace("{z}", String.valueOf(pos.z));

            double price = (viewerClanId != null) ? claimManager.nextPriceFor(viewerClanId) : 0;
            int claimed = (viewerClanId != null) ? claimManager.getClaimCountForClan(viewerClanId) : 0;
            int max = (viewerClanId != null) ? claimManager.getMaxClaims(viewerClanId) : 0;

            lore = replaceXZ(tt.getStringList("free.lore"), pos);
            lore = replace(lore, "{price}", plugin.formatMoney(price));
            lore = replace(lore, "{claimed}", String.valueOf(claimed));
            lore = replace(lore, "{max}", String.valueOf(max));
        } else if (viewerClanId != null && ownerId.equals(viewerClanId)) {
            data = 11; // bleu
            name = tt.getString("owned-self.name", "&9Ton clan ({x};{z})").replace("{x}", String.valueOf(pos.x)).replace("{z}", String.valueOf(pos.z));
            lore = replaceXZ(tt.getStringList("owned-self.lore"), pos);
            lore = replaceClanInfo(lore, ownerId);
        } else {
            data = 14; // rouge
            name = tt.getString("owned-other.name", "&cEnnemi ({x};{z})").replace("{x}", String.valueOf(pos.x)).replace("{z}", String.valueOf(pos.z));
            lore = replaceXZ(tt.getStringList("owned-other.lore"), pos);
            lore = replaceClanInfo(lore, ownerId);
        }
        return new ItemBuilder(mat, data).name(plugin.color(name)).lore(colorize(lore)).build();
    }

    private List<String> replaceClanInfo(List<String> src, String clanId) {
        String leader = uclans.getLeaderNameByClanId(clanId).orElse("?");
        String level = String.valueOf(uclans.getClanLevelById(clanId).orElse(0));
        String tag = uclans.getClanTagById(clanId).orElse(clanId);
        List<String> out = new ArrayList<>();
        for (String s : src) {
            out.add(s.replace("{owner}", tag).replace("{leader}", leader).replace("{level}", level));
        }
        return out;
    }

    private List<String> replaceXZ(List<String> src, ChunkPos pos) {
        List<String> out = new ArrayList<>();
        for (String s : src) out.add(s.replace("{x}", String.valueOf(pos.x)).replace("{z}", String.valueOf(pos.z)));
        return out;
    }
    private List<String> replace(List<String> src, String key, String val) {
        List<String> out = new ArrayList<>();
        for (String s : src) out.add(s.replace(key, val));
        return out;
    }
    private List<String> colorize(List<String> src) {
        List<String> out = new ArrayList<>();
        for (String s : src) out.add(plugin.color(s));
        return out;
    }

    /* ========================= FLAGS ========================= */
    public void openFlagsRoleSelect(Player p, String clanId) {
        Inventory inv = Bukkit.createInventory(p, 27, plugin.color("&9Flags - Rôles"));
        Role[] roles = Role.values();
        int idx = 0;
        for (Role r : roles) inv.setItem(idx++, new ItemBuilder(Material.BOOK, 0).name("&b" + r.name()).build());
        inv.setItem(18, new ItemBuilder(Material.ARROW, 0).name("&7Retour").build());
        p.openInventory(inv);
    }

    public void openFlagsForRole(Player p, String clanId, Role role) {
        Inventory inv = Bukkit.createInventory(p, 54, plugin.color("&9Flags - " + role.name()));
        int gSlot = 0;
        GlobalFlag[] gfs = GlobalFlag.values();
        for (int i = 0; i < gfs.length && gSlot < 18; i++) {
            GlobalFlag gf = gfs[i];
            boolean val = flagManager.getGlobal(clanId, role, gf);
            Material m = val ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
            inv.setItem(gSlot++, new ItemBuilder(m, 0).name("&e" + gf.name() + " &7(" + (val ? "&aON" : "&cOFF") + "&7)").build());
        }
        int slot = 18;
        List<String> items = flagManager.getAllItemKeysAny();
        for (String key : items) {
            for (Action action : Action.values()) {
                if (slot == 45) slot++;
                if (slot >= 54) break;
                boolean val = flagManager.getItemFlag(clanId, role, action, key);
                String name = "&f" + action.name().toLowerCase() + ": " + key + " &7(" + (val ? "&aON" : "&cOFF") + "&7)";
                inv.setItem(slot++, new ItemBuilder(Material.PAPER, 0).name(name).build());
            }
            if (slot >= 54) break;
        }
        inv.setItem(45, new ItemBuilder(Material.ARROW, 0).name("&7Retour").build());
        p.openInventory(inv);
    }

    private boolean canEditFlags(Player p, String clanId) {
        UUID u = p.getUniqueId();
        if (uclans.getLeaderUUIDByClanId(clanId).map(u::equals).orElse(false)) return true;

        String r = uclans.getRoleName(u).orElse("member").toLowerCase();
        Role playerRole = Role.fromString(r);
        if (r.contains("coleader") || r.contains("co_leader")) playerRole = Role.coleader;

        return flagManager.getGlobal(clanId, playerRole, GlobalFlag.FLAGS_CHANGE);
    }

    /* ========================= CLICK ========================= */
    private String cleanTitle(String s) { return s == null ? "" : ChatColor.stripColor(s).trim(); }
    private String topTitle(InventoryView v) { try { return v.getTopInventory().getTitle(); } catch (Throwable t) { return v.getTitle(); } }
    private boolean isMain(InventoryView v) { return cleanTitle(topTitle(v)).equalsIgnoreCase(cleanTitle(plugin.color(plugin.getConfig().getString("main-gui.title", "&9Clanland")))); }
    private boolean isClaim(InventoryView v) { return cleanTitle(topTitle(v)).equalsIgnoreCase(cleanTitle(plugin.color(plugin.getConfig().getString("claim-gui.title", "&9Carte des chunks")))); }
    private boolean isFlagsRoles(InventoryView v) { return cleanTitle(topTitle(v)).startsWith("Flags - Rôles"); }
    private boolean isFlagsRole(InventoryView v)  { return cleanTitle(topTitle(v)).startsWith("Flags - "); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.getWhoClicked() == null) return;
        InventoryView view = e.getView();
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();
        if (isMain(view) || isClaim(view) || isFlagsRoles(view) || isFlagsRole(view)) {
            e.setCancelled(true);
            if (raw >= view.getTopInventory().getSize()) return;
        } else return;

        // --- MENU PRINCIPAL ---
        if (isMain(view)) {
            // Récupère les slots configurés
            int claimSlot = plugin.getConfig().getInt("main-gui.items.claim.slot", 11);
            int flagsSlot = plugin.getConfig().getInt("main-gui.items.flags.slot", 13);
            int vizSlot = plugin.getConfig().getInt("main-gui.items.visualize.slot", 10);
            int msgSlot = plugin.getConfig().getInt("main-gui.items.messages.slot", 16);

            if (raw == claimSlot) {
                String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
                boolean adminMode = admin.isAdmin(p.getUniqueId()) || p.hasPermission("clanland.command.admin");

                if (clanId == null && !adminMode) { p.sendMessage("§cTu n'as pas de clan."); return; }
                String targetId = (clanId != null) ? clanId : p.getUniqueId().toString();
                openClaimMap(p, targetId);
            } else if (raw == flagsSlot) {
                String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
                if (clanId == null) { p.sendMessage("§cTu n'as pas de clan."); return; }
                openFlagsRoleSelect(p, clanId);
            } else if (raw == vizSlot) {
                // BOUTON VISUALIZE
                p.closeInventory();
                p.chat("/clanland visualize");
            } else if (raw == msgSlot) {
                // BOUTON MESSAGES
                p.closeInventory();
                p.sendMessage("§eConfiguration des messages :");
                p.sendMessage("§b/cl setmsg enter <message> §7- Message de bienvenue");
                p.sendMessage("§b/cl setmsg exit <message> §7- Message d'adieu");
            }
            p.updateInventory();
            return;
        }

        // --- MENU CARTE ---
        if (isClaim(view)) {
            if (raw == 45) { openMain(p); return; }

            boolean isAdmin = admin.isAdmin(p.getUniqueId()) || p.hasPermission("clanland.command.admin");
            String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            String effectiveId = (clanId != null) ? clanId : p.getUniqueId().toString();

            if (clanId == null && !isAdmin) { p.sendMessage("§cTu n'as pas de clan."); return; }

            int dx = (raw % 9) - (CENTER_SLOT % 9);
            int dz = (raw / 9) - (CENTER_SLOT / 9);
            if (Math.abs(dx) > 2 || Math.abs(dz) > 2) return;

            ChunkPos center = ChunkPos.of(p.getLocation());
            ChunkPos pos = new ChunkPos(center.world, center.x + dx, center.z + dz);
            String owner = claimManager.getOwnerIdAt(pos);

            if (e.isLeftClick() && owner == null) {
                if (clanId == null) { p.sendMessage("§cImpossible de claim sans clan."); return; }
                if (!claimManager.isWorldAllowed(p.getWorld())) { p.sendMessage("§cMonde non autorisé."); return; }
                if (!claimManager.respectsMinDistance(clanId, pos)) { p.sendMessage("§cTrop proche d'un autre clan."); return; }
                if (claimManager.getClaimCountForClan(clanId) >= claimManager.getMaxClaims(clanId)) { p.sendMessage("§cLimite atteinte."); return; }

                double price = claimManager.nextPriceFor(clanId);
                if (!uclans.withdrawClan(clanId, price)) { p.sendMessage("§cBanque insuffisante."); return; }

                claimManager.addClaim(clanId, pos);
                try { claimManager.saveAll(); } catch(Exception ex) {}
                p.sendMessage("§aChunk claim !");
                openClaimMap(p, effectiveId);
            } else if (e.isRightClick() && owner != null) {
                if ((clanId != null && owner.equals(clanId)) || isAdmin) {
                    claimManager.declaimById(effectiveId, pos, isAdmin);
                    try { claimManager.saveAll(); } catch(Exception ex) {}
                    p.sendMessage("§aChunk declaim.");
                    openClaimMap(p, effectiveId);
                } else {
                    p.sendMessage("§cCe chunk n'appartient pas à ton clan.");
                }
            }
            return;
        }

        // --- MENUS FLAGS ---
        if (isFlagsRoles(view)) {
            if (raw == 18) { openMain(p); return; }
            String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (clanId == null) return;
            if (raw >= 0 && raw < Role.values().length) openFlagsForRole(p, clanId, Role.values()[raw]);
        }
        if (isFlagsRole(view)) {
            if (raw == 45) {
                String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
                if (clanId != null) openFlagsRoleSelect(p, clanId);
                return;
            }
            String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (clanId == null) return;
            if (!canEditFlags(p, clanId)) { p.sendMessage("§cPermission refusée (FLAGS_CHANGE requit)."); return; }

            String title = cleanTitle(topTitle(view));
            String t = title.substring(title.indexOf("Flags - ") + 8).trim();
            Role role; try { role = Role.valueOf(t.toLowerCase()); } catch (Exception ex) { return; }

            if (raw < 18) {
                GlobalFlag gf = GlobalFlag.values()[raw];
                boolean cur = flagManager.getGlobal(clanId, role, gf);
                flagManager.setGlobal(clanId, role, gf, !cur);
                try { flagManager.saveAll(); } catch(Exception ex) {}
                openFlagsForRole(p, clanId, role);
            } else if (raw >= 18) {
                ItemStack cur = e.getCurrentItem();
                if (cur == null || !cur.hasItemMeta()) return;
                String dn = ChatColor.stripColor(cur.getItemMeta().getDisplayName());
                if (dn.contains(": ")) {
                    String[] parts = dn.split(": ", 2);
                    String actStr = parts[0].trim().toUpperCase();
                    String key = parts[1].trim();
                    if (key.contains(" (")) key = key.substring(0, key.lastIndexOf(" ("));

                    try {
                        Action a = Action.valueOf(actStr);
                        boolean val = flagManager.getItemFlag(clanId, role, a, key);
                        flagManager.setItemFlag(clanId, role, a, key, !val);
                        try { flagManager.saveAll(); } catch(Exception ex) {}
                        openFlagsForRole(p, clanId, role);
                    } catch(Exception ignored) {}
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryView view = e.getView();
        if (isMain(view) || isClaim(view) || isFlagsRoles(view) || isFlagsRole(view)) {
            int top = view.getTopInventory().getSize();
            for (int slot : e.getRawSlots()) { if (slot < top) { e.setCancelled(true); return; } }
        }
    }
}