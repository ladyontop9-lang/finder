package com.example.storagefinder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Stan licencji Krux Client.
 *
 * - Klient: wpisuje klucz licencji RAZ; zapamiętuje się na stałe w config/krux/license.key
 *   (poza jarem, więc aktualizacje moda nie kasują licencji).
 * - Wersja developer (krux-client-1.8-developer.jar) zawiera wbudowane narzędzie do podpisywania licencji
 *   i ma zawsze pełny dostęp. Nie ma żadnych dodatkowych plików z kluczami.
 */
public final class Krux {
    public static final String NAME = "Krux Client";

    private static final Properties BUILD = loadBuild();
    /** Klucz do podpisywania — istnieje WYŁĄCZNIE w jarze developer. */
    private static final String DEV_PRIVATE = loadDevPrivate();
    /** true w krux-client-1.8-developer.jar. */
    public static final boolean DEV_BUILD = DEV_PRIVATE != null;

    private static boolean licensed = false;
    private static boolean promptShown = false;
    private static KruxLicense.Info info = null;
    private static String status = "No license";
    private static int tickCounter = 0;

    private Krux() {}

    private static Properties loadBuild() {
        Properties p = new Properties();
        try (InputStream in = Krux.class.getResourceAsStream("/krux_build.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {
        }
        return p;
    }

    private static String loadDevPrivate() {
        try (InputStream in = Krux.class.getResourceAsStream("/krux_dev_private.key")) {
            if (in == null) return null;
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ ścieżki

    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("krux"); }
    private static Path licenseFile() { return dir().resolve("license.key"); }
    private static Path stateFile() { return dir().resolve("state.dat"); }
    private static Path issuedLog() { return dir().resolve("issued.log"); }

    private static long today() { return LocalDate.now(ZoneOffset.UTC).toEpochDay(); }

    // ------------------------------------------------------------------ stan

    /** Wersja developer zawsze ma pełny dostęp. */
    public static boolean licensed() { return licensed || DEV_BUILD; }
    public static String status() { return status; }
    public static KruxLicense.Info info() { return info; }

    public static boolean configured() {
        try {
            KruxLicense.decodePublic(KruxPublicKey.PUBLIC_KEY);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Wersja tego jara (np. 1.8.0 DEV #b40789a). */
    public static String buildId() {
        String ver = "?";
        try {
            ver = FabricLoader.getInstance().getModContainer("storagefinder")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        } catch (Exception ignored) {
        }
        return ver + (DEV_BUILD ? " DEV" : "") + " #" + BUILD.getProperty("build", "local");
    }

    public static String keyStatus() {
        return configured() ? "OK" : "NOT SET";
    }

    public static String describe() {
        if (DEV_BUILD) return "Developer version";
        if (!licensed || info == null) return status;
        if (info.lifetime()) return "Lifetime license";
        long left = info.expiryDay() - today();
        return "Expires " + LocalDate.ofEpochDay(info.expiryDay()) + " (" + Math.max(0, left) + " days left)";
    }

    public static String shortStatus() {
        if (DEV_BUILD) return "Developer";
        if (!licensed || info == null) return "Not licensed";
        if (info.lifetime()) return "Lifetime";
        return Math.max(0, info.expiryDay() - today()) + " days left";
    }

    // ------------------------------------------------------------------ ładowanie i aktywacja

    public static void load() {
        licensed = false;
        info = null;
        if (DEV_BUILD) {
            status = "Developer version";
            return;
        }
        if (!configured()) {
            status = "License system is not configured";
            return;
        }
        try {
            if (Files.exists(licenseFile())) {
                String err = apply(Files.readString(licenseFile()).trim(), false);
                if (err != null) status = err;
                return;
            }
        } catch (Exception ignored) {
        }
        status = "No license";
    }

    /** Sprawdza klucz; zwraca null gdy OK, w przeciwnym razie komunikat błędu. */
    private static String apply(String token, boolean save) {
        long today = today();
        long lastSeen = readLastSeen();
        if (today + 1 < lastSeen) return "System clock looks wrong. Fix your date and time.";

        KruxLicense.Check c = KruxLicense.verify(token, KruxPublicKey.PUBLIC_KEY, today);
        switch (c.result()) {
            case VALID -> {
                licensed = true;
                info = c.info();
                writeLastSeen(Math.max(lastSeen, today));
                if (save) {
                    try {
                        Files.createDirectories(dir());
                        Files.writeString(licenseFile(), token.trim(), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return "Could not save the license file.";
                    }
                }
                status = describe();
                return null;
            }
            case EXPIRED -> {
                info = c.info();
                return "This license expired on " + LocalDate.ofEpochDay(c.info().expiryDay()) + ".";
            }
            case NOT_CONFIGURED -> {
                return "License system is not configured.";
            }
            default -> {
                return "Invalid license key.";
            }
        }
    }

    /** Aktywacja klucza wpisanego przez użytkownika. null = sukces. */
    public static String activate(String token) {
        if (token == null || token.isBlank()) return "Paste your license key first.";
        if (!configured()) return "License system is not configured.";
        return apply(token, true);
    }

    private static long readLastSeen() {
        try {
            if (Files.exists(stateFile())) return Long.parseLong(Files.readString(stateFile()).trim());
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void writeLastSeen(long day) {
        try {
            Files.createDirectories(dir());
            Files.writeString(stateFile(), Long.toString(day));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ wystawianie licencji (tylko wersja developer)

    /** Wystawia licencję: lifetime albo na podaną liczbę dni. Zwraca gotowy klucz dla klienta. */
    public static String issue(boolean lifetime, int days, String note) throws Exception {
        if (DEV_PRIVATE == null) throw new IllegalStateException("This is not the developer version.");
        long today = today();
        long expiry = lifetime ? 0 : today + Math.max(1, days);
        String token = KruxLicense.create(DEV_PRIVATE, lifetime ? KruxLicense.LIFETIME : KruxLicense.TIMED, today, expiry);

        // kontrola: podpis musi pasować do klucza publicznego wbudowanego w jary klientów
        KruxLicense.Check c = KruxLicense.verify(token, KruxPublicKey.PUBLIC_KEY, today);
        if (c.result() != KruxLicense.Result.VALID) {
            throw new IllegalStateException("Signing key does not match KruxPublicKey.java");
        }

        String line = Instant.now() + " | " + c.info().id() + " | "
                + (lifetime ? "LIFETIME" : "TIMED until " + LocalDate.ofEpochDay(expiry))
                + (note == null || note.isBlank() ? "" : " | " + note.trim()) + System.lineSeparator();
        try {
            Files.createDirectories(dir());
            Files.writeString(issuedLog(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        return token;
    }

    // ------------------------------------------------------------------ integracja z grą

    public static Screen gateScreen(Screen parent) {
        return new LicenseScreen(parent);
    }

    public static void tick(Minecraft mc) {
        // wygaśnięcie licencji czasowej w trakcie gry
        if (++tickCounter % 1200 == 0 && licensed && info != null && !info.lifetime() && today() > info.expiryDay()) {
            licensed = false;
            status = "License expired";
            for (Module m : Modules.ALL) m.setEnabled(false);
        }

        // okno licencji przy pierwszym wejściu na ekran tytułowy
        if (!licensed() && !promptShown && mc.screen instanceof TitleScreen) {
            promptShown = true;
            mc.setScreen(gateScreen(mc.screen));
        }
    }
}
