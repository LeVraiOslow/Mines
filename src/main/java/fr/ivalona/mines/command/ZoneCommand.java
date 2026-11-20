package fr.ivalona.mines.command;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.util.Text;
import fr.ivalona.mines.zone.BlockPattern;
import fr.ivalona.mines.zone.Zone;
import fr.ivalona.mines.zone.ZoneManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /ivalonazone : sélection et gestion des zones de mine. */
public final class ZoneCommand implements TabExecutor, Listener {

    private static final List<String> SUBS = List.of("baguette", "pos1", "pos2", "create", "generate", "regen",
            "intervalle", "preremplir", "delete", "list", "info");

    private final IvalonaMines plugin;
    private final NamespacedKey wandKey;

    public ZoneCommand(IvalonaMines plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "baguette");
    }

    private void msg(CommandSender s, String key, String... vars) {
        plugin.messages().send(s, key, Text.vars(vars));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !SUBS.contains(args[0].toLowerCase(Locale.ROOT))) {
            msg(sender, "zone-aide");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        ZoneManager zm = plugin.zones();
        switch (sub) {
            case "baguette" -> {
                if (!(sender instanceof Player p)) return player(sender);
                p.getInventory().addItem(wand());
                msg(p, "zone-baguette");
            }
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player p)) return player(sender);
                Location loc = p.getLocation().getBlock().getLocation();
                if (args.length >= 3) {
                    try {
                        loc = new Location(p.getWorld(), Integer.parseInt(args[1]), loc.getY(), Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        msg(p, "nombre-invalide", "valeur", args[1] + " " + args[2]);
                        return true;
                    }
                }
                setPos(p, sub.equals("pos1") ? 0 : 1, loc);
            }
            case "create" -> {
                if (!(sender instanceof Player p)) return player(sender);
                if (args.length < 2) {
                    msg(p, "zone-aide");
                    return true;
                }
                String name = args[1].toLowerCase(Locale.ROOT);
                if (!ZoneManager.NAME.matcher(name).matches()) {
                    msg(p, "zone-motif-invalide", "erreur", "nom : lettres minuscules, chiffres, _ et - uniquement");
                    return true;
                }
                if (zm.get(name) != null) {
                    msg(p, "zone-existe", "zone", name);
                    return true;
                }
                Location[] sel = zm.selection(p.getUniqueId());
                if (sel == null || sel[0] == null || sel[1] == null || sel[0].getWorld() == null
                        || !sel[0].getWorld().equals(sel[1].getWorld())) {
                    msg(p, "zone-selection-incomplete");
                    return true;
                }
                World w = sel[0].getWorld();
                int yMin = w.getMinHeight(), yMax = w.getMaxHeight() - 1;
                if (args.length >= 4) {
                    try {
                        yMin = Math.max(w.getMinHeight(), Integer.parseInt(args[2]));
                        yMax = Math.min(w.getMaxHeight() - 1, Integer.parseInt(args[3]));
                    } catch (NumberFormatException e) {
                        msg(p, "nombre-invalide", "valeur", args[2] + " " + args[3]);
                        return true;
                    }
                }
                Zone z = zm.create(name, w, sel[0].getBlockX(), sel[0].getBlockZ(), sel[1].getBlockX(), sel[1].getBlockZ(), yMin, yMax);
                msg(p, "zone-creee", "zone", z.name, "taille_x", Text.number(z.sizeX()), "taille_z", Text.number(z.sizeZ()),
                        "ymin", String.valueOf(z.yMin), "ymax", String.valueOf(z.yMax));
                plugin.logs().log("zones", sender.getName() + " crée la zone " + z.name + " (" + z.world + " " + z.x1 + "," + z.z1
                        + " -> " + z.x2 + "," + z.z2 + ", Y " + z.yMin + "->" + z.yMax + ")");
            }
            case "generate" -> {
                if (args.length < 3) {
                    msg(sender, "zone-aide");
                    return true;
                }
                Zone z = zm.get(args[1]);
                if (z == null) {
                    msg(sender, "zone-introuvable", "zone", args[1]);
                    return true;
                }
                String motif = String.join("", Arrays.copyOfRange(args, 2, args.length));
                BlockPattern pattern;
                try {
                    pattern = BlockPattern.parse(motif);
                } catch (IllegalArgumentException e) {
                    msg(sender, "zone-motif-invalide", "erreur", e.getMessage());
                    return true;
                }
                if (Math.abs(pattern.total() - 100) > 0.001) {
                    msg(sender, "zone-motif-normalise", "total", trim(pattern.total()));
                }
                z.pattern = pattern;
                plugin.regen().regenerate(z, "commande generate de " + sender.getName());
                msg(sender, "zone-generee", "zone", z.name, "motif", pattern.display());
                plugin.logs().log("zones", sender.getName() + " définit les blocs de " + z.name + " : " + pattern.display());
            }
            case "regen" -> {
                Zone z = zone(sender, args);
                if (z == null) return true;
                if (z.pattern == null) {
                    msg(sender, "zone-motif-invalide", "erreur", "aucun bloc défini (/ivalonazone generate)");
                    return true;
                }
                plugin.regen().regenerate(z, "commande regen de " + sender.getName());
                msg(sender, "fait");
            }
            case "intervalle" -> {
                Zone z = zone(sender, args);
                if (z == null) return true;
                if (args.length < 3) {
                    msg(sender, "zone-aide");
                    return true;
                }
                try {
                    z.intervalMinutes = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    msg(sender, "nombre-invalide", "valeur", args[2]);
                    return true;
                }
                zm.save(z);
                msg(sender, "zone-intervalle", "zone", z.name, "minutes", String.valueOf(z.intervalMinutes));
                plugin.logs().log("zones", sender.getName() + " : intervalle de " + z.name + " = " + z.intervalMinutes + " min");
            }
            case "preremplir" -> {
                if (!(sender instanceof Player p)) return player(sender);
                Zone z = zone(sender, args);
                if (z == null) return true;
                if (z.pattern == null) {
                    msg(sender, "zone-motif-invalide", "erreur", "aucun bloc défini (/ivalonazone generate)");
                    return true;
                }
                if (!p.getWorld().getName().equalsIgnoreCase(z.world)) {
                    msg(sender, "zone-pas-dans-monde", "zone", z.name, "monde", z.world);
                    return true;
                }
                int radius = 10;
                if (args.length >= 3) {
                    try {
                        radius = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
                    } catch (NumberFormatException e) {
                        msg(sender, "nombre-invalide", "valeur", args[2]);
                        return true;
                    }
                }
                int n = plugin.regen().prefill(z, p.getLocation().getBlockX(), p.getLocation().getBlockZ(), radius);
                msg(sender, "zone-preremplissage", "zone", z.name, "chunks", Text.number(n), "rayon", String.valueOf(radius));
            }
            case "delete" -> {
                Zone z = zone(sender, args);
                if (z == null) return true;
                plugin.regen().forget(z);
                zm.delete(z.name);
                msg(sender, "zone-supprimee", "zone", z.name);
                plugin.logs().log("zones", sender.getName() + " supprime la zone " + z.name);
            }
            case "list" -> {
                if (zm.names().isEmpty()) msg(sender, "zone-liste-vide");
                else msg(sender, "zone-liste", "zones", String.join(", ", zm.names()));
            }
            case "info" -> {
                Zone z = zone(sender, args);
                if (z == null) return true;
                long next = (z.nextRegen() - System.currentTimeMillis()) / 1000;
                msg(sender, "zone-info", "zone", z.name, "monde", z.world,
                        "x1", String.valueOf(z.x1), "x2", String.valueOf(z.x2), "z1", String.valueOf(z.z1), "z2", String.valueOf(z.z2),
                        "ymin", String.valueOf(z.yMin), "ymax", String.valueOf(z.yMax),
                        "taille_x", Text.number(z.sizeX()), "taille_z", Text.number(z.sizeZ()),
                        "motif", z.pattern == null ? "aucun" : z.pattern.display(),
                        "minutes", String.valueOf(z.intervalMinutes),
                        "prochaine", z.pattern == null ? "-" : Text.duration(next),
                        "attente", Text.number(plugin.regen().pending(z)));
            }
            default -> msg(sender, "zone-aide");
        }
        return true;
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    private boolean player(CommandSender s) {
        msg(s, "joueur-seulement");
        return true;
    }

    private Zone zone(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "zone-aide");
            return null;
        }
        Zone z = plugin.zones().get(args[1]);
        if (z == null) msg(s, "zone-introuvable", "zone", args[1]);
        return z;
    }

    private void setPos(Player p, int index, Location loc) {
        plugin.zones().setPos(p.getUniqueId(), index, loc);
        msg(p, "zone-pos", "numero", String.valueOf(index + 1), "x", String.valueOf(loc.getBlockX()),
                "z", String.valueOf(loc.getBlockZ()), "monde", loc.getWorld() == null ? "?" : loc.getWorld().getName());
    }

    // ------------------------------------------------------------------ baguette

    private ItemStack wand() {
        Material m = Material.matchMaterial(plugin.configs().main().getString("selection.baguette", "GOLDEN_AXE"));
        ItemStack it = new ItemStack(m == null || !m.isItem() ? Material.GOLDEN_AXE : m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.color("&b&lBaguette de zone"));
        meta.lore(List.of(Text.color("&7Clic gauche : &fposition 1"), Text.color("&7Clic droit : &fposition 2")));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta() || !it.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("ivalonamines.admin.zone")) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) setPos(p, 0, e.getClickedBlock().getLocation());
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) setPos(p, 1, e.getClickedBlock().getLocation());
        else return;
        e.setCancelled(true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && List.of("generate", "regen", "intervalle", "preremplir", "delete", "info").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (String n : plugin.zones().names()) if (n.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(n);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("generate")) {
            out.add("60%stone,20%gold_ore,20%grass");
        }
        return out;
    }
}
