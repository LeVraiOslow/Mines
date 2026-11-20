package fr.ivalona.mines.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Couleurs (&a, &#RRGGBB, <#RRGGBB>), variables {x} et sons. */
public final class Text {

    private static final LegacyComponentSerializer SER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final Pattern ANGLE_HEX = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private Text() {
    }

    public static String replace(String s, Map<String, String> vars) {
        if (s == null) return "";
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return s;
    }

    public static Component color(String s) {
        if (s == null) s = "";
        s = ANGLE_HEX.matcher(s).replaceAll("&#$1");
        return SER.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Map<String, String> vars(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Format : "entity.player.levelup;volume;pitch" */
    public static void sound(Player p, String spec) {
        if (p == null || spec == null || spec.isBlank()) return;
        String[] a = spec.split(";");
        float vol = 1f, pitch = 1f;
        try {
            if (a.length > 1) vol = Float.parseFloat(a[1].trim());
            if (a.length > 2) pitch = Float.parseFloat(a[2].trim());
        } catch (NumberFormatException ignored) {
        }
        p.playSound(p.getLocation(), a[0].trim().toLowerCase(Locale.ROOT), vol, pitch);
    }

    /** 1234567 -> "1.234.567" */
    public static String number(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) sb.append('.');
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    /** 3725 secondes -> "1h 02m" / "5m 03s" */
    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + String.format("%02dm", m);
        return m + "m " + String.format("%02ds", s);
    }
}
