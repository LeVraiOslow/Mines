package fr.ivalona.mines.hook;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import fr.ivalona.mines.pickaxe.Upgrades;
import fr.ivalona.mines.util.Text;
import fr.ivalona.mines.zone.Zone;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Placeholders (CustomNamePlates, scoreboard...) :
 *  %ivalonamines_minecoins%  %ivalonamines_minecoins_brut%
 *  %ivalonamines_cristaux%   %ivalonamines_cristaux_brut%
 *  %ivalonamines_niveau_<vitesse|taille|profondeur|recompenses>%
 *  %ivalonamines_valeur_<vitesse|taille|profondeur|recompenses>%
 *  %ivalonamines_dans_mine%
 *  %ivalonamines_regen_<zone>%   (temps avant la prochaine régénération)
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final IvalonaMines plugin;

    public PapiExpansion(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "ivalonamines";
    }

    @Override
    public String getAuthor() {
        return "Ivalona";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("regen_")) {
            Zone z = plugin.zones().get(p.substring(6));
            if (z == null || z.pattern == null) return "-";
            return Text.duration((z.nextRegen() - System.currentTimeMillis()) / 1000);
        }
        if (player == null) return "";
        PlayerData d = plugin.data().get(player);
        if (p.equals("minecoins")) return Text.number(d.minecoins);
        if (p.equals("minecoins_brut")) return String.valueOf(d.minecoins);
        if (p.equals("cristaux")) return Text.number(d.cristaux);
        if (p.equals("cristaux_brut")) return String.valueOf(d.cristaux);
        if (p.equals("dans_mine")) return d.dansMine ? "oui" : "non";
        if (p.startsWith("niveau_")) {
            String t = Upgrades.normalize(p.substring(7));
            return t == null ? "" : String.valueOf(plugin.upgrades().clamp(t, d.niveau(t)));
        }
        if (p.startsWith("valeur_")) {
            String t = Upgrades.normalize(p.substring(7));
            return t == null ? "" : plugin.upgrades().value(t, d.niveau(t));
        }
        return null;
    }
}
