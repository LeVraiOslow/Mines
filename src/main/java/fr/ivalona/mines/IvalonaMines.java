package fr.ivalona.mines;

import fr.ivalona.mines.command.AdminCommand;
import fr.ivalona.mines.command.PlayerCommands;
import fr.ivalona.mines.command.ZoneCommand;
import fr.ivalona.mines.data.PlayerDataManager;
import fr.ivalona.mines.hook.PapiExpansion;
import fr.ivalona.mines.menu.MenuHolder;
import fr.ivalona.mines.menu.MenuListener;
import fr.ivalona.mines.menu.MenuManager;
import fr.ivalona.mines.mining.BlockRewards;
import fr.ivalona.mines.mining.MiningListener;
import fr.ivalona.mines.pickaxe.PickaxeManager;
import fr.ivalona.mines.pickaxe.Upgrades;
import fr.ivalona.mines.util.Configs;
import fr.ivalona.mines.util.LogManager;
import fr.ivalona.mines.util.Messages;
import fr.ivalona.mines.world.WorldListener;
import fr.ivalona.mines.zone.RegenManager;
import fr.ivalona.mines.zone.VoidGenerator;
import fr.ivalona.mines.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class IvalonaMines extends JavaPlugin {

    private Configs configs;
    private Messages messages;
    private LogManager logs;
    private PlayerDataManager data;
    private ZoneManager zones;
    private RegenManager regen;
    private Upgrades upgrades;
    private PickaxeManager pickaxes;
    private BlockRewards rewards;
    private MenuManager menus;
    private WorldListener worldListener;
    private Location mineSpawn;

    @Override
    public void onEnable() {
        configs = new Configs(this);
        configs.load();
        messages = new Messages(this);
        logs = new LogManager(this);
        data = new PlayerDataManager(this);
        zones = new ZoneManager(this);
        zones.load();
        upgrades = new Upgrades(this);
        pickaxes = new PickaxeManager(this);
        rewards = new BlockRewards(this);
        rewards.load();
        regen = new RegenManager(this);
        menus = new MenuManager(this);
        loadSpawn();

        worldListener = new WorldListener(this);
        ZoneCommand zoneCommand = new ZoneCommand(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(regen, this);
        pm.registerEvents(new MiningListener(this), this);
        pm.registerEvents(worldListener, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(zoneCommand, this);

        command("ivalonazone", zoneCommand);
        command("ivalonamines", new AdminCommand(this));
        PlayerCommands pc = new PlayerCommands(this);
        command("mine", pc);
        command("boutiquemine", pc);
        command("minecoins", pc);

        if (pm.getPlugin("PlaceholderAPI") != null) {
            new PapiExpansion(this).register();
            getLogger().info("Placeholders PlaceholderAPI enregistrés (%ivalonamines_...%).");
        }

        Bukkit.getScheduler().runTaskTimer(this, regen::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, regen::timer, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, data::flushDirty, 600L, 600L);

        // /reload ou redémarrage du plugin : joueurs déjà dans la mine
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) if (isMineWorld(p.getWorld())) worldListener.enter(p);
        });

        if (Bukkit.getWorld(mineWorldName()) == null) {
            getLogger().warning("Le monde '" + mineWorldName() + "' n'existe pas encore. Crée-le avec Multiverse-Core : "
                    + "/mv create " + mineWorldName() + " normal -g IvalonaMines");
        }
        logs.log("admin", "Plugin démarré (" + zones.all().size() + " zone(s))");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) p.closeInventory();
        }
        if (data != null) data.shutdown();
        if (logs != null) {
            logs.log("admin", "Plugin arrêté");
            logs.shutdown();
        }
    }

    private void command(String name, TabExecutor exec) {
        PluginCommand c = getCommand(name);
        if (c == null) return;
        c.setExecutor(exec);
        c.setTabCompleter(exec);
    }

    /** /ivalonamines reload */
    public void reloadAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) p.closeInventory();
        }
        configs.load();
        rewards.load();
        loadSpawn();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isMineWorld(p.getWorld()) && data.get(p).dansMine) pickaxes.give(p);
        }
    }

    /** Monde vide : /mv create IvalonaMines normal -g IvalonaMines */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator();
    }

    // ------------------------------------------------------------------ monde / spawn

    public String mineWorldName() {
        return configs.main().getString("monde", "IvalonaMines");
    }

    public boolean isMineWorld(World w) {
        return w != null && w.getName().equalsIgnoreCase(mineWorldName());
    }

    private File serverFile() {
        return new File(getDataFolder(), "data/serveur.yml");
    }

    private void loadSpawn() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(serverFile());
        mineSpawn = null;
        String w = y.getString("spawn-mine.monde");
        if (w == null) return;
        World world = Bukkit.getWorld(w);
        if (world == null) return;
        mineSpawn = new Location(world, y.getDouble("spawn-mine.x"), y.getDouble("spawn-mine.y"), y.getDouble("spawn-mine.z"),
                (float) y.getDouble("spawn-mine.yaw"), (float) y.getDouble("spawn-mine.pitch"));
    }

    /** Spawn de la mine (/ivalonamines setspawn), ou spawn du monde de la mine. */
    public Location mineSpawn() {
        if (mineSpawn != null && mineSpawn.getWorld() != null) return mineSpawn.clone();
        loadSpawn();
        if (mineSpawn != null) return mineSpawn.clone();
        World w = Bukkit.getWorld(mineWorldName());
        return w == null ? null : w.getSpawnLocation();
    }

    public void setMineSpawn(Location l) {
        mineSpawn = l.clone();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(serverFile());
        y.set("spawn-mine.monde", l.getWorld().getName());
        y.set("spawn-mine.x", l.getX());
        y.set("spawn-mine.y", l.getY());
        y.set("spawn-mine.z", l.getZ());
        y.set("spawn-mine.yaw", (double) l.getYaw());
        y.set("spawn-mine.pitch", (double) l.getPitch());
        try {
            serverFile().getParentFile().mkdirs();
            y.save(serverFile());
        } catch (IOException e) {
            getLogger().severe("Sauvegarde du spawn impossible : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ accès

    public Configs configs() { return configs; }
    public Messages messages() { return messages; }
    public LogManager logs() { return logs; }
    public PlayerDataManager data() { return data; }
    public ZoneManager zones() { return zones; }
    public RegenManager regen() { return regen; }
    public Upgrades upgrades() { return upgrades; }
    public PickaxeManager pickaxes() { return pickaxes; }
    public BlockRewards rewards() { return rewards; }
    public MenuManager menus() { return menus; }
}
