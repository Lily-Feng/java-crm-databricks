package com.minicrm.application.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * §5.1's {@code request_hash} column: a stable digest of a request's content, used to
 * tell "the same request replayed" apart from "a different request reusing the same
 * Idempotency-Key" (§5.2) without storing and re-comparing the full request body.
 */
public final class RequestHash {

    private RequestHash() {
    }

    public static String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every supported JVM", e);
        }
    }
}
