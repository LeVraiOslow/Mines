package fr.ivalona.mines.zone;

import org.bukkit.Location;
import org.bukkit.World;

/** Une zone de mine (boîte de x1..x2, z1..z2, yMin..yMax). */
public final class Zone {

    public final String name;
    public final String world;
    public final int x1, z1, x2, z2, yMin, yMax;

    public BlockPattern pattern;
    public int intervalMinutes;
    /** numéro de génération : chaque régénération l'augmente, les chunks plus anciens sont refaits */
    public int epoch;
    public long lastRegen;
    public long seed;

    /** dernier avertissement envoyé (minutes) pour la régénération en cours */
    int lastWarning = Integer.MAX_VALUE;

    public Zone(String name, String world, int xa, int za, int xb, int zb, int yMin, int yMax) {
        this.name = name;
        this.world = world;
        this.x1 = Math.min(xa, xb);
        this.x2 = Math.max(xa, xb);
        this.z1 = Math.min(za, zb);
        this.z2 = Math.max(za, zb);
        this.yMin = Math.min(yMin, yMax);
        this.yMax = Math.max(yMin, yMax);
    }

    public boolean contains(World w, int x, int y, int z) {
        return w != null && w.getName().equalsIgnoreCase(world)
                && x >= x1 && x <= x2 && z >= z1 && z <= z2 && y >= yMin && y <= yMax;
    }

    public boolean contains(Location l) {
        return l != null && contains(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    /** même colonne (X/Z) que la zone, quelle que soit la hauteur */
    public boolean containsColumn(Location l) {
        return l != null && l.getWorld() != null && l.getWorld().getName().equalsIgnoreCase(world)
                && l.getBlockX() >= x1 && l.getBlockX() <= x2 && l.getBlockZ() >= z1 && l.getBlockZ() <= z2;
    }

    public boolean intersectsChunk(String w, int cx, int cz) {
        if (!w.equalsIgnoreCase(world)) return false;
        int bx1 = cx << 4, bz1 = cz << 4;
        return bx1 + 15 >= x1 && bx1 <= x2 && bz1 + 15 >= z1 && bz1 <= z2;
    }

    public int sizeX() {
        return x2 - x1 + 1;
    }

    public int sizeZ() {
        return z2 - z1 + 1;
    }

    public long nextRegen() {
        return lastRegen + intervalMinutes * 60_000L;
    }
}
