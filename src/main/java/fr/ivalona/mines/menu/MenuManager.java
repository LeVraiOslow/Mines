package fr.ivalona.mines.menu;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import fr.ivalona.mines.pickaxe.Upgrades;
import fr.ivalona.mines.util.Configs;
import fr.ivalona.mines.util.ItemFactory;
import fr.ivalona.mines.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Menus : améliorations de la pioche et boutique des Cristaux. */
public final class MenuManager {

    public static final String UPGRADES = "ameliorations";
    public static final String SHOP = "boutique";

    private final IvalonaMines plugin;

    public MenuManager(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg(String menu) {
        return plugin.configs().get(menu.equals(SHOP) ? Configs.MENU_BOUTIQUE : Configs.MENU_AMELIORATIONS);
    }

    public void openUpgrades(Player p) {
        open(p, UPGRADES);
    }

    public void openShop(Player p) {
        if (shopForbidden(p)) {
            plugin.messages().send(p, "boutique-interdite", null);
            return;
        }
        open(p, SHOP);
    }

    public boolean shopForbidden(Player p) {
        for (String w : plugin.configs().main().getStringList("boutique.mondes-interdits")) {
            if (w.equalsIgnoreCase(p.getWorld().getName())) return true;
        }
        return false;
    }

    private void open(Player p, String menu) {
        YamlConfiguration c = cfg(menu);
        MenuHolder h = new MenuHolder(menu);
        Map<String, String> vars = plugin.pickaxes().vars(p, plugin.data().get(p));
        int size = Math.max(9, Math.min(54, ((Math.max(1, c.getInt("size", 27)) + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(h, size, Text.color(Text.replace(c.getString("title", "&8Menu"), vars)));
        h.setInventory(inv);
        fill(p, h, c);
        p.openInventory(inv);
        Text.sound(p, plugin.configs().main().getString("sons.ouverture-menu", ""));
    }

    void refresh(Player p, MenuHolder h) {
        h.getInventory().clear();
        h.entries().clear();
        fill(p, h, cfg(h.menu()));
    }

    private void fill(Player p, MenuHolder h, YamlConfiguration c) {
        Inventory inv = h.getInventory();
        PlayerData d = plugin.data().get(p);
        Map<String, String> vars = plugin.pickaxes().vars(p, d);

        ConfigurationSection items = c.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                ItemStack st = ItemFactory.build(plugin, s, vars);
                MenuHolder.Entry e = new MenuHolder.Entry(s.getString("action", "NONE").toUpperCase(Locale.ROOT),
                        null, s.getStringList("commands"));
                for (int slot : ItemFactory.slots(s, inv.getSize())) {
                    inv.setItem(slot, st.clone());
                    h.entries().put(slot, e);
                }
            }
        }

        if (h.menu().equals(UPGRADES)) fillUpgrades(p, d, h, c, vars);
        else fillShop(p, d, h, c, vars);

        ConfigurationSection filler = c.getConfigurationSection("filler");
        if (filler != null) {
            ItemStack f = ItemFactory.build(plugin, filler, vars);
            for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, f.clone());
        }
    }

    private void fillUpgrades(Player p, PlayerData d, MenuHolder h, YamlConfiguration c, Map<String, String> base) {
        ConfigurationSection ups = c.getConfigurationSection("ameliorations");
        if (ups == null) return;
        Upgrades u = plugin.upgrades();
        for (String key : ups.getKeys(false)) {
            String type = Upgrades.normalize(key);
            ConfigurationSection s = ups.getConfigurationSection(key);
            if (type == null || s == null) continue;
            int lvl = u.clamp(type, d.niveau(type));
            int max = u.maxLevel(type);
            boolean isMax = lvl >= max;
            long cost = isMax ? 0 : u.cost(type, lvl + 1);
            String perm = isMax ? null : u.permission(type, lvl + 1);
            long missing = Math.max(0, cost - d.minecoins);

            Map<String, String> v = new java.util.HashMap<>(base);
            v.put("nom", u.name(type));
            v.put("niveau", String.valueOf(lvl));
            v.put("niveau_max", String.valueOf(max));
            v.put("valeur", u.value(type, lvl));
            v.put("valeur_suivante", isMax ? "-" : u.value(type, lvl + 1));
            v.put("cout", isMax ? "-" : Text.number(cost));
            v.put("manque", Text.number(missing));
            String status;
            if (isMax) status = c.getString("statuts.max", "");
            else if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) status = c.getString("statuts.permission", "");
            else if (missing > 0) status = c.getString("statuts.trop-cher", "");
            else status = c.getString("statuts.achetable", "");
            v.put("statut", Text.replace(status, v));

            ItemStack st = ItemFactory.build(plugin, s, v);
            MenuHolder.Entry e = new MenuHolder.Entry("UPGRADE", type, List.of());
            for (int slot : ItemFactory.slots(s, h.getInventory().getSize())) {
                h.getInventory().setItem(slot, st.clone());
                h.entries().put(slot, e);
            }
        }
    }

    private void fillShop(Player p, PlayerData d, MenuHolder h, YamlConfiguration c, Map<String, String> base) {
        ConfigurationSection offers = c.getConfigurationSection("offres");
        if (offers == null) return;
        for (String key : offers.getKeys(false)) {
            ConfigurationSection s = offers.getConfigurationSection(key);
            if (s == null) continue;
            long price = s.getLong("prix", 0);
            long missing = Math.max(0, price - d.cristaux);
            String perm = s.getString("permission");
            Map<String, String> v = new java.util.HashMap<>(base);
            v.put("prix", Text.number(price));
            v.put("manque", Text.number(missing));
            v.put("objet", s.getString("objet", key));
            String status;
            if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) status = c.getString("statuts.permission", "");
            else if (missing > 0) status = c.getString("statuts.trop-cher", "");
            else status = c.getString("statuts.achetable", "");
            v.put("statut", Text.replace(status, v));
            ItemStack st = ItemFactory.build(plugin, s, v);
            MenuHolder.Entry e = new MenuHolder.Entry("BUY", key, List.of());
            for (int slot : ItemFactory.slots(s, h.getInventory().getSize())) {
                h.getInventory().setItem(slot, st.clone());
                h.entries().put(slot, e);
            }
        }
    }

    // ------------------------------------------------------------------ actions

    void click(Player p, MenuHolder h, MenuHolder.Entry e) {
        for (String cmd : e.commands()) runCommand(p, cmd);
        switch (e.action()) {
            case "CLOSE" -> p.closeInventory();
            case "UPGRADE" -> {
                buyUpgrade(p, e.arg());
                refresh(p, h);
            }
            case "BUY" -> {
                buyOffer(p, e.arg());
                if (p.getOpenInventory().getTopInventory().getHolder() == h) refresh(p, h);
            }
            default -> {
            }
        }
    }

    private void runCommand(Player p, String raw) {
        String cmd = raw.replace("{joueur}", p.getName()).trim();
        if (cmd.toLowerCase(Locale.ROOT).startsWith("[player]")) {
            p.performCommand(cmd.substring(8).trim().replaceFirst("^/", ""));
        } else {
            if (cmd.toLowerCase(Locale.ROOT).startsWith("[console]")) cmd = cmd.substring(9).trim();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replaceFirst("^/", ""));
        }
    }

    public void buyUpgrade(Player p, String type) {
        Upgrades u = plugin.upgrades();
        PlayerData d = plugin.data().get(p);
        int lvl = u.clamp(type, d.niveau(type));
        String sound = plugin.configs().main().getString("sons.erreur", "");
        if (lvl >= u.maxLevel(type)) {
            plugin.messages().send(p, "amelioration-max", Text.vars("amelioration", u.name(type)));
            Text.sound(p, sound);
            return;
        }
        int next = lvl + 1;
        String perm = u.permission(type, next);
        if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) {
            plugin.messages().send(p, "amelioration-permission", null);
            Text.sound(p, sound);
            return;
        }
        long cost = u.cost(type, next);
        if (d.minecoins < cost) {
            plugin.messages().send(p, "pas-assez-minecoins", Text.vars("manque", Text.number(cost - d.minecoins)));
            Text.sound(p, sound);
            return;
        }
        d.minecoins -= cost;
        d.niveaux.put(type, next);
        plugin.data().saveAsync(d);
        if (plugin.isMineWorld(p.getWorld()) && d.dansMine) plugin.pickaxes().give(p);
        plugin.messages().send(p, "amelioration-achetee", Text.vars("amelioration", u.name(type), "niveau", String.valueOf(next)));
        Text.sound(p, plugin.configs().main().getString("sons.amelioration", ""));
        plugin.logs().log("ameliorations", p.getName() + " : " + type + " niveau " + next + " (-" + cost
                + " MineCoins, reste " + d.minecoins + ")");
    }

    public void buyOffer(Player p, String key) {
        if (shopForbidden(p)) {
            plugin.messages().send(p, "boutique-interdite", null);
            return;
        }
        ConfigurationSection s = cfg(SHOP).getConfigurationSection("offres." + key);
        if (s == null) return;
        PlayerData d = plugin.data().get(p);
        String sound = plugin.configs().main().getString("sons.erreur", "");
        String perm = s.getString("permission");
        if (perm != null && !perm.isBlank() && !p.hasPermission(perm)) {
            plugin.messages().send(p, "pas-de-permission", null);
            Text.sound(p, sound);
            return;
        }
        long price = s.getLong("prix", 0);
        if (d.cristaux < price) {
            plugin.messages().send(p, "pas-assez-cristaux", Text.vars("manque", Text.number(price - d.cristaux)));
            Text.sound(p, sound);
            return;
        }
        d.cristaux -= price;
        plugin.data().saveAsync(d);
        for (String cmd : s.getStringList("commandes")) runCommand(p, cmd);
        String objet = s.getString("objet", key);
        plugin.messages().send(p, "achat-effectue", Text.vars("objet", objet, "prix", Text.number(price)));
        Text.sound(p, plugin.configs().main().getString("sons.achat", ""));
        plugin.logs().log("achats", p.getName() + " achète '" + objet + "' (" + key + ") pour " + price
                + " Cristaux (reste " + d.cristaux + ")");
    }
}
