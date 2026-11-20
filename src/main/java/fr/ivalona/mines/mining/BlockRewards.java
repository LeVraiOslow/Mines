package fr.ivalona.mines.mining;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.util.Configs;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** recompenses/blocs.yml : MineCoins et chance de Cristaux par bloc. */
public final class BlockRewards {

    public record Reward(int coinsMin, int coinsMax, double crystalChance, int crystalsMin, int crystalsMax) {
        public int coins() {
            return coinsMax <= coinsMin ? coinsMin : ThreadLocalRandom.current().nextInt(coinsMin, coinsMax + 1);
        }

        public int crystals() {
            return crystalsMax <= crystalsMin ? crystalsMin : ThreadLocalRandom.current().nextInt(crystalsMin, crystalsMax + 1);
        }
    }

    private final IvalonaMines plugin;
    private final Map<Material, Reward> rewards = new EnumMap<>(Material.class);
    private Reward fallback = new Reward(1, 1, 0, 0, 0);

    public BlockRewards(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rewards.clear();
        YamlConfiguration y = plugin.configs().get(Configs.BLOCS);
        ConfigurationSection def = y.getConfigurationSection("defaut");
        if (def != null) fallback = parse(def);
        ConfigurationSection blocs = y.getConfigurationSection("blocs");
        if (blocs == null) return;
        for (String k : blocs.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            ConfigurationSection s = blocs.getConfigurationSection(k);
            if (m == null || s == null) {
                plugin.getLogger().warning("recompenses/blocs.yml : bloc inconnu '" + k + "'");
                continue;
            }
            rewards.put(m, parse(s));
        }
    }

    private static Reward parse(ConfigurationSection s) {
        int[] coins = range(String.valueOf(s.get("minecoins", "0")));
        double chance = s.getDouble("cristaux.chance", 0);
        int[] nb = range(String.valueOf(s.get("cristaux.nombre", "1")));
        return new Reward(coins[0], coins[1], chance, nb[0], nb[1]);
    }

    /** "2" -> [2,2] ; "1-3" -> [1,3] */
    static int[] range(String v) {
        try {
            String t = v.trim();
            int dash = t.indexOf('-', 1);
            if (dash > 0) {
                int a = Integer.parseInt(t.substring(0, dash).trim()), b = Integer.parseInt(t.substring(dash + 1).trim());
                return new int[]{Math.min(a, b), Math.max(a, b)};
            }
            int n = (int) Double.parseDouble(t);
            return new int[]{n, n};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    public Reward get(Material m) {
        return rewards.getOrDefault(m, fallback);
    }
}
