package com.pixelmon.europe.clanland.commands;

import com.pixelmon.europe.clanland.*;
import com.pixelmon.europe.clanland.gui.GuiManager;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.util.ChunkPos;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClanlandCommand implements CommandExecutor, TabCompleter {

    private final ClanlandPlugin plugin;
    private final GuiManager gui;
    private final ClaimManager claims;
    private final FlagManager flags;
    private final UClansHook uclans;
    private final AdminModeManager admin;
    private final ClanSettingsManager settings;
    private final FlyManager flyManager;
    private final HomeManager homeManager; // NOUVEAU

    // CONSTRUCTEUR MIS A JOUR
    public ClanlandCommand(ClanlandPlugin plugin, GuiManager gui, ClaimManager claims, FlagManager flags, UClansHook uc, AdminModeManager am, ClanSettingsManager settings, FlyManager fly, HomeManager home) {
        this.plugin = plugin;
        this.gui = gui;
        this.claims = claims;
        this.flags = flags;
        this.uclans = uc;
        this.admin = am;
        this.settings = settings;
        this.flyManager = fly;
        this.homeManager = home;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            if (!p.hasPermission("clanland.command.use")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.use");
                return true;
            }
            gui.openMain(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        // --- HOME (TP) ---
        if (sub.equals("home")) {
            if (!p.hasPermission("clanland.command.home")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.home");
                return true;
            }
            String cId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (cId == null) { p.sendMessage(ChatColor.RED + "Tu n'as pas de clan."); return true; }

            Location home = homeManager.getHome(cId);
            if (home == null) {
                p.sendMessage(ChatColor.RED + "Aucun point de spawn défini pour le clan (Leader: /cl sethome).");
                return true;
            }

            p.teleport(home);
            p.sendMessage(ChatColor.GREEN + "Téléportation au QG du clan...");
            return true;
        }

        // --- SETHOME ---
        if (sub.equals("sethome")) {
            if (!p.hasPermission("clanland.command.sethome")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.sethome");
                return true;
            }

            String cId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (cId == null) { p.sendMessage(ChatColor.RED + "Tu n'as pas de clan."); return true; }

            // Vérif rôle (Leader/Coleader)
            String role = uclans.getRoleName(p.getUniqueId()).orElse("").toLowerCase();
            // Adaptez selon les noms de rôles exacts de votre UClans, souvent "leader" et "coleader"
            if (!role.contains("leader")) {
                p.sendMessage(ChatColor.RED + "Seuls les chefs et co-chefs peuvent définir le spawn.");
                return true;
            }

            ChunkPos pos = ChunkPos.of(p.getLocation());
            String owner = claims.getOwnerIdAt(pos);

            // SÉCURITÉ : Le home doit être DANS un claim du clan
            if (owner == null || !owner.equals(cId)) {
                p.sendMessage(ChatColor.RED + "Le point de spawn doit être défini À L'INTÉRIEUR de votre territoire claim.");
                return true;
            }

            homeManager.setHome(cId, p.getLocation());
            p.sendMessage(ChatColor.GREEN + "Point de spawn du clan mis à jour !");
            return true;
        }

        // --- FLY ---
        if (sub.equals("fly")) {
            if (!p.hasPermission("clanland.command.fly")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.fly");
                return true;
            }
            boolean st = flyManager.toggleFly(p);
            if (st) p.sendMessage(ChatColor.GREEN + "Mode Fly Clan: ACTIVÉ (Vol possible dans vos claims)");
            else p.sendMessage(ChatColor.RED + "Mode Fly Clan: DÉSACTIVÉ");
            return true;
        }

        // --- RELOAD ---
        if (sub.equals("reload")) {
            if (!p.hasPermission("clanland.command.reload")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.reload");
                return true;
            }
            plugin.reloadConfig();
            claims.loadAll();
            flags.loadAll();
            settings.load();
            homeManager.load(); // Recharge homes
            p.sendMessage(ChatColor.GREEN + "Configuration rechargée avec succès.");
            return true;
        }

        // --- CLAIM ---
        if (sub.equals("claim")) {
            if (!p.hasPermission("clanland.command.claim")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.claim");
                return true;
            }
            String cId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (cId == null) { p.sendMessage(ChatColor.RED + "Tu n'as pas de clan."); return true; }
            ChunkPos pos = ChunkPos.of(p.getLocation());

            if (claims.getOwnerIdAt(pos) != null) { p.sendMessage(ChatColor.RED + "Déjà claimé."); return true; }
            if (!claims.isWorldAllowed(p.getWorld())) { p.sendMessage(ChatColor.RED + "Monde interdit."); return true; }
            if (!claims.respectsMinDistance(cId, pos)) { p.sendMessage(ChatColor.RED + "Trop proche d'un autre clan."); return true; }

            if (plugin.getConfig().getBoolean("claims.must-be-contiguous", true)) {
                int levelBypass = plugin.getConfig().getInt("claims.contiguous-bypass-level", 5);
                int clanLevel = uclans.getClanLevelById(cId).orElse(0);
                boolean adminBypass = admin.isAdmin(p.getUniqueId());

                if (!adminBypass && clanLevel < levelBypass && !claims.isContiguous(cId, pos)) {
                    p.sendMessage(ChatColor.RED + "Le claim doit toucher votre territoire (Niveau " + levelBypass + "+ pour ignorer).");
                    return true;
                }
            }

            if (claims.getClaimCountForClan(cId) >= claims.getMaxClaims(cId)) { p.sendMessage(ChatColor.RED + "Limite atteinte."); return true; }

            double price = claims.nextPriceFor(cId);
            if (!uclans.withdrawClan(cId, price)) { p.sendMessage(ChatColor.RED + "Banque insuffisante."); return true; }

            claims.addClaim(cId, pos);
            try { claims.saveAll(); } catch(Exception e) {}
            p.sendMessage(ChatColor.GREEN + "Chunk claim !");
            return true;
        }

        // --- DECLAIM ---
        if (sub.equals("declaim")) {
            if (!p.hasPermission("clanland.command.declaim")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.declaim");
                return true;
            }
            String cId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            ChunkPos pos = ChunkPos.of(p.getLocation());
            String owner = claims.getOwnerIdAt(pos);
            boolean isAdmin = admin.isAdmin(p.getUniqueId()) || p.hasPermission("clanland.command.admin");

            if (owner == null) { p.sendMessage(ChatColor.RED + "Pas de claim ici."); return true; }
            if (!isAdmin && (cId == null || !owner.equals(cId))) { p.sendMessage(ChatColor.RED + "Pas à toi."); return true; }

            String effectiveId = (cId != null) ? cId : p.getUniqueId().toString();

            // Si c'était le home, on pourrait le supprimer ici, mais pas obligatoire.

            if (claims.declaimById(effectiveId, pos, isAdmin)) {
                p.sendMessage(ChatColor.GREEN + "Chunk declaim.");
                try { claims.saveAll(); } catch(Exception e) {}
            }
            return true;
        }

        // --- SETMSG ---
        if (sub.equals("setmsg")) {
            if (!p.hasPermission("clanland.command.setmsg")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.setmsg");
                return true;
            }
            String clanId = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (clanId == null) { p.sendMessage("§cTu n'as pas de clan."); return true; }
            String role = uclans.getRoleName(p.getUniqueId()).orElse("").toLowerCase();
            if (!role.contains("leader")) { p.sendMessage("§cRéservé aux chefs."); return true; }
            if (args.length < 3) { p.sendMessage("§cUsage: /cl setmsg <enter|exit> <msg>"); return true; }

            String msg = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            if (args[1].equalsIgnoreCase("enter")) {
                settings.setEnterMessage(clanId, msg);
                p.sendMessage("§aMessage d'entrée mis à jour.");
            } else if (args[1].equalsIgnoreCase("exit")) {
                settings.setExitMessage(clanId, msg);
                p.sendMessage("§aMessage de sortie mis à jour.");
            }
            return true;
        }

        // --- VISUALIZE ---
        if (sub.equals("visualize")) {
            if (!p.hasPermission("clanland.command.visualize")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.visualize");
                return true;
            }
            boolean state = admin.toggleVisualizer(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Visualisation: " + (state ? "ON" : "OFF"));
            return true;
        }

        // --- GIVEWAND ---
        if (sub.equals("givewand")) {
            if (!p.hasPermission("clanland.command.givewand")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.givewand");
                return true;
            }
            ItemStack wand = plugin.getVisualizerItem();
            if (wand != null) {
                p.getInventory().addItem(wand);
                p.sendMessage(ChatColor.GREEN + "Vous avez reçu la Baguette de Territoire.");
            } else {
                p.sendMessage(ChatColor.RED + "Item non configuré ou désactivé.");
            }
            return true;
        }

        // --- FLAGS ---
        if (sub.equals("flags")) {
            if (!p.hasPermission("clanland.command.flags")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.flags");
                return true;
            }
            String id = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            if (id == null) { p.sendMessage(ChatColor.RED + "Pas de clan."); return true; }
            gui.openFlagsRoleSelect(p, id);
            return true;
        }

        // --- ADMIN ---
        if (sub.equals("admin") || sub.equals("mode")) {
            if (!p.hasPermission("clanland.command.admin")) {
                p.sendMessage(ChatColor.RED + "Permission manquante: clanland.command.admin");
                return true;
            }
            boolean st = admin.toggle(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Admin mode: " + (st ? "ON" : "OFF"));
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("claim", "declaim", "flags", "visualize", "setmsg", "reload", "admin", "givewand", "fly", "home", "sethome");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setmsg")) {
            return Arrays.asList("enter", "exit");
        }
        return Collections.emptyList();
    }
}