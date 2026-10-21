package com.example.storagefinder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Licencje podpisane Ed25519 (czysta Java, bez Minecrafta).
 * Klucz publiczny jest w modzie, prywatny jest tylko w jarze developer — bez prywatnego klucza
 * nie da się wygenerować działającej licencji.
 *
 * Format klucza: KRUX-XXXXXXXX-XXXXXXXX-... (Base32 z 80 bajtów: 16 bajtów danych + 64 bajty podpisu).
 * Dane: wersja(1) typ(1) dzień-wydania(4) dzień-wygaśnięcia(4) losowe-id(6).
 */
public final class KruxLicense {
    public static final int LIFETIME = 1, TIMED = 2;
    private static final int VERSION = 1;
    private static final int PAYLOAD_LEN = 16, SIG_LEN = 64;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public record Info(int type, long issuedDay, long expiryDay, String id) {
        public boolean lifetime() { return type == LIFETIME; }
    }

    public enum Result { VALID, MALFORMED, BAD_SIGNATURE, EXPIRED, NOT_CONFIGURED }

    public record Check(Result result, Info info) {}

    public record KeyPairB64(String publicKey, String privateKey) {}

    private KruxLicense() {}

    // ------------------------------------------------------------------ klucze

    public static KeyPairB64 generateKeyPair() throws GeneralSecurityException {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        return new KeyPairB64(
                Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
    }

    public static PublicKey decodePublic(String b64) throws GeneralSecurityException {
        byte[] raw = Base64.getDecoder().decode(b64.trim());
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(raw));
    }

    public static PrivateKey decodePrivate(String b64) throws GeneralSecurityException {
        byte[] raw = Base64.getDecoder().decode(b64.trim());
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(raw));
    }

    /** Czy prywatny klucz pasuje do publicznego (podpisuje testową wiadomość i weryfikuje). */
    public static boolean keysMatch(String privB64, String pubB64) {
        try {
            byte[] msg = "krux-key-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(decodePrivate(privB64));
            s.update(msg);
            byte[] sig = s.sign();
            Signature v = Signature.getInstance("Ed25519");
            v.initVerify(decodePublic(pubB64));
            v.update(msg);
            return v.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ tworzenie / weryfikacja

    /** expiryDay = 0 dla licencji lifetime. */
    public static String create(String privB64, int type, long issuedDay, long expiryDay) throws GeneralSecurityException {
        byte[] id = new byte[6];
        new SecureRandom().nextBytes(id);
        ByteBuffer bb = ByteBuffer.allocate(PAYLOAD_LEN);
        bb.put((byte) VERSION).put((byte) type).putInt((int) issuedDay).putInt((int) expiryDay).put(id);
        byte[] payload = bb.array();

        Signature s = Signature.getInstance("Ed25519");
        s.initSign(decodePrivate(privB64));
        s.update(payload);
        byte[] sig = s.sign();

        byte[] all = new byte[PAYLOAD_LEN + sig.length];
        System.arraycopy(payload, 0, all, 0, PAYLOAD_LEN);
        System.arraycopy(sig, 0, all, PAYLOAD_LEN, sig.length);
        return format(base32(all));
    }

    public static Check verify(String token, String pubB64, long today) {
        PublicKey pub;
        try {
            pub = decodePublic(pubB64);
        } catch (Exception e) {
            return new Check(Result.NOT_CONFIGURED, null);
        }

        byte[] all = parse(token);
        if (all == null || all.length != PAYLOAD_LEN + SIG_LEN) return new Check(Result.MALFORMED, null);

        byte[] payload = new byte[PAYLOAD_LEN];
        byte[] sig = new byte[SIG_LEN];
        System.arraycopy(all, 0, payload, 0, PAYLOAD_LEN);
        System.arraycopy(all, PAYLOAD_LEN, sig, 0, SIG_LEN);

        try {
            Signature v = Signature.getInstance("Ed25519");
            v.initVerify(pub);
            v.update(payload);
            if (!v.verify(sig)) return new Check(Result.BAD_SIGNATURE, null);
        } catch (Exception e) {
            return new Check(Result.BAD_SIGNATURE, null);
        }

        ByteBuffer bb = ByteBuffer.wrap(payload);
        int ver = bb.get() & 0xFF;
        int type = bb.get() & 0xFF;
        long issued = bb.getInt();
        long expiry = bb.getInt();
        byte[] id = new byte[6];
        bb.get(id);
        if (ver != VERSION || (type != LIFETIME && type != TIMED)) return new Check(Result.MALFORMED, null);

        StringBuilder hex = new StringBuilder();
        for (byte b : id) hex.append(String.format("%02X", b));
        Info info = new Info(type, issued, type == LIFETIME ? 0 : expiry, hex.toString());

        if (type == TIMED && today > expiry) return new Check(Result.EXPIRED, info);
        return new Check(Result.VALID, info);
    }

    // ------------------------------------------------------------------ Base32 + formatowanie

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    private static byte[] unbase32(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = ALPHABET.indexOf(s.charAt(i));
            if (v < 0) return null;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String format(String b32) {
        StringBuilder sb = new StringBuilder("KRUX");
        for (int i = 0; i < b32.length(); i += 8) {
            sb.append('-').append(b32, i, Math.min(b32.length(), i + 8));
        }
        return sb.toString();
    }

    /** Ignoruje spacje, myślniki i wielkość liter; prefiks KRUX jest opcjonalny. */
    private static byte[] parse(String token) {
        if (token == null) return null;
        String t = token.toUpperCase().replaceAll("[\\s\\-]", "");
        int b32Len = ((PAYLOAD_LEN + SIG_LEN) * 8 + 4) / 5;
        if (t.length() == b32Len + 4 && t.startsWith("KRUX")) t = t.substring(4);
        if (t.length() != b32Len) return null;
        return unbase32(t);
    }
}
