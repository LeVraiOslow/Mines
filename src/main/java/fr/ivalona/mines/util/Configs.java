package fr.ivalona.mines.util;

import fr.ivalona.mines.IvalonaMines;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Charge tous les fichiers de configuration, rangés par dossier. */
public final class Configs {

    public static final String CONFIG = "config.yml";
    public static final String MESSAGES = "messages/messages.yml";
    public static final String PIOCHE = "pioche/pioche.yml";
    public static final String AMELIORATIONS = "pioche/ameliorations.yml";
    public static final String MENU_AMELIORATIONS = "menus/ameliorations.yml";
    public static final String MENU_BOUTIQUE = "menus/boutique.yml";
    public static final String BLOCS = "recompenses/blocs.yml";

    private static final String[] ALL = {CONFIG, MESSAGES, PIOCHE, AMELIORATIONS, MENU_AMELIORATIONS, MENU_BOUTIQUE, BLOCS};

    private final IvalonaMines plugin;
    private final Map<String, YamlConfiguration> loaded = new HashMap<>();

    public Configs(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    public void load() {
        loaded.clear();
        for (String path : ALL) {
            File f = new File(plugin.getDataFolder(), path);
            if (!f.exists()) plugin.saveResource(path, false);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            // valeurs par défaut du jar pour config et messages (clés manquantes après une mise à jour)
            if (path.equals(CONFIG) || path.equals(MESSAGES)) {
                try (InputStream in = plugin.getResource(path)) {
                    if (in != null) {
                        cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                    }
                } catch (IOException ignored) {
                }
            }
            loaded.put(path, cfg);
        }
    }

    public YamlConfiguration get(String path) {
        return loaded.get(path);
    }

    public YamlConfiguration main() {
        return loaded.get(CONFIG);
    }
}
