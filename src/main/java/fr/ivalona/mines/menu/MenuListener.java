package fr.ivalona.mines.menu;

import fr.ivalona.mines.IvalonaMines;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Clics dans les menus du plugin (rien ne peut être pris). */
public final class MenuListener implements Listener {

    private final IvalonaMines plugin;

    public MenuListener(IvalonaMines plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT && e.getClick() != ClickType.SHIFT_LEFT) return;
        MenuHolder.Entry entry = h.entries().get(e.getRawSlot());
        if (entry != null) plugin.menus().click(p, h, entry);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }
}
