package com.alternative_studios.newswipe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class BackupCryptoTest {
    private static final byte[] AAD = "aad".getBytes(StandardCharsets.UTF_8);
    // 테스트를 빨리 돌리려고 허용 범위의 가장 작은 반복 횟수를 쓴다.
    private static final int FAST = BackupCrypto.MIN_ITERATIONS;

    @Test
    public void roundTrip() throws Exception {
        byte[] plain = "{\"a\":\"학습한 단어\"}".getBytes(StandardCharsets.UTF_8);
        BackupCrypto.Sealed s = BackupCrypto.encrypt(plain, "pass!23".toCharArray(), AAD, FAST);
        assertFalse(new String(s.data, StandardCharsets.UTF_8).contains("학습"));
        assertArrayEquals(plain, BackupCrypto.decrypt(s, "pass!23".toCharArray(), AAD));
    }

    @Test
    public void saltAndIvAreRandom() throws Exception {
        byte[] plain = "same".getBytes(StandardCharsets.UTF_8);
        BackupCrypto.Sealed a = BackupCrypto.encrypt(plain, "abcdef".toCharArray(), AAD, FAST);
        BackupCrypto.Sealed b = BackupCrypto.encrypt(plain, "abcdef".toCharArray(), AAD, FAST);
        assertFalse(java.util.Arrays.equals(a.salt, b.salt));
        assertFalse(java.util.Arrays.equals(a.data, b.data));
    }

    @Test
    public void wrongPasswordTamperingAndAadFail() throws Exception {
        byte[] plain = "secret".getBytes(StandardCharsets.UTF_8);
        BackupCrypto.Sealed s = BackupCrypto.encrypt(plain, "right1".toCharArray(), AAD, FAST);
        expectWrong(s, "wrong1", AAD);
        expectWrong(s, "right1", "other".getBytes(StandardCharsets.UTF_8));
        s.data[0] ^= 1;
        expectWrong(s, "right1", AAD);
    }

    private static void expectWrong(BackupCrypto.Sealed s, String pw, byte[] aad) throws Exception {
        try {
            BackupCrypto.decrypt(s, pw.toCharArray(), aad);
            fail("복호화되면 안 됩니다");
        } catch (BackupCrypto.WrongPasswordException expected) {
            // 기대한 대로
        }
    }

    @Test
    public void rejectsTooFewIterations() throws Exception {
        BackupCrypto.Sealed s = BackupCrypto.encrypt("x".getBytes(StandardCharsets.UTF_8), "abcdef".toCharArray(), AAD, 1000);
        try {
            BackupCrypto.decrypt(s, "abcdef".toCharArray(), AAD);
            fail();
        } catch (java.security.GeneralSecurityException expected) {
            // 약한 반복 횟수로 만든 파일은 받지 않는다
        }
    }

    @Test
    public void passwordRules() {
        assertNotNull(BackupCrypto.checkPassword("abc12"));
        assertNull(BackupCrypto.checkPassword("abc123"));
        assertNull(BackupCrypto.checkPassword("A1!@#$%^&*()_+-=[]{};:'\",.<>/?\\|`~"));
        assertNotNull(BackupCrypto.checkPassword("abc 123"));
        assertNotNull(BackupCrypto.checkPassword("비밀번호123"));
    }

    @Test
    public void defaultIterationsTakeReasonableTimeOnJvm() throws Exception {
        long t = System.nanoTime();
        BackupCrypto.Sealed s = BackupCrypto.encrypt(new byte[10], "abcdef".toCharArray(), AAD);
        assertEquals(BackupCrypto.ITERATIONS, s.iterations);
        System.out.println("PBKDF2 " + BackupCrypto.ITERATIONS + "회: " + (System.nanoTime() - t) / 1_000_000 + "ms");
    }
}
