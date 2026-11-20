package fr.ivalona.mines.util;

import fr.ivalona.mines.IvalonaMines;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Messages de messages/messages.yml (texte simple ou liste de lignes). */
public final class Messages {

    private final IvalonaMines plugin;

    public Messages(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration yml() {
        return plugin.configs().get(Configs.MESSAGES);
    }

    public List<String> lines(String key, Map<String, String> vars) {
        List<String> raw = new ArrayList<>();
        if (yml().isList(key)) raw.addAll(yml().getStringList(key));
        else {
            String s = yml().getString(key, "");
            if (s != null && !s.isEmpty()) raw.add(s);
        }
        String prefix = yml().getString("prefix", "");
        List<String> out = new ArrayList<>();
        for (String l : raw) out.add(Text.replace(l.replace("{prefix}", prefix), vars));
        return out;
    }

    public String get(String key, Map<String, String> vars) {
        List<String> l = lines(key, vars);
        return l.isEmpty() ? "" : String.join("\n", l);
    }

    public void send(CommandSender to, String key, Map<String, String> vars) {
        if (to == null) return;
        for (String l : lines(key, vars)) to.sendMessage(Text.color(l));
    }

    public void actionBar(Player p, String key, Map<String, String> vars) {
        String s = get(key, vars);
        if (!s.isEmpty()) p.sendActionBar(Text.color(s));
    }

    public void broadcastWorld(String world, String key, Map<String, String> vars) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equalsIgnoreCase(world)) send(p, key, vars);
        }
    }
}
