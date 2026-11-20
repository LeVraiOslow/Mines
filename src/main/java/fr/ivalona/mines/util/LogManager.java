package fr.ivalona.mines.util;

import fr.ivalona.mines.IvalonaMines;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Journaux dans plugins/IvalonaMines/logs/<date>.log (écriture en arrière-plan). */
public final class LogManager {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final IvalonaMines plugin;
    private final File dir;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IvalonaMines-Logs");
        t.setDaemon(true);
        return t;
    });

    public LogManager(IvalonaMines plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "logs");
    }

    /** categorie : admin, zones, achats, ameliorations, entrees-sorties */
    public void log(String categorie, String message) {
        if (!plugin.configs().main().getBoolean("logs.actif", true)) return;
        if (!plugin.configs().main().getBoolean("logs." + categorie, true)) return;
        String line = "[" + LocalTime.now().format(TIME) + "] [" + categorie.toUpperCase() + "] " + message
                + System.lineSeparator();
        File file = new File(dir, LocalDate.now() + ".log");
        writer.execute(() -> {
            dir.mkdirs();
            try (Writer w = new FileWriter(file, StandardCharsets.UTF_8, true)) {
                w.write(line);
            } catch (IOException e) {
                plugin.getLogger().warning("Écriture du log impossible : " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
