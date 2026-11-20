package fr.ivalona.mines.pickaxe;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.util.Configs;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lecture de pioche/ameliorations.yml. */
public final class Upgrades {

    public static final String VITESSE = "vitesse";
    public static final String TAILLE = "taille";
    public static final String PROFONDEUR = "profondeur";
    public static final String RECOMPENSES = "recompenses";
    public static final List<String> TYPES = List.of(VITESSE, TAILLE, PROFONDEUR, RECOMPENSES);

    private final IvalonaMines plugin;

    public Upgrades(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    public static String normalize(String s) {
        if (s == null) return null;
        String t = s.toLowerCase(Locale.ROOT).replace("é", "e");
        return TYPES.contains(t) ? t : null;
    }

    private ConfigurationSection root(String type) {
        return plugin.configs().get(Configs.AMELIORATIONS).getConfigurationSection(type);
    }

    public String name(String type) {
        ConfigurationSection r = root(type);
        return r == null ? type : r.getString("nom", type);
    }

    /** niveau -> section, null si inexistant */
    public ConfigurationSection level(String type, int level) {
        ConfigurationSection r = root(type);
        if (r == null) return null;
        return r.getConfigurationSection("niveaux." + level);
    }

    public int maxLevel(String type) {
        int max = 1;
        while (level(type, max + 1) != null) max++;
        return max;
    }

    /** niveau limité entre 1 et le maximum */
    public int clamp(String type, int level) {
        return Math.max(1, Math.min(level, maxLevel(type)));
    }

    public long cost(String type, int level) {
        ConfigurationSection s = level(type, level);
        return s == null ? 0 : s.getLong("cout", 0);
    }

    public String value(String type, int level) {
        ConfigurationSection s = level(type, clamp(type, level));
        return s == null ? String.valueOf(level) : s.getString("valeur", String.valueOf(level));
    }

    public String permission(String type, int level) {
        ConfigurationSection s = level(type, level);
        return s == null ? null : s.getString("permission");
    }

    private double num(String type, int level, String key, double def) {
        ConfigurationSection s = level(type, clamp(type, level));
        return s == null ? def : s.getDouble(key, def);
    }

    public int efficiency(int level) {
        return (int) num(VITESSE, level, "efficacite", 0);
    }

    public int haste(int level) {
        return (int) num(VITESSE, level, "celerite", 0);
    }

    public int width(int level) {
        return Math.max(1, (int) num(TAILLE, level, "largeur", 1));
    }

    public int height(int level) {
        return Math.max(1, (int) num(TAILLE, level, "hauteur", 1));
    }

    public int depth(int level) {
        return Math.max(1, (int) num(PROFONDEUR, level, "blocs", 1));
    }

    /** portée de minage (0 = ne pas modifier) */
    public double reach(int level) {
        return num(PROFONDEUR, level, "portee", 0);
    }

    public double coinMultiplier(int level) {
        return num(RECOMPENSES, level, "multiplicateur-minecoins", 1.0);
    }

    public double crystalMultiplier(int level) {
        return num(RECOMPENSES, level, "multiplicateur-cristaux", 1.0);
    }

    public List<String> types() {
        return new ArrayList<>(TYPES);
    }
}
