package fr.ivalona.mines.command;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import fr.ivalona.mines.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /mine, /boutiquemine, /minecoins */
public final class PlayerCommands implements TabExecutor {

    private final IvalonaMines plugin;

    public PlayerCommands(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("minecoins")) return balance(sender, args);
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "joueur-seulement", null);
            return true;
        }
        if (name.equals("mine")) {
            if (!p.hasPermission(plugin.configs().main().getString("acces.permission", "ivalonamines.acces"))) {
                plugin.messages().send(p, "acces-refuse", null);
                return true;
            }
            Location spawn = plugin.mineSpawn();
            if (spawn == null) {
                World w = Bukkit.getWorld(plugin.configs().main().getString("monde", "IvalonaMines"));
                if (w == null) {
                    plugin.messages().send(p, "spawn-non-defini", null);
                    return true;
                }
                spawn = w.getSpawnLocation();
            }
            plugin.messages().send(p, "teleportation-mine", null);
            p.teleport(spawn);
            return true;
        }
        if (name.equals("boutiquemine")) {
            if (!p.hasPermission("ivalonamines.boutique")) {
                plugin.messages().send(p, "pas-de-permission", null);
                return true;
            }
            plugin.menus().openShop(p);
        }
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length >= 1 && sender.hasPermission("ivalonamines.minecoins.autres")) {
            OfflinePlayer t = AdminCommand.target(args[0]);
            if (t == null) {
                plugin.messages().send(sender, "joueur-introuvable", null);
                return true;
            }
            PlayerData d = plugin.data().get(t);
            plugin.messages().send(sender, "solde-autre", Text.vars("joueur", d.name,
                    "minecoins", Text.number(d.minecoins), "cristaux", Text.number(d.cristaux)));
            return true;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "joueur-seulement", null);
            return true;
        }
        PlayerData d = plugin.data().get(p);
        plugin.messages().send(p, "solde", Text.vars("minecoins", Text.number(d.minecoins), "cristaux", Text.number(d.cristaux)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("minecoins") && args.length == 1 && sender.hasPermission("ivalonamines.minecoins.autres")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
