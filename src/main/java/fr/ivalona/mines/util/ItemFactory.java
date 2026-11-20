package fr.ivalona.mines.util;

import fr.ivalona.mines.IvalonaMines;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Construit un item depuis la config : matériau vanilla ou item Nexo (nexo:xxx). */
public final class ItemFactory {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ItemFactory() {
    }

    public static ItemStack fromSpec(IvalonaMines plugin, String spec) {
        if (spec == null || spec.isBlank()) return missing("(vide)");
        String s = spec.trim();
        if (s.regionMatches(true, 0, "nexo:", 0, 5)) {
            ItemStack n = nexo(s.substring(5).trim());
            if (n != null) return n;
            warn(plugin, "Item Nexo introuvable : " + s);
            return missing(s);
        }
        Material m = Material.matchMaterial(s);
        if (m != null && m.isItem() && !m.isAir()) return new ItemStack(m);
        warn(plugin, "Item inconnu : " + s);
        return missing(s);
    }

    /** Appel par réflexion : pas de dépendance de compilation sur Nexo. */
    private static ItemStack nexo(String id) {
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = api.getMethod("itemFromId", String.class).invoke(null, id);
            if (builder == null) return null;
            Object stack = builder.getClass().getMethod("build").invoke(builder);
            return stack instanceof ItemStack is ? is : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static ItemStack missing(String spec) {
        ItemStack st = new ItemStack(Material.BARRIER);
        ItemMeta meta = st.getItemMeta();
        meta.displayName(Text.color("&cItem introuvable : &f" + spec));
        st.setItemMeta(meta);
        return st;
    }

    private static void warn(IvalonaMines plugin, String msg) {
        if (WARNED.add(msg)) plugin.getLogger().warning(msg);
    }

    /** Item depuis une section (item, amount, name, lore, glow, hide-flags). */
    public static ItemStack build(IvalonaMines plugin, ConfigurationSection sec, Map<String, String> vars) {
        String spec = Text.replace(sec.getString("item", sec.getString("material", "STONE")), vars);
        ItemStack stack = fromSpec(plugin, spec);
        stack.setAmount(Math.max(1, Math.min(sec.getInt("amount", 1), stack.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (sec.contains("name")) meta.displayName(Text.color(Text.replace(sec.getString("name"), vars)));
        if (sec.contains("lore")) {
            List<Component> lore = new ArrayList<>();
            for (String line : sec.getStringList("lore")) lore.add(Text.color(Text.replace(line, vars)));
            meta.lore(lore);
        }
        if (sec.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        if (sec.getBoolean("hide-flags", true)) meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    /** "[0, 1, \"3-5\"]" ou slot: 4 */
    public static List<Integer> slots(ConfigurationSection s, int size) {
        List<Integer> out = new ArrayList<>();
        if (s.isList("slots")) {
            for (Object o : s.getList("slots")) {
                String v = String.valueOf(o).trim();
                try {
                    if (v.contains("-")) {
                        String[] r = v.split("-");
                        int a = Integer.parseInt(r[0].trim()), b = Integer.parseInt(r[1].trim());
                        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
                    } else {
                        out.add(Integer.parseInt(v));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } else if (s.contains("slot")) {
            out.add(s.getInt("slot"));
        }
        out.removeIf(i -> i < 0 || i >= size);
        return out;
    }
}
