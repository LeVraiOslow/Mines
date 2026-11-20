package fr.ivalona.mines.zone;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/** Motif de blocs : "60%stone,20%gold_ore,20%grass". */
public final class BlockPattern {

    private static final Map<String, String> ALIASES = Map.of(
            "grass", "GRASS_BLOCK",
            "herbe", "GRASS_BLOCK",
            "pierre", "STONE",
            "terre", "DIRT");

    public record Entry(Material material, double percent) {
    }

    private final String raw;
    private final List<Entry> entries;
    private final BlockData[] datas;
    private final double[] cumulative;
    private final double total;

    private BlockPattern(String raw, List<Entry> entries) {
        this.raw = raw;
        this.entries = entries;
        this.datas = new BlockData[entries.size()];
        this.cumulative = new double[entries.size()];
        double sum = 0;
        for (int i = 0; i < entries.size(); i++) {
            datas[i] = entries.get(i).material().createBlockData();
            sum += entries.get(i).percent();
            cumulative[i] = sum;
        }
        this.total = sum;
    }

    /** @throws IllegalArgumentException message lisible si le motif est invalide */
    public static BlockPattern parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("motif vide");
        List<Entry> list = new ArrayList<>();
        for (String part : input.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int pct = p.indexOf('%');
            if (pct <= 0) throw new IllegalArgumentException("'" + p + "' : format attendu 60%stone");
            double value;
            try {
                value = Double.parseDouble(p.substring(0, pct).trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + p + "' : pourcentage invalide");
            }
            if (value <= 0) throw new IllegalArgumentException("'" + p + "' : le pourcentage doit être positif");
            String name = p.substring(pct + 1).trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) name = name.substring(10);
            String mapped = ALIASES.getOrDefault(name, name);
            Material m = Material.matchMaterial(mapped);
            if (m == null || !m.isBlock()) throw new IllegalArgumentException("'" + name + "' n'est pas un bloc");
            list.add(new Entry(m, value));
        }
        if (list.isEmpty()) throw new IllegalArgumentException("aucun bloc");
        return new BlockPattern(input.trim(), list);
    }

    public BlockData pick(SplittableRandom r) {
        double v = r.nextDouble() * total;
        for (int i = 0; i < cumulative.length; i++) if (v < cumulative[i]) return datas[i];
        return datas[datas.length - 1];
    }

    public double total() {
        return total;
    }

    public List<Entry> entries() {
        return entries;
    }

    public String raw() {
        return raw;
    }

    /** "60% stone, 20% gold_ore, 20% grass_block" (pourcentages ramenés sur 100) */
    public String display() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(", ");
            double pc = e.percent() * 100.0 / total;
            sb.append(pc == Math.rint(pc) ? String.valueOf((long) pc) : String.format(Locale.ROOT, "%.1f", pc))
                    .append("% ").append(e.material().name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
