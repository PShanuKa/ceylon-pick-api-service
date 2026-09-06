package lk.ceylonpick.auth.service;

import lk.ceylonpick.auth.domain.AppUser;

/**
 * What a sign-in attempt produced: either a session, or a demand for the second
 * factor (mandatory for admins, optional for creators and vendors — FR-AUTH-02).
 */
public sealed interface LoginOutcome {

    record SessionIssued(AppUser user, IssuedSession session) implements LoginOutcome {
    }

    /**
     * No cookies are set yet. The client must post the code back against
     * {@code challengeId}.
     *
     * @param devCode populated only when {@code ceylonpick.auth.otp.expose-code}
     *                is on, which never happens outside dev
     */
    record OtpRequired(String challengeId, String maskedPhone, String devCode) implements LoginOutcome {
    }
}
