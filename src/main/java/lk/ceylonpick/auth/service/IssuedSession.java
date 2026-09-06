package lk.ceylonpick.auth.service;

import java.time.Duration;

/** A freshly minted pair of tokens, ready to be written as cookies. */
public record IssuedSession(
        String accessToken,
        Duration accessTtl,
        String refreshToken,
        Duration refreshTtl) {
}
