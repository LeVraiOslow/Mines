package fr.ivalona.mines.command;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import fr.ivalona.mines.pickaxe.Upgrades;
import fr.ivalona.mines.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /ivalonamines : administration. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("reload", "setspawn", "give", "minecoins", "cristaux", "niveau", "info", "reset");

    private final IvalonaMines plugin;

    public AdminCommand(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String key, String... vars) {
        plugin.messages().send(s, key, Text.vars(vars));
    }

    /** joueur connecté ou déjà venu sur le serveur */
    static OfflinePlayer target(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !SUBS.contains(args[0].toLowerCase(Locale.ROOT))) {
            msg(sender, "aide-admin");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "reload");
                plugin.logs().log("admin", sender.getName() + " recharge la configuration");
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "joueur-seulement");
                    return true;
                }
                plugin.setMineSpawn(p.getLocation());
                msg(p, "spawn-defini");
                plugin.logs().log("admin", p.getName() + " définit le spawn de la mine");
            }
            case "give" -> {
                Player t = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (t == null) {
                    msg(sender, "joueur-introuvable");
                    return true;
                }
                plugin.pickaxes().give(t);
                msg(sender, "pioche-donnee", "joueur", t.getName());
                plugin.logs().log("admin", sender.getName() + " donne la pioche à " + t.getName());
            }
            case "minecoins", "cristaux" -> money(sender, sub, args);
            case "niveau" -> {
                if (args.length < 4) {
                    msg(sender, "aide-admin");
                    return true;
                }
                OfflinePlayer t = target(args[1]);
                if (t == null) {
                    msg(sender, "joueur-introuvable");
                    return true;
                }
                String type = Upgrades.normalize(args[2]);
                if (type == null) {
                    msg(sender, "amelioration-inconnue", "liste", String.join(", ", Upgrades.TYPES));
                    return true;
                }
                int lvl;
                try {
                    lvl = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    msg(sender, "nombre-invalide", "valeur", args[3]);
                    return true;
                }
                lvl = plugin.upgrades().clamp(type, lvl);
                PlayerData d = plugin.data().get(t);
                d.niveaux.put(type, lvl);
                plugin.data().saveAsync(d);
                if (t instanceof Player op && plugin.isMineWorld(op.getWorld()) && d.dansMine) plugin.pickaxes().give(op);
                msg(sender, "niveau-modifie", "joueur", d.name, "amelioration", plugin.upgrades().name(type), "niveau", String.valueOf(lvl));
                plugin.logs().log("admin", sender.getName() + " : " + d.name + " " + type + " niveau " + lvl);
            }
            case "info" -> {
                if (args.length < 2) {
                    msg(sender, "aide-admin");
                    return true;
                }
                OfflinePlayer t = target(args[1]);
                if (t == null) {
                    msg(sender, "joueur-introuvable");
                    return true;
                }
                PlayerData d = plugin.data().get(t);
                StringBuilder lv = new StringBuilder();
                for (String type : Upgrades.TYPES) {
                    if (lv.length() > 0) lv.append(", ");
                    lv.append(type).append(' ').append(plugin.upgrades().clamp(type, d.niveau(type)));
                }
                msg(sender, "info-joueur", "joueur", d.name, "minecoins", Text.number(d.minecoins),
                        "cristaux", Text.number(d.cristaux), "niveaux", lv.toString(), "dans_mine", d.dansMine ? "oui" : "non");
            }
            case "reset" -> {
                if (args.length < 2) {
                    msg(sender, "aide-admin");
                    return true;
                }
                OfflinePlayer t = target(args[1]);
                if (t == null) {
                    msg(sender, "joueur-introuvable");
                    return true;
                }
                PlayerData d = plugin.data().get(t);
                d.minecoins = 0;
                d.cristaux = 0;
                d.niveaux.clear();
                plugin.data().saveAsync(d); // l'inventaire sauvegardé n'est pas touché
                if (t instanceof Player op && plugin.isMineWorld(op.getWorld()) && d.dansMine) plugin.pickaxes().give(op);
                msg(sender, "reset-joueur", "joueur", d.name);
                plugin.logs().log("admin", sender.getName() + " remet à zéro " + d.name);
            }
            default -> msg(sender, "aide-admin");
        }
        return true;
    }

    private void money(CommandSender sender, String currency, String[] args) {
        if (args.length < 4) {
            msg(sender, "aide-admin");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer t = target(args[2]);
        if (t == null) {
            msg(sender, "joueur-introuvable");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3].replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            msg(sender, "nombre-invalide", "valeur", args[3]);
            return;
        }
        if (amount < 0) {
            msg(sender, "nombre-invalide", "valeur", args[3]);
            return;
        }
        PlayerData d = plugin.data().get(t);
        boolean coins = currency.equals("minecoins");
        long cur = coins ? d.minecoins : d.cristaux;
        long val = switch (op) {
            case "give", "add" -> cur + amount;
            case "take", "remove" -> Math.max(0, cur - amount);
            case "set" -> amount;
            default -> -1;
        };
        if (val < 0) {
            msg(sender, "aide-admin");
            return;
        }
        if (coins) d.minecoins = val;
        else d.cristaux = val;
        plugin.data().saveAsync(d);
        msg(sender, "argent-modifie", "joueur", d.name, "minecoins", Text.number(d.minecoins), "cristaux", Text.number(d.cristaux));
        plugin.logs().log("admin", sender.getName() + " : " + currency + " " + op + " " + amount + " -> " + d.name
                + " (MineCoins " + d.minecoins + ", Cristaux " + d.cristaux + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(a0)) out.add(s);
        } else if ((a0.equals("minecoins") || a0.equals("cristaux")) && args.length == 2) {
            for (String s : List.of("give", "take", "set")) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (((a0.equals("minecoins") || a0.equals("cristaux")) && args.length == 3)
                || (List.of("give", "niveau", "info", "reset").contains(a0) && args.length == 2)) {
            String start = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(start)) out.add(p.getName());
        } else if (a0.equals("niveau") && args.length == 3) {
            for (String t : Upgrades.TYPES) if (t.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(t);
        }
        return out;
    }
}
