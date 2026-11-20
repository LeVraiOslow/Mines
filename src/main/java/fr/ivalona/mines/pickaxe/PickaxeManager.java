package fr.ivalona.mines.pickaxe;

import fr.ivalona.mines.IvalonaMines;
import fr.ivalona.mines.data.PlayerData;
import fr.ivalona.mines.util.Configs;
import fr.ivalona.mines.util.ItemFactory;
import fr.ivalona.mines.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** La pioche : construction de l'item et effets (Efficacité, Célérité, portée). */
public final class PickaxeManager {

    private final IvalonaMines plugin;
    private final NamespacedKey tag;

    public PickaxeManager(IvalonaMines plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "pioche");
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get(Configs.PIOCHE);
    }

    public boolean isPickaxe(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    /** Variables : niveaux, valeurs, soldes. */
    public Map<String, String> vars(Player p, PlayerData d) {
        Upgrades u = plugin.upgrades();
        Map<String, String> v = Text.vars("joueur", p.getName(),
                "minecoins", Text.number(d.minecoins), "cristaux", Text.number(d.cristaux));
        for (String t : Upgrades.TYPES) {
            int lvl = u.clamp(t, d.niveau(t));
            v.put(t, String.valueOf(lvl));
            v.put(t + "_valeur", u.value(t, lvl));
            v.put(t + "_max", String.valueOf(u.maxLevel(t)));
        }
        return v;
    }

    public ItemStack build(Player p, PlayerData d) {
        YamlConfiguration c = cfg();
        Map<String, String> vars = vars(p, d);
        ItemStack item = ItemFactory.fromSpec(plugin, c.getString("item", "DIAMOND_PICKAXE"));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Text.color(Text.replace(c.getString("name", "&bPioche"), vars)));
        List<Component> lore = new ArrayList<>();
        for (String l : c.getStringList("lore")) lore.add(Text.color(Text.replace(l, vars)));
        meta.lore(lore);
        meta.setUnbreakable(c.getBoolean("unbreakable", true));
        Enchantment eff = Enchantment.EFFICIENCY;
        int effLevel = plugin.upgrades().efficiency(d.niveau(Upgrades.VITESSE));
        if (eff != null && effLevel > 0) meta.addEnchant(eff, effLevel, true);
        if (c.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        else if (effLevel > 0) meta.setEnchantmentGlintOverride(false);
        if (c.getBoolean("hide-flags", true)) meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Donne (ou remplace) la pioche du joueur. */
    public void give(Player p) {
        PlayerData d = plugin.data().get(p);
        ItemStack pick = build(p, d);
        PlayerInventory inv = p.getInventory();
        int found = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            if (isPickaxe(inv.getItem(i))) {
                if (found < 0) found = i;
                else inv.setItem(i, null);
            }
        }
        if (found >= 0) {
            inv.setItem(found, pick);
        } else {
            int slot = Math.max(0, Math.min(8, plugin.configs().main().getInt("inventaire.slot-pioche", 0)));
            ItemStack old = inv.getItem(slot);
            if (old == null || old.getType().isAir()) inv.setItem(slot, pick);
            else inv.addItem(pick);
        }
        applyEffects(p, d);
    }

    /** Retire toutes les pioches de l'inventaire. */
    public void removeAll(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (isPickaxe(inv.getItem(i))) inv.setItem(i, null);
        if (isPickaxe(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    public void applyEffects(Player p, PlayerData d) {
        PotionEffectType haste = PotionEffectType.HASTE;
        if (haste != null) {
            int lvl = plugin.upgrades().haste(d.niveau(Upgrades.VITESSE));
            p.removePotionEffect(haste);
            if (lvl > 0) {
                p.addPotionEffect(new PotionEffect(haste, PotionEffect.INFINITE_DURATION, lvl - 1, true, false, true));
            }
        }
        AttributeInstance reach = reachAttribute(p);
        if (reach != null) {
            double r = plugin.upgrades().reach(d.niveau(Upgrades.PROFONDEUR));
            reach.setBaseValue(r > 0 ? Math.min(64, r) : reach.getDefaultValue());
        }
    }

    public void clearEffects(Player p) {
        PotionEffectType haste = PotionEffectType.HASTE;
        if (haste != null) {
            PotionEffect cur = p.getPotionEffect(haste);
            if (cur != null && cur.getDuration() == PotionEffect.INFINITE_DURATION) p.removePotionEffect(haste);
        }
        AttributeInstance reach = reachAttribute(p);
        if (reach != null) reach.setBaseValue(reach.getDefaultValue());
    }

    private AttributeInstance reachAttribute(Player p) {
        return p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
    }
}
