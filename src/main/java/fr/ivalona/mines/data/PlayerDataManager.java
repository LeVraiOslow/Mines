package fr.ivalona.mines.data;

import fr.ivalona.mines.IvalonaMines;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Fichiers data/joueurs/<uuid>.yml. Les joueurs connectés restent en mémoire. */
public final class PlayerDataManager {

    private final IvalonaMines plugin;
    private final File dir;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IvalonaMines-Data");
        t.setDaemon(true);
        return t;
    });

    public PlayerDataManager(IvalonaMines plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "data/joueurs");
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    /** Données d'un joueur (chargées depuis le disque si besoin). */
    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public PlayerData get(OfflinePlayer p) {
        PlayerData d = get(p.getUniqueId());
        if (p.getName() != null) d.name = p.getName();
        return d;
    }

    private PlayerData load(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        PlayerData d = new PlayerData(id, op.getName() == null ? id.toString() : op.getName());
        File f = file(id);
        if (!f.exists()) return d;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(f);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Fichier joueur illisible " + f.getName() + " : " + e.getMessage());
            return d;
        }
        d.name = y.getString("nom", d.name);
        d.minecoins = y.getLong("minecoins", 0);
        d.cristaux = y.getLong("cristaux", 0);
        ConfigurationSection lv = y.getConfigurationSection("niveaux");
        if (lv != null) for (String k : lv.getKeys(false)) d.niveaux.put(k, lv.getInt(k, 1));
        d.dansMine = y.getBoolean("dans-mine", false);
        if (y.isList("inventaire")) {
            List<?> list = y.getList("inventaire");
            ItemStack[] inv = new ItemStack[list.size()];
            for (int i = 0; i < inv.length; i++) {
                Object o = list.get(i);
                inv[i] = o instanceof ItemStack is ? is : null;
            }
            d.inventaireSauvegarde = inv;
        }
        d.niveauXpSauvegarde = y.getInt("xp-niveau", 0);
        d.xpSauvegardee = (float) y.getDouble("xp", 0);
        return d;
    }

    private String serialize(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nom", d.name);
        y.set("minecoins", d.minecoins);
        y.set("cristaux", d.cristaux);
        for (Map.Entry<String, Integer> e : d.niveaux.entrySet()) y.set("niveaux." + e.getKey(), e.getValue());
        y.set("dans-mine", d.dansMine);
        if (d.inventaireSauvegarde != null) {
            y.set("inventaire", java.util.Arrays.asList(d.inventaireSauvegarde));
            y.set("xp-niveau", d.niveauXpSauvegarde);
            y.set("xp", (double) d.xpSauvegardee);
        }
        return y.saveToString();
    }

    private void write(UUID id, String content) {
        File f = file(id);
        File tmp = new File(dir, id + ".yml.tmp");
        try {
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
            } catch (IOException e2) {
                plugin.getLogger().severe("Sauvegarde du joueur " + id + " impossible : " + e2.getMessage());
            }
        }
    }

    /** Sauvegarde immédiate (inventaires : on ne prend aucun risque). */
    public void saveNow(PlayerData d) {
        d.dirty = false;
        write(d.uuid, serialize(d));
    }

    /** Sauvegarde en arrière-plan. */
    public void saveAsync(PlayerData d) {
        d.dirty = false;
        String content = serialize(d);
        io.execute(() -> write(d.uuid, content));
    }

    /** Sauvegarde périodique des données modifiées. */
    public void flushDirty() {
        for (PlayerData d : cache.values()) if (d.dirty) saveAsync(d);
    }

    /** À la déconnexion : sauvegarde et libère la mémoire. */
    public void unload(UUID id) {
        PlayerData d = cache.remove(id);
        if (d != null) saveAsync(d);
    }

    public void delete(UUID id) {
        cache.remove(id);
        File f = file(id);
        if (f.exists() && !f.delete()) plugin.getLogger().warning("Suppression impossible : " + f.getName());
    }

    public void shutdown() {
        for (PlayerData d : cache.values()) {
            d.dirty = false;
            write(d.uuid, serialize(d));
        }
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
