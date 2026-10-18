package com.example.storagefinder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Stan licencji Krux Client.
 * - Klucz licencji jest zapamiętywany na stałe w config/krux/license.key.
 * - Licencja timed (np. miesięczna) wygasa po dacie z klucza; lifetime nie wygasa.
 * - Tryb dewelopera włącza się, gdy w config/krux/dev.key leży Twój klucz prywatny pasujący do klucza w modzie.
 */
public final class Krux {
    public static final String NAME = "Krux Client";

    private static boolean licensed = false;
    private static boolean dev = false;
    private static boolean promptShown = false;
    private static KruxLicense.Info info = null;
    private static String status = "No license";
    private static int tickCounter = 0;

    private Krux() {}

    // ------------------------------------------------------------------ ścieżki

    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("krux"); }
    private static Path licenseFile() { return dir().resolve("license.key"); }
    private static Path devKeyFile() { return dir().resolve("dev.key"); }
    private static Path stateFile() { return dir().resolve("state.dat"); }
    private static Path issuedLog() { return dir().resolve("issued.log"); }

    /** dev.key: pierwsza linia = klucz prywatny, druga = klucz publiczny (Base64). */
    private static String[] devKeyLines() throws Exception {
        String[] lines = Files.readString(devKeyFile()).trim().split("\\R");
        return new String[]{lines[0].trim(), lines.length > 1 ? lines[1].trim() : ""};
    }

    private static long today() { return LocalDate.now(ZoneOffset.UTC).toEpochDay(); }

    // ------------------------------------------------------------------ stan

    public static boolean licensed() { return licensed || dev; }
    public static boolean isDev() { return dev; }
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

    /** Wersja tego jara (zawiera skrót commita z GitHuba, np. 1.8.1+b40789a). */
    public static String buildId() {
        try {
            return FabricLoader.getInstance().getModContainer("storagefinder")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        } catch (Exception e) {
            return "?";
        }
    }

    /** Krótki status klucza publicznego zapisanego w TYM jarze. */
    public static String keyStatus() {
        String k = KruxPublicKey.PUBLIC_KEY;
        if (k == null || k.contains("PASTE_YOUR")) return "NOT SET";
        try {
            KruxLicense.decodePublic(k);
            return "OK";
        } catch (Exception e) {
            return "INVALID";
        }
    }

    /** Szczegóły do diagnozy (klucz publiczny nie jest tajny). */
    public static String keyDetail() {
        String k = KruxPublicKey.PUBLIC_KEY == null ? "" : KruxPublicKey.PUBLIC_KEY;
        if (k.contains("PASTE_YOUR")) return "This jar still has the placeholder key. Paste your public key and build again.";
        String head = k.substring(0, Math.min(12, k.length()));
        try {
            KruxLicense.decodePublic(k);
            return "Public key OK: " + head + "... (" + k.length() + " chars)";
        } catch (Exception e) {
            return "Public key INVALID: " + head + "... (" + k.length() + " chars). Expected ~60 chars starting with MCowBQYDK2VwAyEA.";
        }
    }

    public static boolean hasPrivateKey() { return Files.exists(devKeyFile()); }

    /** Build bez wklejonego klucza publicznego i bez klucza dewelopera = trzeba przejść konfigurację. */
    public static boolean needsSetup() { return !configured() && !hasPrivateKey(); }

    /** Opis licencji do pokazania w oknie. */
    public static String describe() {
        if (dev) return "Developer mode";
        if (!licensed || info == null) return status;
        if (info.lifetime()) return "Lifetime license";
        long left = info.expiryDay() - today();
        return "Expires " + LocalDate.ofEpochDay(info.expiryDay()) + " (" + Math.max(0, left) + " days left)";
    }

    /** Krótki opis do paska bocznego. */
    public static String shortStatus() {
        if (dev) return "Developer";
        if (!licensed || info == null) return "Not licensed";
        if (info.lifetime()) return "Lifetime";
        return Math.max(0, info.expiryDay() - today()) + " days left";
    }

    // ------------------------------------------------------------------ ładowanie i aktywacja

    public static void load() {
        dev = false;
        licensed = false;
        info = null;

        try {
            if (hasPrivateKey()) {
                String priv = devKeyLines()[0];
                dev = !configured() || KruxLicense.keysMatch(priv, KruxPublicKey.PUBLIC_KEY);
            }
        } catch (Exception ignored) {
        }
        if (dev) {
            status = "Developer mode";
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

    // ------------------------------------------------------------------ narzędzia dewelopera

    /** Tworzy parę kluczy; prywatny trafia do dev.key, zwraca klucz publiczny (do wklejenia w KruxPublicKey). */
    public static String generateKeys() throws Exception {
        KruxLicense.KeyPairB64 kp = KruxLicense.generateKeyPair();
        Files.createDirectories(dir());
        Files.writeString(devKeyFile(), kp.privateKey() + System.lineSeparator() + kp.publicKey(), StandardCharsets.UTF_8);
        load();
        return kp.publicKey();
    }

    /** Klucz publiczny zapisany w dev.key (do wklejenia w KruxPublicKey.java). */
    public static String devPublicKey() {
        try {
            return devKeyLines()[1];
        } catch (Exception e) {
            return "";
        }
    }

    /** Wystawia licencję: days <= 0 => lifetime. */
    public static String issue(boolean lifetime, int days, String note) throws Exception {
        if (!hasPrivateKey()) throw new IllegalStateException("No dev.key");
        String priv = devKeyLines()[0];
        long today = today();
        long expiry = lifetime ? 0 : today + Math.max(1, days);
        String token = KruxLicense.create(priv, lifetime ? KruxLicense.LIFETIME : KruxLicense.TIMED, today, expiry);

        KruxLicense.Check c = KruxLicense.verify(token, KruxPublicKey.PUBLIC_KEY, today);
        String id = c.info() != null ? c.info().id() : "?";
        String line = Instant.now() + " | " + id + " | " + (lifetime ? "LIFETIME" : "TIMED until " + LocalDate.ofEpochDay(expiry))
                + (note == null || note.isBlank() ? "" : " | " + note.trim()) + System.lineSeparator();
        Files.createDirectories(dir());
        Files.writeString(issuedLog(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return token;
    }

    // ------------------------------------------------------------------ integracja z grą

    public static Screen gateScreen(Screen parent) {
        return needsSetup() ? new KruxDevScreen(parent) : new LicenseScreen(parent);
    }

    public static void tick(Minecraft mc) {
        // wygaśnięcie licencji czasowej w trakcie gry
        if (++tickCounter % 1200 == 0 && licensed && !dev && info != null && !info.lifetime() && today() > info.expiryDay()) {
            licensed = false;
            status = "License expired";
            for (Module m : Modules.ALL) m.setEnabled(false);
        }

        // ekran licencji przy pierwszym wejściu na ekran tytułowy
        if (!licensed() && !promptShown && mc.screen instanceof TitleScreen) {
            promptShown = true;
            mc.setScreen(gateScreen(mc.screen));
        }
    }
}
