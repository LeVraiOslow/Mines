package fr.ivalona.mines.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menu du plugin : action associée à chaque case. */
public final class MenuHolder implements InventoryHolder {

    /** action : CLOSE, NONE, UPGRADE (arg = amélioration), BUY (arg = offre) */
    public record Entry(String action, String arg, List<String> commands) {
    }

    private final String menu;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(String menu) {
        this.menu = menu;
    }

    public String menu() {
        return menu;
    }

    public Map<Integer, Entry> entries() {
        return entries;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
