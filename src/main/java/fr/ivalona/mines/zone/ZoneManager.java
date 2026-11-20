package fr.ivalona.mines.zone;

import fr.ivalona.mines.IvalonaMines;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Zones (zones/<nom>.yml) et sélections des admins. */
public final class ZoneManager {

    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final IvalonaMines plugin;
    private final File dir;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public ZoneManager(IvalonaMines plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "zones");
    }

    public void load() {
        zones.clear();
        dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String name = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            Zone z = new Zone(name, y.getString("monde", ""), y.getInt("x1"), y.getInt("z1"), y.getInt("x2"),
                    y.getInt("z2"), y.getInt("y-min"), y.getInt("y-max"));
            z.intervalMinutes = Math.max(1, y.getInt("intervalle-minutes", 120));
            z.epoch = y.getInt("generation", 0);
            z.lastRegen = y.getLong("derniere-regeneration", 0);
            z.seed = y.getLong("graine", System.nanoTime());
            String motif = y.getString("motif", "");
            if (motif != null && !motif.isBlank()) {
                try {
                    z.pattern = BlockPattern.parse(motif);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Zone " + name + " : motif invalide (" + e.getMessage() + ")");
                }
            }
            zones.put(name, z);
        }
        plugin.getLogger().info(zones.size() + " zone(s) de mine chargée(s).");
    }

    public void save(Zone z) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Zone de mine " + z.name + " - générée par /ivalonazone, ne pas modifier serveur allumé."));
        y.set("monde", z.world);
        y.set("x1", z.x1);
        y.set("z1", z.z1);
        y.set("x2", z.x2);
        y.set("z2", z.z2);
        y.set("y-min", z.yMin);
        y.set("y-max", z.yMax);
        y.set("motif", z.pattern == null ? "" : z.pattern.raw());
        y.set("intervalle-minutes", z.intervalMinutes);
        y.set("generation", z.epoch);
        y.set("derniere-regeneration", z.lastRegen);
        y.set("graine", z.seed);
        dir.mkdirs();
        try {
            y.save(new File(dir, z.name + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Sauvegarde de la zone " + z.name + " impossible : " + e.getMessage());
        }
    }

    public Zone get(String name) {
        return name == null ? null : zones.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    public List<String> names() {
        return new ArrayList<>(zones.keySet());
    }

    public Zone create(String name, World w, int xa, int za, int xb, int zb, int yMin, int yMax) {
        Zone z = new Zone(name.toLowerCase(Locale.ROOT), w.getName(), xa, za, xb, zb, yMin, yMax);
        z.intervalMinutes = Math.max(1, plugin.configs().main().getInt("regeneration.intervalle-minutes", 120));
        z.seed = System.nanoTime();
        z.lastRegen = System.currentTimeMillis();
        zones.put(z.name, z);
        save(z);
        return z;
    }

    public boolean delete(String name) {
        Zone z = zones.remove(name.toLowerCase(Locale.ROOT));
        if (z == null) return false;
        File f = new File(dir, z.name + ".yml");
        if (f.exists() && !f.delete()) plugin.getLogger().warning("Suppression impossible : " + f.getName());
        return true;
    }

    /** Zone contenant ce bloc (null = aucune). */
    public Zone at(World w, int x, int y, int z) {
        for (Zone zone : zones.values()) if (zone.contains(w, x, y, z)) return zone;
        return null;
    }

    public Zone at(Location l) {
        return l == null ? null : at(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    // ------------------------------------------------------------------ sélections

    public void setPos(UUID admin, int index, Location loc) {
        selections.computeIfAbsent(admin, k -> new Location[2])[index] = loc.clone();
    }

    public Location[] selection(UUID admin) {
        return selections.get(admin);
    }
}
