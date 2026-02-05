package com.pixelmon.europe.clanland.papi;

import com.pixelmon.europe.clanland.ClaimManager;
import com.pixelmon.europe.clanland.ClanlandPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public class ClanlandExpansion extends PlaceholderExpansion {

    private final ClanlandPlugin plugin;
    private final ClaimManager claims;

    public ClanlandExpansion(ClanlandPlugin plugin, ClaimManager claims) {
        this.plugin = plugin; this.claims = claims;
    }

    @Override
    public boolean persist() { return true; }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public String getIdentifier() { return "clanland"; }

    @Override
    public String getAuthor() { return "PixelmonEurope"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) return "";
        String clanId = new com.pixelmon.europe.clanland.hooks.UClansHook(plugin).getClanUUIDOf(player.getUniqueId()).orElse(null);
        if (clanId == null) return "";
        switch (params.toLowerCase()) {
            case "claimed":
                return String.valueOf(claims.getClaimCountForClan(clanId));
            case "max":
                return String.valueOf(claims.getMaxClaims(clanId));
            case "nextprice":
                return plugin.formatMoney(claims.nextPriceFor(clanId));
            default:
                return "";
        }
    }
}