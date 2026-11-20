package fr.ivalona.mines.zone;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Régénération des zones, chunk par chunk.
 *
 * Une zone de 5000x5000 sur toute la hauteur fait ~9,6 milliards de blocs : impossible de tout reposer d'un coup.
 * Chaque régénération augmente le numéro de "génération" de la zone ; un chunk dont le numéro enregistré
 * (dans le chunk lui-même) est plus ancien est rempli à nouveau :
 *  - tout de suite pour les chunks chargés (les plus proches des joueurs d'abord) ;
 *  - à son chargement pour les autres (quand un joueur s'en approche).
 * Le remplissage est étalé sur plusieurs ticks avec un temps de calcul maximum par tick (budget-ms-par-tick).
 */
public final class RegenManager implements Listener {

    private static final class Job {
        final Zone zone;
        final int cx, cz;
        int epoch;
        int col = -1; // colonne en cours (index dans le chunk), -1 = pas commencé
        int x0, x1, z0, z1;
        boolean ticket; // chunk maintenu chargé par le pré-remplissage
        SplittableRandom random;

        Job(Zone zone, int cx, int cz) {
            this.zone = zone;
            this.cx = cx;
            this.cz = cz;
        }

        long key() {
            return ((long) cx << 32) ^ (cz & 0xffffffffL);
        }
    }

    private final IvalonaMines plugin;
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Map<String, Set<Long>> queued = new HashMap<>();
    private final Map<String, Integer> pendingPerZone = new HashMap<>();
    private final Set<String> announceEnd = new HashSet<>();
    private Job current;

    /** pré-remplissage en cours : chunks restants à charger */
    private final Deque<long[]> prefill = new ArrayDeque<>();
    private Zone prefillZone;
    private int prefillTickets;

    public RegenManager(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(Zone z) {
        return new NamespacedKey(plugin, "generation_" + z.name);
    }

    public int pending(Zone z) {
        return pendingPerZone.getOrDefault(z.name, 0);
    }

    // ------------------------------------------------------------------ régénération

    /** Lance une nouvelle génération de la zone (nouveaux blocs aléatoires). */
    public void regenerate(Zone z, String cause) {
        if (z.pattern == null) return;
        z.epoch++;
        z.lastRegen = System.currentTimeMillis();
        z.lastWarning = Integer.MAX_VALUE;
        plugin.zones().save(z);
        plugin.logs().log("zones", "Régénération de " + z.name + " (génération " + z.epoch + ", " + cause + ")");

        World w = Bukkit.getWorld(z.world);
        if (w == null) return;

        // joueurs dans la zone -> spawn de la mine
        if (plugin.configs().main().getBoolean("regeneration.teleporter-joueurs", true)) {
            Location spawn = plugin.mineSpawn();
            for (Player p : w.getPlayers()) {
                if (z.containsColumn(p.getLocation()) && p.getLocation().getY() >= z.yMin - 2) {
                    if (spawn != null && !z.contains(spawn)) p.teleport(spawn);
                }
            }
        }
        plugin.messages().broadcastWorld(z.world, "regen-debut", Text.vars("zone", z.name));
        for (Player p : w.getPlayers()) Text.sound(p, plugin.configs().main().getString("sons.regeneration", ""));

        // chunks chargés : les plus proches des joueurs d'abord
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk c : w.getLoadedChunks()) if (z.intersectsChunk(w.getName(), c.getX(), c.getZ())) chunks.add(c);
        List<Player> players = w.getPlayers();
        chunks.sort(Comparator.comparingDouble(c -> nearestPlayer(players, c)));
        List<Job> jobs = new ArrayList<>();
        for (Chunk c : chunks) jobs.add(new Job(z, c.getX(), c.getZ()));
        // en tête de file, dans l'ordre de proximité
        for (int i = jobs.size() - 1; i >= 0; i--) enqueue(jobs.get(i), true);
        if (!jobs.isEmpty()) announceEnd.add(z.name);
    }

    private static double nearestPlayer(List<Player> players, Chunk c) {
        double cxm = (c.getX() << 4) + 8, czm = (c.getZ() << 4) + 8, best = Double.MAX_VALUE;
        for (Player p : players) {
            double dx = p.getLocation().getX() - cxm, dz = p.getLocation().getZ() - czm;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private void enqueue(Job j, boolean front) {
        Set<Long> set = queued.computeIfAbsent(j.zone.name, k -> new HashSet<>());
        if (!set.add(j.key())) return;
        if (front) queue.addFirst(j);
        else queue.addLast(j);
        pendingPerZone.merge(j.zone.name, 1, Integer::sum);
    }

    private void done(Job j) {
        if (j.ticket) {
            World w = Bukkit.getWorld(j.zone.world);
            if (w != null) w.removePluginChunkTicket(j.cx, j.cz, plugin);
            prefillTickets = Math.max(0, prefillTickets - 1);
        }
        Set<Long> set = queued.get(j.zone.name);
        if (set != null) set.remove(j.key());
        int left = pendingPerZone.merge(j.zone.name, -1, Integer::sum);
        if (left <= 0) {
            pendingPerZone.remove(j.zone.name);
            if (announceEnd.remove(j.zone.name)) {
                plugin.messages().broadcastWorld(j.zone.world, "regen-fin", Text.vars("zone", j.zone.name));
                plugin.logs().log("zones", "Zone " + j.zone.name + " régénérée autour des joueurs.");
            }
        }
    }

    /** Un chunk qui se charge avec une génération plus ancienne est refait. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk c = e.getChunk();
        String w = c.getWorld().getName();
        for (Zone z : plugin.zones().all()) {
            if (z.pattern == null || !z.intersectsChunk(w, c.getX(), c.getZ())) continue;
            Integer stored = c.getPersistentDataContainer().get(key(z), PersistentDataType.INTEGER);
            if (stored == null || stored != z.epoch) enqueue(new Job(z, c.getX(), c.getZ()), false);
        }
    }

    // ------------------------------------------------------------------ travail par tick

    public void tick() {
        feedPrefill();
        long budgetNs = Math.max(1, plugin.configs().main().getInt("regeneration.budget-ms-par-tick", 12)) * 1_000_000L;
        long deadline = System.nanoTime() + budgetNs;
        while (System.nanoTime() < deadline) {
            if (current == null) {
                current = pollNearest();
                if (current == null) return;
                if (!start(current)) {
                    done(current);
                    current = null;
                    continue;
                }
            }
            World w = Bukkit.getWorld(current.zone.world);
            if (w == null || !w.isChunkLoaded(current.cx, current.cz) || plugin.zones().get(current.zone.name) != current.zone) {
                // chunk déchargé : il sera refait à son prochain chargement
                done(current);
                current = null;
                continue;
            }
            Chunk chunk = w.getChunkAt(current.cx, current.cz);
            if (fillColumns(chunk, current, deadline)) {
                chunk.getPersistentDataContainer().set(key(current.zone), PersistentDataType.INTEGER, current.epoch);
                done(current);
                current = null;
            }
        }
    }

    /** Parmi les premiers chunks en attente, prend le plus proche d'un joueur. */
    private Job pollNearest() {
        if (queue.isEmpty()) return null;
        Map<String, List<Player>> byWorld = new HashMap<>();
        Job best = null;
        double bestDist = Double.MAX_VALUE;
        int scanned = 0;
        for (Job j : queue) {
            if (scanned++ >= 512) break;
            List<Player> players = byWorld.computeIfAbsent(j.zone.world, w -> {
                World world = Bukkit.getWorld(w);
                return world == null ? List.of() : world.getPlayers();
            });
            double cxm = (j.cx << 4) + 8, czm = (j.cz << 4) + 8, dist = Double.MAX_VALUE;
            for (Player p : players) {
                double dx = p.getLocation().getX() - cxm, dz = p.getLocation().getZ() - czm;
                dist = Math.min(dist, dx * dx + dz * dz);
            }
            if (best == null || dist < bestDist) {
                best = j;
                bestDist = dist;
            }
        }
        queue.remove(best);
        return best;
    }

    // ------------------------------------------------------------------ pré-remplissage

    /** Charge et remplit à l'avance les chunks dans un rayon (en chunks) autour d'un point. */
    public int prefill(Zone z, int centerX, int centerZ, int radius) {
        prefill.clear();
        prefillZone = z;
        int ccx = centerX >> 4, ccz = centerZ >> 4;
        List<long[]> list = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (z.intersectsChunk(z.world, ccx + dx, ccz + dz)) list.add(new long[]{ccx + dx, ccz + dz, (long) dx * dx + (long) dz * dz});
            }
        }
        list.sort(Comparator.comparingLong(a -> a[2]));
        prefill.addAll(list);
        plugin.logs().log("zones", "Pré-remplissage de " + z.name + " : " + list.size() + " chunk(s) autour de " + centerX + ", " + centerZ);
        return list.size();
    }

    public int prefillRemaining() {
        return prefill.size() + prefillTickets;
    }

    /** Demande quelques chargements de chunks par tick (jamais plus de 64 chunks maintenus en même temps). */
    private void feedPrefill() {
        if (prefill.isEmpty() || prefillZone == null) return;
        World w = Bukkit.getWorld(prefillZone.world);
        if (w == null || plugin.zones().get(prefillZone.name) != prefillZone) {
            prefill.clear();
            return;
        }
        int asked = 0;
        while (!prefill.isEmpty() && prefillTickets < 64 && asked < 4) {
            long[] c = prefill.pollFirst();
            int cx = (int) c[0], cz = (int) c[1];
            Zone z = prefillZone;
            prefillTickets++;
            asked++;
            w.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                Integer stored = chunk.getPersistentDataContainer().get(key(z), PersistentDataType.INTEGER);
                if (stored != null && stored == z.epoch) {
                    prefillTickets = Math.max(0, prefillTickets - 1);
                    return;
                }
                w.addPluginChunkTicket(cx, cz, plugin);
                Set<Long> set = queued.get(z.name);
                Job j = new Job(z, cx, cz);
                j.ticket = true;
                if (set != null && set.contains(j.key())) {
                    // déjà en file (chargement normal) : on marque juste le ticket sur ce job
                    for (Job q : queue) if (q.zone == z && q.cx == cx && q.cz == cz) q.ticket = true;
                    return;
                }
                enqueue(j, false);
            });
        }
        if (prefill.isEmpty() && prefillTickets == 0) prefillZone = null;
    }

    private boolean start(Job j) {
        Zone z = j.zone;
        if (z.pattern == null) return false;
        int bx = j.cx << 4, bz = j.cz << 4;
        j.x0 = Math.max(bx, z.x1) - bx;
        j.x1 = Math.min(bx + 15, z.x2) - bx;
        j.z0 = Math.max(bz, z.z1) - bz;
        j.z1 = Math.min(bz + 15, z.z2) - bz;
        if (j.x0 > j.x1 || j.z0 > j.z1) return false;
        j.epoch = z.epoch;
        long mix = z.seed ^ (z.epoch * 0x9E3779B97F4A7C15L) ^ (j.key() * 0xBF58476D1CE4E5B9L);
        j.random = new SplittableRandom(mix);
        j.col = 0;
        return true;
    }

    /** Remplit des colonnes jusqu'à la fin du chunk ou du budget. @return true si le chunk est terminé */
    private boolean fillColumns(Chunk chunk, Job j, long deadline) {
        Zone z = j.zone;
        World w = chunk.getWorld();
        int yMin = Math.max(z.yMin, w.getMinHeight());
        int yMax = Math.min(z.yMax, w.getMaxHeight() - 1);
        BlockData floor = floor();
        int width = j.x1 - j.x0 + 1;
        int total = width * (j.z1 - j.z0 + 1);
        while (j.col < total) {
            int lx = j.x0 + (j.col % width);
            int lz = j.z0 + (j.col / width);
            for (int y = yMin; y <= yMax; y++) {
                BlockData data = (y == z.yMin && floor != null) ? floor : z.pattern.pick(j.random);
                chunk.getBlock(lx, y, lz).setBlockData(data, false);
            }
            j.col++;
            if (System.nanoTime() >= deadline) return j.col >= total;
        }
        return true;
    }

    private BlockData floor() {
        String s = plugin.configs().main().getString("regeneration.sol", "");
        if (s == null || s.isBlank()) return null;
        Material m = Material.matchMaterial(s.trim());
        return m != null && m.isBlock() ? m.createBlockData() : null;
    }

    // ------------------------------------------------------------------ minuterie (chaque seconde)

    public void timer() {
        long now = System.currentTimeMillis();
        List<Integer> warnings = plugin.configs().main().getIntegerList("regeneration.avertissements-minutes");
        for (Zone z : plugin.zones().all()) {
            if (z.pattern == null) continue;
            long left = z.nextRegen() - now;
            if (left <= 0) {
                regenerate(z, "automatique");
                continue;
            }
            for (int m : warnings) {
                if (m > 0 && left <= m * 60_000L && m < z.lastWarning) {
                    z.lastWarning = m;
                    plugin.messages().broadcastWorld(z.world, "regen-avertissement",
                            Text.vars("zone", z.name, "minutes", String.valueOf(m)));
                    break;
                }
            }
        }
    }

    /** Zone supprimée : retire ses chunks de la file. */
    public void forget(Zone z) {
        if (prefillZone == z) {
            prefill.clear();
            prefillZone = null;
        }
        World w = Bukkit.getWorld(z.world);
        for (Job j : queue) if (j.zone == z && j.ticket && w != null) w.removePluginChunkTicket(j.cx, j.cz, plugin);
        queue.removeIf(j -> j.zone == z);
        if (current != null && current.zone == z) current = null;
        queued.remove(z.name);
        pendingPerZone.remove(z.name);
        announceEnd.remove(z.name);
    }
}
