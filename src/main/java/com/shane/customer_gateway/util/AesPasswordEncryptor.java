package com.shane.customer_gateway.util;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class AesPasswordEncryptor {

    // 算法参数
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    // GCM 认证标签长度
    private static final int GCM_TAG_LENGTH = 128;
    // GCM 推荐 12 字节 IV
    private static final int IV_LENGTH = 12;

    // AES 密钥
    private static final String AES_SECRET = "XwMm$teIdukPCZgQ8LA225ZQIVP!YLl9";
    private static final SecretKey SECRET_KEY = new SecretKeySpec(
            AES_SECRET.getBytes(StandardCharsets.UTF_8), "AES");



    /**
     * 加密方法
     * @param password  原始密码
     * @param timestamp 时间戳
     * @return Base64(IV + AES密文)
     */
    public static String encrypt(String password, long timestamp) throws Exception {
        // 1. 生成密码的 MD5 哈希
        byte[] passwordMd5 = MessageDigest.getInstance("MD5")
                .digest(password.getBytes(StandardCharsets.UTF_8));
        // 打印 加密的MD5
        System.out.println("加密 MD5: " + bytesToHex(passwordMd5));

        // 2. 构造明文数据：MD5 + 时间戳
        byte[] timestampBytes = ByteBuffer.allocate(8).putLong(timestamp).array();
        byte[] plaintext = ByteBuffer.allocate(passwordMd5.length + timestampBytes.length)
                .put(passwordMd5)
                .put(timestampBytes)
                .array();

        // 3. 生成随机 IV
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom.getInstanceStrong().nextBytes(iv);

        // 4. AES-GCM 加密
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, gcmSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        // 5. 组合 IV + 密文，Base64 编码
        byte[] encryptedData = ByteBuffer.allocate(iv.length + ciphertext.length)
                .put(iv)
                .put(ciphertext)
                .array();
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * 解密方法
     * @param encryptedData Base64(IV + AES密文)
     * @return 解密后的 MD5 和时间戳
     */
    public static DecryptedResult decrypt(String encryptedData) throws Exception {
        // 1. Base64 解码
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);

        // 2. 分离 IV 和密文
        ByteBuffer buffer = ByteBuffer.wrap(encryptedBytes);
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        // 3. AES-GCM 解密
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, gcmSpec);
        byte[] plaintext = cipher.doFinal(ciphertext);

        // 4. 分离 MD5 和时间戳
        ByteBuffer plainBuffer = ByteBuffer.wrap(plaintext);
        // MD5 为 16 字节
        byte[] passwordMd5 = new byte[16];
        plainBuffer.get(passwordMd5);
        long timestamp = plainBuffer.getLong();

        return new DecryptedResult(passwordMd5, timestamp);
    }

    // 解密结果包装类
    public static class DecryptedResult {
        private final byte[] passwordMd5;
        private final long timestamp;

        public DecryptedResult(byte[] passwordMd5, long timestamp) {
            this.passwordMd5 = passwordMd5;
            this.timestamp = timestamp;
        }

        // 可添加验证方法，例如检查时间戳是否在合理范围内
        public boolean isTimestampValid() {
            long current = System.currentTimeMillis();
            return Math.abs(current - timestamp) < 5 * 60 * 1000; // ±5分钟
        }
    }

    public static void main(String[] args) throws Exception {
        String password = "userPassword123";
        long timestamp = System.currentTimeMillis();

        // 加密示例
        System.out.println("原始密码: " + password);
        String encrypted = encrypt(password, timestamp);
        System.out.println("加密结果: " + encrypted);

        // 解密示例
        DecryptedResult result = decrypt(encrypted);
        System.out.println("解密 MD5: " + bytesToHex(result.passwordMd5));
        System.out.println("解密时间戳: " + result.timestamp);
        System.out.println("时间有效性: " + result.isTimestampValid());
    }

    // 辅助方法：字节数组转十六进制
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
