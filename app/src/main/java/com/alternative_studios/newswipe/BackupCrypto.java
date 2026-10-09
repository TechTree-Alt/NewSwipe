package com.alternative_studios.newswipe;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 설정 백업 파일을 비밀번호로 암호화한다.
 *
 * <p>비밀번호에서 PBKDF2-HMAC-SHA256(무작위 솔트 16바이트, 반복 횟수는 파일에 기록)으로 256비트 키를 만들고,
 * AES-256-GCM(무작위 IV 12바이트)으로 암호화한다. GCM은 내용이 바뀌거나 비밀번호가 틀리면 복호화 자체가 실패하므로,
 * 틀린 비밀번호로 엉뚱한 설정이 적용되는 일이 없다. 반복 횟수는 휴대전화에서 1초 안팎이 걸리도록 정해,
 * 비밀번호를 하나씩 넣어 보는 공격을 느리게 만든다.
 * 안드로이드 의존성이 없어 단위 테스트할 수 있다.
 */
public final class BackupCrypto {
    public static final int MIN_PASSWORD = 6;
    public static final int MAX_PASSWORD = 128;
    /** 새로 암호화할 때의 반복 횟수. 가져올 때는 파일에 적힌 값을 쓴다. */
    static final int ITERATIONS = 310_000;
    /** 손상되거나 조작된 파일이 지나치게 오래 걸리거나 약한 값을 쓰지 못하게 막는 범위. */
    static final int MIN_ITERATIONS = 100_000, MAX_ITERATIONS = 5_000_000;
    private static final int SALT_BYTES = 16, IV_BYTES = 12, KEY_BITS = 256, TAG_BITS = 128;

    /** 암호화 결과 (모두 Base64가 아니라 원래 바이트). */
    public static final class Sealed {
        public final int iterations;
        public final byte[] salt, iv, data;

        Sealed(int iterations, byte[] salt, byte[] iv, byte[] data) {
            this.iterations = iterations;
            this.salt = salt;
            this.iv = iv;
            this.data = data;
        }
    }

    /** 비밀번호가 틀렸거나 파일이 손상되었다. */
    public static final class WrongPasswordException extends Exception {
        WrongPasswordException() {
            super("비밀번호가 맞지 않거나 파일이 손상되었습니다");
        }
    }

    private BackupCrypto() {
    }

    /**
     * 비밀번호 규칙: 6자 이상, 영문·숫자·특수문자(공백 제외 ASCII)만. 맞으면 null, 아니면 안내 문구.
     */
    public static String checkPassword(CharSequence pw) {
        if (pw.length() < MIN_PASSWORD) return "비밀번호는 " + MIN_PASSWORD + "자 이상이어야 합니다";
        if (pw.length() > MAX_PASSWORD) return "비밀번호가 너무 깁니다";
        for (int i = 0; i < pw.length(); i++) {
            char c = pw.charAt(i);
            if (c < 0x21 || c > 0x7E) return "비밀번호에는 영문, 숫자, 특수문자만 쓸 수 있습니다 (공백 제외)";
        }
        return null;
    }

    public static Sealed encrypt(byte[] plain, char[] password, byte[] aad) throws GeneralSecurityException {
        return encrypt(plain, password, aad, ITERATIONS);
    }

    static Sealed encrypt(byte[] plain, char[] password, byte[] aad, int iterations) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);
        byte[] key = deriveKey(password, salt, iterations);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) cipher.updateAAD(aad);
            return new Sealed(iterations, salt, iv, cipher.doFinal(plain));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static byte[] decrypt(Sealed sealed, char[] password, byte[] aad)
            throws GeneralSecurityException, WrongPasswordException {
        if (sealed.iterations < MIN_ITERATIONS || sealed.iterations > MAX_ITERATIONS
                || sealed.salt.length != SALT_BYTES || sealed.iv.length != IV_BYTES) {
            throw new GeneralSecurityException("암호화 정보가 올바르지 않습니다");
        }
        byte[] key = deriveKey(password, sealed.salt, sealed.iterations);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, sealed.iv));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(sealed.data);
        } catch (AEADBadTagException e) {
            throw new WrongPasswordException();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] deriveKey(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    static byte[] unb64(String s) {
        return Base64.getDecoder().decode(s);
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
