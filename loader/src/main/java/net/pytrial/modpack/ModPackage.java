package net.pytrial.modpack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class ModPackage {

    public static final String EXTENSION = ".pytm";

    private static final byte[] MAGIC = {'P', 'Y', 'T', 'M'};
    private static final int VERSION = 1;
    private static final int SALT_LENGTH = 16;
    private static final int CHECKSUM_LENGTH = 32;
    private static final int HEADER_LENGTH = MAGIC.length + 1 + SALT_LENGTH + CHECKSUM_LENGTH;
    private static final byte[] KEY = sha256("pyTrial mod package v1".getBytes(StandardCharsets.UTF_8));

    private ModPackage() {
    }

    public static boolean isPackage(Path file) {
        return file.getFileName().toString().endsWith(EXTENSION);
    }

    public static byte[] read(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        if (data.length <= HEADER_LENGTH || !matchesMagic(data)) {
            throw new IOException("not a pyTrial package");
        }
        int version = data[MAGIC.length] & 0xFF;
        if (version != VERSION) {
            throw new IOException("unsupported package version " + version);
        }
        int start = MAGIC.length + 1;
        byte[] salt = Arrays.copyOfRange(data, start, start + SALT_LENGTH);
        byte[] checksum = Arrays.copyOfRange(data, start + SALT_LENGTH, HEADER_LENGTH);
        byte[] body = Arrays.copyOfRange(data, HEADER_LENGTH, data.length);
        byte[] plain = xor(body, keystream(salt, body.length));
        if (!MessageDigest.isEqual(checksum, sha256(plain))) {
            throw new IOException("package checksum mismatch, the file is damaged");
        }
        return plain;
    }

    private static boolean matchesMagic(byte[] data) {
        for (int index = 0; index < MAGIC.length; index++) {
            if (data[index] != MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] keystream(byte[] salt, int length) {
        byte[] stream = new byte[length];
        int offset = 0;
        int counter = 0;
        while (offset < length) {
            MessageDigest digest = digest();
            digest.update(KEY);
            digest.update(salt);
            digest.update(new byte[]{
                    (byte) counter, (byte) (counter >>> 8), (byte) (counter >>> 16), (byte) (counter >>> 24)});
            byte[] block = digest.digest();
            int chunk = Math.min(block.length, length - offset);
            System.arraycopy(block, 0, stream, offset, chunk);
            offset += chunk;
            counter++;
        }
        return stream;
    }

    private static byte[] xor(byte[] data, byte[] stream) {
        byte[] result = new byte[data.length];
        for (int index = 0; index < data.length; index++) {
            result[index] = (byte) (data[index] ^ stream[index]);
        }
        return result;
    }

    private static byte[] sha256(byte[] data) {
        return digest().digest(data);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
