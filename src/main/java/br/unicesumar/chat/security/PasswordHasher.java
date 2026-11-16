package br.unicesumar.chat.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Hash de senha com PBKDF2-HMAC-SHA256 + salt aleatório. Formato: pbkdf2$iteracoes$salt$hash (Base64). */
public final class PasswordHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACOES = 210_000;
    private static final int TAM_SALT = 16;
    private static final int TAM_HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String gerar(char[] senha) {
        byte[] salt = new byte[TAM_SALT];
        RANDOM.nextBytes(salt);
        byte[] hash = derivar(senha, salt, ITERACOES);
        return "pbkdf2$" + ITERACOES + "$" + b64(salt) + "$" + b64(hash);
    }

    public static boolean verificar(char[] senha, String armazenado) {
        if (armazenado == null) {
            return false;
        }
        String[] p = armazenado.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] esperado = Base64.getDecoder().decode(p[3]);
            byte[] obtido = derivar(senha, salt, iteracoes);
            return MessageDigest.isEqual(esperado, obtido); // comparação em tempo constante
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] senha, byte[] salt, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha, salt, iteracoes, TAM_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
