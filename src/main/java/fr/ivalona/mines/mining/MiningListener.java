package fr.ivalona.mines.mining;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import fr.ivalona.mines.pickaxe.Upgrades;
import fr.ivalona.mines.util.Text;
import fr.ivalona.mines.zone.Zone;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Minage dans les zones : taille x profondeur, MineCoins et Cristaux. */
public final class MiningListener implements Listener {

    private final IvalonaMines plugin;
    /** face du bloc tapée en dernier (sert à savoir dans quelle direction le joueur mine) */
    private final Map<UUID, BlockFace> lastFace = new HashMap<>();

    public MiningListener(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getBlockFace() != null) {
            lastFace.put(e.getPlayer().getUniqueId(), e.getBlockFace());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastFace.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!plugin.isMineWorld(b.getWorld())) return;
        Zone zone = plugin.zones().at(b.getWorld(), b.getX(), b.getY(), b.getZ());
        boolean builder = p.hasPermission("ivalonamines.admin.build");

        if (zone == null) {
            if (!builder && !plugin.configs().main().getBoolean("protection.casser-hors-zone", false)) {
                e.setCancelled(true);
                plugin.messages().actionBar(p, "casser-interdit", null);
            }
            return;
        }
        if (!plugin.pickaxes().isPickaxe(p.getInventory().getItemInMainHand())) {
            if (!builder) e.setCancelled(true);
            return;
        }
        Material floor = floor();
        if (floor != null && b.getY() == zone.yMin && b.getType() == floor) {
            e.setCancelled(true);
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);

        PlayerData d = plugin.data().get(p);
        Upgrades u = plugin.upgrades();
        List<Block> extra = area(p, b, zone, floor,
                u.width(d.niveau(Upgrades.TAILLE)), u.height(d.niveau(Upgrades.TAILLE)),
                u.depth(d.niveau(Upgrades.PROFONDEUR)));

        // récompenses
        double coinMult = u.coinMultiplier(d.niveau(Upgrades.RECOMPENSES));
        double crystalMult = u.crystalMultiplier(d.niveau(Upgrades.RECOMPENSES));
        double coins = 0;
        long crystals = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        BlockRewards.Reward r = plugin.rewards().get(b.getType());
        coins += r.coins();
        if (r.crystalChance() > 0 && rnd.nextDouble() * 100 < r.crystalChance() * crystalMult) crystals += r.crystals();
        for (Block x : extra) {
            BlockRewards.Reward rx = plugin.rewards().get(x.getType());
            coins += rx.coins();
            if (rx.crystalChance() > 0 && rnd.nextDouble() * 100 < rx.crystalChance() * crystalMult) crystals += rx.crystals();
            x.setType(Material.AIR, false);
        }
        long gained = Math.round(coins * coinMult);
        d.minecoins += gained;
        d.cristaux += crystals;
        d.markDirty();

        if (gained > 0 && plugin.configs().main().getBoolean("minage.barre-action", true)) {
            plugin.messages().actionBar(p, "barre-action", Text.vars("minecoins", Text.number(gained),
                    "total_minecoins", Text.number(d.minecoins), "total_cristaux", Text.number(d.cristaux)));
        }
        if (crystals > 0) {
            plugin.messages().send(p, "cristal-gagne", Text.vars("cristaux", Text.number(crystals),
                    "total_cristaux", Text.number(d.cristaux)));
            Text.sound(p, plugin.configs().main().getString("sons.cristal", ""));
        }
    }

    private Material floor() {
        String s = plugin.configs().main().getString("regeneration.sol", "");
        if (s == null || s.isBlank()) return null;
        return Material.matchMaterial(s.trim());
    }

    /**
     * Blocs à casser en plus du bloc d'origine : un rectangle largeur x hauteur perpendiculaire à la
     * direction de minage, répété sur "profondeur" blocs devant le joueur.
     */
    private List<Block> area(Player p, Block origin, Zone zone, Material floor, int width, int height, int depth) {
        List<Block> out = new ArrayList<>();
        if (width <= 1 && height <= 1 && depth <= 1) return out;
        BlockFace face = lastFace.get(p.getUniqueId());
        if (face == null || !face.isCartesian()) face = facing(p.getLocation().getDirection()).getOppositeFace();
        BlockFace dir = face.getOppositeFace(); // on creuse vers l'intérieur du bloc tapé

        // axes du rectangle (u = largeur, v = hauteur)
        int[] du, dv;
        int[] dd = {dir.getModX(), dir.getModY(), dir.getModZ()};
        if (dir.getModY() != 0) {           // vers le haut / le bas
            du = new int[]{1, 0, 0};
            dv = new int[]{0, 0, 1};
        } else if (dir.getModX() != 0) {    // vers l'est / l'ouest
            du = new int[]{0, 0, 1};
            dv = new int[]{0, 1, 0};
        } else {                            // vers le nord / le sud
            du = new int[]{1, 0, 0};
            dv = new int[]{0, 1, 0};
        }
        int max = Math.max(1, plugin.configs().main().getInt("minage.blocs-max-par-coup", 1500));
        World w = origin.getWorld();
        int u0 = -(width - 1) / 2, u1 = width / 2;
        int v0 = -(height - 1) / 2, v1 = height / 2;
        for (int k = 0; k < depth; k++) {
            for (int i = u0; i <= u1; i++) {
                for (int j = v0; j <= v1; j++) {
                    if (k == 0 && i == 0 && j == 0) continue; // bloc d'origine (cassé par le jeu)
                    int x = origin.getX() + dd[0] * k + du[0] * i + dv[0] * j;
                    int y = origin.getY() + dd[1] * k + du[1] * i + dv[1] * j;
                    int z = origin.getZ() + dd[2] * k + du[2] * i + dv[2] * j;
                    if (!zone.contains(w, x, y, z)) continue;
                    Block bl = w.getBlockAt(x, y, z);
                    Material m = bl.getType();
                    if (m.isAir() || m == Material.BEDROCK || (floor != null && y == zone.yMin && m == floor)) continue;
                    out.add(bl);
                    if (out.size() >= max) return out;
                }
            }
        }
        return out;
    }

    private static BlockFace facing(Vector v) {
        double ax = Math.abs(v.getX()), ay = Math.abs(v.getY()), az = Math.abs(v.getZ());
        if (ay >= ax && ay >= az) return v.getY() > 0 ? BlockFace.UP : BlockFace.DOWN;
        if (ax >= az) return v.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        return v.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Clic droit avec la pioche : menu d'amélioration. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.pickaxes().isPickaxe(e.getItem())) return;
        e.setCancelled(true);
        plugin.menus().openUpgrades(e.getPlayer());
    }
}
