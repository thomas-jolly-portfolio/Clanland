package com.pixelmon.europe.clanland.listener;

import com.pixelmon.europe.clanland.*;
import com.pixelmon.europe.clanland.gui.GuiManager;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.util.ChunkPos;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable; // IMPORT AJOUTÉ ICI
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Iterator;
import java.util.List;

public class ProtectionListener implements Listener {

    private final ClanlandPlugin plugin;
    private final GuiManager gui;
    private final ClaimManager claims;
    private final FlagManager flags;
    private final UClansHook uclans;
    private final AdminModeManager admin;

    public ProtectionListener(ClanlandPlugin plugin, GuiManager gui, ClaimManager cm, FlagManager fm, UClansHook uc, AdminModeManager am) {
        this.plugin = plugin;
        this.gui = gui;
        this.claims = cm;
        this.flags = fm;
        this.uclans = uc;
        this.admin = am;
    }

    // =========================================================================
    // 1. LOGIQUE DE ROLES (VOTRE CONFIGURATION)
    // =========================================================================

    private FlagManager.Role getEffectiveRole(Player p, String chunkOwnerId) {
        if (p == null) return FlagManager.Role.visitor;

        String playerClan = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);

        if (playerClan == null || !playerClan.equals(chunkOwnerId)) {
            return FlagManager.Role.visitor;
        }

        String r = uclans.getRoleName(p.getUniqueId()).orElse("member").toLowerCase();
        switch (r) {
            case "leader":      return FlagManager.Role.leader;
            case "coleader":    return FlagManager.Role.coleader;
            case "moderator":   return FlagManager.Role.moderator;
            case "officer":     return FlagManager.Role.officer;
            case "strategist":  return FlagManager.Role.strategist;
            case "recruiter":   return FlagManager.Role.recruiter;
            case "ultramember": return FlagManager.Role.ultramember;
            case "supermember": return FlagManager.Role.supermember;
            case "member":
            default:            return FlagManager.Role.member;
        }
    }

    // =========================================================================
    // 2. OUTILS & VISUALISATION
    // =========================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onWandInteract(PlayerInteractEvent e) {
        if (e.getItem() != null && plugin.isVisualizerItem(e.getItem())) {
            e.setCancelled(true);
            Player p = e.getPlayer();

            if (!p.hasPermission("clanland.command.visualize")) return;

            if (e.getAction().toString().contains("RIGHT")) {
                String cid = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
                if (cid == null) p.sendMessage("§cPas de clan.");
                else gui.openClaimMap(p, cid);
            } else if (e.getAction().toString().contains("LEFT")) {
                boolean st = admin.toggleVisualizer(p.getUniqueId());
                p.sendMessage("§eVisualisation: " + (st ? "§aON" : "§cOFF"));
            }
        }
    }

    // =========================================================================
    // 3. PROTECTION JOUEUR (INTERACT, BREAK, PLACE)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (plugin.isVisualizerItem(e.getItem())) return;

        Block b = e.getClickedBlock();
        Material mat = b.getType();
        Action action = e.getAction();

        if (action == Action.PHYSICAL && mat == Material.SOIL) {
            if (shouldCancel(e.getPlayer(), b.getLocation(), FlagManager.GlobalFlag.BREAK, FlagManager.Action.BREAK, mat)) {
                e.setCancelled(true);
            }
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            boolean isContainer = (b.getState() instanceof InventoryHolder)
                    || mat.name().contains("SHULKER_BOX")
                    || mat.name().contains("CHEST")
                    || mat == Material.ANVIL
                    || mat == Material.WORKBENCH
                    || mat == Material.ENCHANTMENT_TABLE
                    || mat == Material.ENDER_CHEST
                    || mat == Material.HOPPER
                    || mat == Material.DISPENSER
                    || mat == Material.DROPPER
                    || mat == Material.BREWING_STAND
                    || mat == Material.BEACON
                    || mat == Material.FURNACE
                    || mat == Material.BURNING_FURNACE
                    || mat == Material.JUKEBOX
                    || mat == Material.CAKE_BLOCK;

            FlagManager.GlobalFlag targetFlag = isContainer ? FlagManager.GlobalFlag.CONTAINER : FlagManager.GlobalFlag.INTERACT;

            if (shouldCancel(e.getPlayer(), b.getLocation(), targetFlag, FlagManager.Action.INTERACT, mat)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (shouldCancel(e.getPlayer(), e.getBlock().getLocation(), FlagManager.GlobalFlag.BUILD, FlagManager.Action.BUILD, e.getBlockPlaced().getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (shouldCancel(e.getPlayer(), e.getBlock().getLocation(), FlagManager.GlobalFlag.BREAK, FlagManager.Action.BREAK, e.getBlock().getType())) {
            e.setCancelled(true);
        }
    }

    // =========================================================================
    // 4. PROTECTION ENVIRONNEMENTALE
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().isEmpty()) return;
        handlePiston(e.getBlock(), e.getBlocks(), e.getDirection(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().isEmpty()) return;
        handlePiston(e.getBlock(), e.getBlocks(), e.getDirection().getOppositeFace(), e);
    }

    private void handlePiston(Block piston, List<Block> blocks, org.bukkit.block.BlockFace direction, Cancellable e) {
        ChunkPos pistonChunk = ChunkPos.of(piston.getLocation());
        String pistonOwner = claims.getOwnerIdAt(pistonChunk);

        for (Block b : blocks) {
            Block targetBlock = b.getRelative(direction);
            ChunkPos targetChunk = ChunkPos.of(targetBlock.getLocation());
            String targetOwner = claims.getOwnerIdAt(targetChunk);

            if (targetOwner != null && !targetOwner.equals(pistonOwner)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            ChunkPos pos = ChunkPos.of(b.getLocation());
            String owner = claims.getOwnerIdAt(pos);

            if (owner != null) {
                if (!flags.getGlobal(owner, FlagManager.Role.visitor, FlagManager.GlobalFlag.EXPLOSION)) {
                    it.remove();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (claims.getOwnerIdAt(ChunkPos.of(e.getBlock().getLocation())) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            if (claims.getOwnerIdAt(ChunkPos.of(e.getBlock().getLocation())) != null) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e) {
        ChunkPos from = ChunkPos.of(e.getBlock().getLocation());
        ChunkPos to = ChunkPos.of(e.getToBlock().getLocation());
        if (from.equals(to)) return;

        String ownerFrom = claims.getOwnerIdAt(from);
        String ownerTo = claims.getOwnerIdAt(to);

        if (ownerTo != null && !ownerTo.equals(ownerFrom)) {
            e.setCancelled(true);
        }
    }

    // =========================================================================
    // 5. PROTECTION ENTITES & PVP
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        Player attacker = null;

        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) attacker = (Player) shooter;
        }

        if (attacker == null) return;
        if (admin.isAdmin(attacker.getUniqueId())) return;

        ChunkPos pos = ChunkPos.of(e.getEntity().getLocation());
        String owner = claims.getOwnerIdAt(pos);
        if (owner == null) return;

        if (e.getEntity() instanceof Player) {
            if (!flags.getGlobal(owner, getEffectiveRole(attacker, owner), FlagManager.GlobalFlag.PVP)) {
                e.setCancelled(true);
            }
        } else {
            if (!flags.getGlobal(owner, getEffectiveRole(attacker, owner), FlagManager.GlobalFlag.INTERACT_ENTITY)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (admin.isAdmin(e.getPlayer().getUniqueId())) return;
        ChunkPos pos = ChunkPos.of(e.getRightClicked().getLocation());
        String owner = claims.getOwnerIdAt(pos);
        if (owner == null) return;

        if (!flags.getGlobal(owner, getEffectiveRole(e.getPlayer(), owner), FlagManager.GlobalFlag.INTERACT_ENTITY)) {
            e.setCancelled(true);
        }
    }

    // =========================================================================
    // 6. LOGIQUE DE VERIFICATION
    // =========================================================================

    private boolean shouldCancel(Player p, org.bukkit.Location loc, FlagManager.GlobalFlag globalFlag, FlagManager.Action action, Material mat) {
        if (admin.isAdmin(p.getUniqueId())) return false;

        ChunkPos pos = ChunkPos.of(loc);
        String owner = claims.getOwnerIdAt(pos);

        if (owner == null) return false;

        FlagManager.Role role = getEffectiveRole(p, owner);
        String blockKey = mat.name();
        boolean isSpecialItem = false;

        for (String key : flags.getAllItemKeysAny()) {
            if (key.replace(":", "").replace("_", "").equalsIgnoreCase(blockKey.replace("_", ""))) {
                blockKey = key;
                isSpecialItem = true;
                break;
            }
        }

        if (isSpecialItem) {
            return !flags.getItemFlag(owner, role, action, blockKey);
        } else {
            return !flags.getGlobal(owner, role, globalFlag);
        }
    }
}