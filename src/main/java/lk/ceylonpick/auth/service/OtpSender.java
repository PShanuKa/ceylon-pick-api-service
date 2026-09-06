package lk.ceylonpick.auth.service;

import lk.ceylonpick.auth.domain.OtpChallenge;

/**
 * Delivers an OTP code. IF-04 / FR-NOT-01: SMS primary, WhatsApp alternative,
 * within 30 seconds.
 *
 * <p>A direct call for now. Architecture §4 has notifications consuming domain
 * events, so when that module lands this becomes an outbox event rather than an
 * in-line send — the interface is here so only the implementation moves.
 */
public interface OtpSender {

    void send(OtpChallenge challenge, String plainCode);
}
