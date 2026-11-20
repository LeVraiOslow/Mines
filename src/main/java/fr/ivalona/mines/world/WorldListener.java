package fr.ivalona.mines.world;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Entrée / sortie de la mine : accès, échange d'inventaire, mort, protections. */
public final class WorldListener implements Listener {

    private final IvalonaMines plugin;

    public WorldListener(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    private boolean hasAccess(Player p) {
        return p.hasPermission(plugin.configs().main().getString("acces.permission", "ivalonamines.acces"));
    }

    private static boolean bypass(Player p) {
        return p.hasPermission("ivalonamines.bypass.inventaire");
    }

    // ------------------------------------------------------------------ entrée / sortie

    /** Le joueur arrive dans la mine : sauvegarde de l'inventaire, inventaire vidé, pioche donnée. */
    public void enter(Player p) {
        if (bypass(p)) return;
        PlayerData d = plugin.data().get(p);
        if (!d.dansMine) {
            ItemStack[] contents = p.getInventory().getContents();
            ItemStack[] copy = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
            d.inventaireSauvegarde = copy;
            boolean xp = plugin.configs().main().getBoolean("inventaire.sauvegarder-experience", true);
            if (xp) {
                d.niveauXpSauvegarde = p.getLevel();
                d.xpSauvegardee = p.getExp();
            }
            d.dansMine = true;
            plugin.data().saveNow(d); // sur le disque AVANT de vider l'inventaire
            p.getInventory().clear();
            p.setItemOnCursor(null);
            if (xp) {
                p.setLevel(0);
                p.setExp(0);
            }
            plugin.messages().send(p, "entree", null);
            plugin.logs().log("entrees-sorties", p.getName() + " entre dans la mine (inventaire sauvegardé : "
                    + count(copy) + " stack(s))");
        }
        plugin.pickaxes().give(p);
    }

    /** Le joueur quitte la mine : pioche retirée, inventaire d'avant restauré. */
    public void leave(Player p) {
        PlayerData d = plugin.data().get(p);
        plugin.pickaxes().removeAll(p);
        plugin.pickaxes().clearEffects(p);
        if (!d.dansMine) return;
        ItemStack[] leftovers = null;
        if (plugin.configs().main().getBoolean("inventaire.vider-a-la-sortie", true)) {
            p.getInventory().clear();
        } else {
            leftovers = p.getInventory().getContents();
            p.getInventory().clear();
        }
        if (d.inventaireSauvegarde != null) {
            ItemStack[] saved = d.inventaireSauvegarde;
            ItemStack[] target = new ItemStack[p.getInventory().getSize()];
            for (int i = 0; i < Math.min(saved.length, target.length); i++) target[i] = saved[i];
            p.getInventory().setContents(target);
        }
        if (leftovers != null) {
            for (ItemStack it : leftovers) {
                if (it == null || it.getType().isAir()) continue;
                Map<Integer, ItemStack> over = p.getInventory().addItem(it);
                for (ItemStack o : over.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
            }
        }
        if (plugin.configs().main().getBoolean("inventaire.sauvegarder-experience", true) && d.inventaireSauvegarde != null) {
            p.setLevel(d.niveauXpSauvegarde);
            p.setExp(Math.max(0f, Math.min(1f, d.xpSauvegardee)));
        }
        int restored = count(d.inventaireSauvegarde);
        d.inventaireSauvegarde = null;
        d.dansMine = false;
        plugin.data().saveNow(d);
        plugin.messages().send(p, "sortie", null);
        plugin.logs().log("entrees-sorties", p.getName() + " quitte la mine (inventaire restauré : " + restored + " stack(s))");
    }

    private static int count(ItemStack[] items) {
        int n = 0;
        if (items != null) for (ItemStack i : items) if (i != null && !i.getType().isAir()) n++;
        return n;
    }

    private Location exitLocation() {
        World main = Bukkit.getWorlds().get(0);
        return main.getSpawnLocation();
    }

    // ------------------------------------------------------------------ évènements

    /** Bloque l'accès sans permission (téléportations, portails, /mv tp...). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        boolean toMine = plugin.isMineWorld(e.getTo().getWorld());
        boolean fromMine = e.getFrom().getWorld() != null && plugin.isMineWorld(e.getFrom().getWorld());
        if (toMine && !fromMine && !hasAccess(e.getPlayer())) {
            e.setCancelled(true);
            plugin.messages().send(e.getPlayer(), "acces-refuse", null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean fromMine = plugin.isMineWorld(e.getFrom());
        boolean toMine = plugin.isMineWorld(p.getWorld());
        if (fromMine && !toMine) {
            leave(p);
        } else if (toMine && !fromMine) {
            if (!hasAccess(p)) {
                plugin.messages().send(p, "acces-refuse", null);
                p.teleport(e.getFrom().getSpawnLocation());
                return;
            }
            enter(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // un tick plus tard : les autres plugins (spawn, Multiverse) ont placé le joueur
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            PlayerData d = plugin.data().get(p);
            if (plugin.isMineWorld(p.getWorld())) {
                if (!hasAccess(p)) {
                    plugin.messages().send(p, "acces-refuse", null);
                    p.teleport(exitLocation()); // déclenche la sortie (changement de monde)
                    return;
                }
                enter(p);
            } else if (d.dansMine) {
                leave(p); // déconnecté dans la mine puis replacé ailleurs
            }
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.data().unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!plugin.isMineWorld(p.getWorld())) return;
        if (!plugin.configs().main().getBoolean("protection.garder-inventaire-a-la-mort", true)) {
            e.getDrops().removeIf(plugin.pickaxes()::isPickaxe);
            return;
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!plugin.isMineWorld(p.getWorld())) return;
        Location spawn = plugin.mineSpawn();
        if (spawn != null) e.setRespawnLocation(spawn);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && plugin.isMineWorld(p.getWorld()) && plugin.data().get(p).dansMine) plugin.pickaxes().give(p);
        }, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (plugin.pickaxes().isPickaxe(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            return;
        }
        if (plugin.isMineWorld(p.getWorld()) && !plugin.configs().main().getBoolean("protection.jeter-items", false)
                && !p.hasPermission("ivalonamines.admin.build")) {
            e.setCancelled(true);
            plugin.messages().actionBar(p, "jeter-interdit", null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!plugin.isMineWorld(e.getBlock().getWorld())) return;
        if (plugin.configs().main().getBoolean("protection.poser-blocs", false) || p.hasPermission("ivalonamines.admin.build")) return;
        e.setCancelled(true);
        plugin.messages().actionBar(p, "poser-interdit", null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom().getBlockY() == e.getTo().getBlockY()) return;
        Player p = e.getPlayer();
        if (!plugin.isMineWorld(p.getWorld())) return;
        if (e.getTo().getY() < plugin.configs().main().getInt("protection.hauteur-vide", -80)) {
            Location spawn = plugin.mineSpawn();
            if (spawn == null) return;
            p.setFallDistance(0);
            p.teleport(spawn);
            plugin.messages().send(p, "chute-vide", null);
        }
    }

    /** La pioche ne peut pas être rangée dans un coffre / un autre inventaire. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.PLAYER) return;
        boolean cursorPick = plugin.pickaxes().isPickaxe(e.getCursor());
        boolean currentPick = plugin.pickaxes().isPickaxe(e.getCurrentItem());
        boolean clickTop = e.getClickedInventory() == top;
        if ((cursorPick && clickTop) || (currentPick && e.isShiftClick())) {
            e.setCancelled(true);
            return;
        }
        if (clickTop && e.getHotbarButton() >= 0
                && plugin.pickaxes().isPickaxe(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))) {
            e.setCancelled(true);
        }
    }
}
