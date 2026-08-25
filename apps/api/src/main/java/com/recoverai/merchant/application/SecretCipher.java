package com.recoverai.merchant.application;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for provider secrets at rest. The master key is derived from
 * ENCRYPTION_KEY via PBKDF2. Format: base64(iv(12) + ciphertext + tag).
 */
@Component
public class SecretCipher {

  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private final SecretKey key;

  public SecretCipher(@Value("${recoverai.encryption.key}") String masterKey) {
    this.key = deriveKey(masterKey);
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_BYTES];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  public String decrypt(String encoded) {
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      byte[] iv = java.util.Arrays.copyOfRange(all, 0, IV_BYTES);
      byte[] ciphertext = java.util.Arrays.copyOfRange(all, IV_BYTES, all.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Decryption failed", e);
    }
  }

  private static SecretKey deriveKey(String masterKey) {
    try {
      byte[] salt = "recoverai-secrets".getBytes(StandardCharsets.UTF_8);
      PBEKeySpec spec = new PBEKeySpec(masterKey.toCharArray(), salt, 100_000, 256);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Key derivation failed", e);
    }
  }
}
