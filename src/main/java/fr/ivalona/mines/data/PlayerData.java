package fr.ivalona.mines.data;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Données d'un joueur pour la mine. */
public final class PlayerData {

    public final UUID uuid;
    public String name;
    public long minecoins;
    public long cristaux;
    /** amélioration -> niveau (1 = départ) */
    public final Map<String, Integer> niveaux = new HashMap<>();

    /** true tant que l'inventaire "hors mine" est sauvegardé (joueur dans la mine) */
    public boolean dansMine;
    public ItemStack[] inventaireSauvegarde;
    public int niveauXpSauvegarde;
    public float xpSauvegardee;

    boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public int niveau(String amelioration) {
        return Math.max(1, niveaux.getOrDefault(amelioration, 1));
    }

    public void markDirty() {
        dirty = true;
    }
}
