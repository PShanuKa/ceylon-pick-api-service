package lk.ceylonpick.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.domain.AuditLogEntry;
import lk.ceylonpick.auth.domain.OtpChallenge;
import lk.ceylonpick.auth.repo.OtpChallengeRepository;
import lk.ceylonpick.shared.web.ApiException;
import lk.ceylonpick.shared.Hashes;
import lk.ceylonpick.shared.Ids;

/**
 * Issues and verifies one-time codes.
 *
 * <p>BR-07 fixes the numbers: 6 digits, 10-minute expiry, at most 3 attempts and
 * 3 resends. FR-NOT-01 / IF-04 add a cap of 3 codes per phone per hour, counted
 * from the table rather than an in-memory limiter so it survives a restart and
 * stays correct if a second instance is ever added.
 *
 * <p>Only the salted SHA-256 of the code is stored (Architecture §5), and
 * comparison is constant-time.
 */
@Service
public class OtpService {

    /** A confirmed challenge must be exchanged promptly; it is not a second session. */
    private static final Duration CONFIRMATION_VALIDITY = Duration.ofMinutes(5);

    private final OtpChallengeRepository challenges;
    private final OtpSender sender;
    private final AuditService audit;
    private final AuthProperties properties;
    private final Clock clock;

    public OtpService(OtpChallengeRepository challenges,
                      OtpSender sender,
                      AuditService audit,
                      AuthProperties properties,
                      Clock clock) {
        this.challenges = challenges;
        this.sender = sender;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** The challenge plus its plain code, which is returned only so dev can echo it. */
    public record IssuedOtp(OtpChallenge challenge, String plainCode) {
    }

    @Transactional
    public IssuedOtp issue(OtpChallenge.Purpose purpose, String phone, String userId,
                           String orderId, OtpChallenge.Channel channel) {
        Instant now = clock.instant();
        enforcePerPhoneHourlyCap(phone, purpose, now);

        AuthProperties.Otp config = properties.otp();
        String code = Hashes.randomNumericCode(config.length());
        String salt = Hashes.randomToken(16);

        OtpChallenge challenge = new OtpChallenge();
        challenge.setId(Ids.newId());
        challenge.setPurpose(purpose);
        challenge.setPhone(phone);
        challenge.setUserId(userId);
        challenge.setOrderId(orderId);
        challenge.setSalt(salt);
        challenge.setCodeHash(Hashes.sha256(salt, code));
        challenge.setChannel(channel == null ? OtpChallenge.Channel.SMS : channel);
        challenge.setExpiresAt(now.plus(config.ttl()));
        challenge.setCreatedAt(now);
        challenges.save(challenge);

        sender.send(challenge, code);
        audit.record(AuditLogEntry.OTP_ISSUED, userId, null, "otp_challenge", challenge.getId(), null);
        return new IssuedOtp(challenge, code);
    }

    /** BR-07: at most 3 resends, each still counting against the hourly cap. */
    @Transactional
    public IssuedOtp resend(String challengeId) {
        Instant now = clock.instant();
        OtpChallenge challenge = load(challengeId);
        if (challenge.isConfirmed() || challenge.isConsumed()) {
            throw ApiException.badRequest("OTP_ALREADY_USED", "This code has already been used");
        }
        if (challenge.getResends() >= properties.otp().maxResends()) {
            throw ApiException.tooManyRequests("OTP_RESEND_LIMIT", "No more resends for this request");
        }
        enforcePerPhoneHourlyCap(challenge.getPhone(), challenge.getPurpose(), now);

        String code = Hashes.randomNumericCode(properties.otp().length());
        String salt = Hashes.randomToken(16);
        challenge.setSalt(salt);
        challenge.setCodeHash(Hashes.sha256(salt, code));
        challenge.setAttempts(0);
        challenge.setResends(challenge.getResends() + 1);
        challenge.setExpiresAt(now.plus(properties.otp().ttl()));
        challenges.save(challenge);

        sender.send(challenge, code);
        return new IssuedOtp(challenge, code);
    }

    /**
     * Checks a submitted code and marks the challenge confirmed.
     *
     * <p>A wrong code is counted before the failure is reported, in a way that
     * survives the caller's rollback.
     */
    @Transactional
    public OtpChallenge verify(String challengeId, String code, OtpChallenge.Purpose expectedPurpose) {
        Instant now = clock.instant();
        OtpChallenge challenge = load(challengeId);

        if (challenge.getPurpose() != expectedPurpose) {
            throw ApiException.badRequest("OTP_WRONG_PURPOSE", "This code cannot be used here");
        }
        if (challenge.isConsumed()) {
            throw ApiException.badRequest("OTP_ALREADY_USED", "This code has already been used");
        }
        if (challenge.isExpired(now)) {
            throw ApiException.badRequest("OTP_EXPIRED", "This code has expired. Request a new one.");
        }
        if (challenge.getAttempts() >= properties.otp().maxAttempts()) {
            throw ApiException.tooManyRequests("OTP_ATTEMPTS_EXCEEDED", "Too many attempts. Request a new code.");
        }

        if (!Hashes.matches(challenge.getCodeHash(), Hashes.sha256(challenge.getSalt(), code))) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            challenges.saveAndFlush(challenge);
            audit.record(AuditLogEntry.OTP_FAILED, challenge.getUserId(), null,
                    "otp_challenge", challenge.getId(), null);
            throw ApiException.badRequest("OTP_INVALID", "That code is not correct");
        }

        challenge.setConfirmedAt(now);
        return challenges.save(challenge);
    }

    /** Burns a confirmed challenge so the same confirmation cannot be replayed. */
    @Transactional
    public void consume(OtpChallenge challenge) {
        Instant now = clock.instant();
        if (challenge.getConfirmedAt() == null
                || challenge.getConfirmedAt().plus(CONFIRMATION_VALIDITY).isBefore(now)) {
            throw ApiException.badRequest("OTP_NOT_CONFIRMED", "Confirm the code again");
        }
        challenge.setConsumedAt(now);
        challenges.save(challenge);
    }

    /** Reads a challenge without touching its attempt counters. */
    @Transactional(readOnly = true)
    public OtpChallenge peek(String challengeId) {
        return load(challengeId);
    }

    /** FR-AUTH-04: has this user re-verified by OTP within {@code window}? */
    @Transactional(readOnly = true)
    public boolean hasFreshStepUp(String userId, OtpChallenge.Purpose purpose, Duration window) {
        return challenges.hasConfirmedSince(userId, purpose, clock.instant().minus(window));
    }

    private void enforcePerPhoneHourlyCap(String phone, OtpChallenge.Purpose purpose, Instant now) {
        long recent = challenges.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1)));
        if (recent >= hourlyCapFor(purpose)) {
            throw ApiException.tooManyRequests("OTP_RATE_LIMITED",
                    "Too many codes requested for this number. Try again in an hour.");
        }
    }

    /**
     * FR-NOT-01's cap of 3 per phone per hour is written for buyer-facing codes.
     * Staff sign-in shares the counter but not the ceiling: their second factor
     * is mandatory (FR-AUTH-02), so the buyer limit would lock an admin out of
     * the platform for an hour after three ordinary logins.
     */
    private int hourlyCapFor(OtpChallenge.Purpose purpose) {
        return switch (purpose) {
            case ADMIN_LOGIN, LOGIN_2FA, BANK_EDIT -> properties.otp().perStaffPhonePerHour();
            case ORDER_CONFIRM, BUYER_LOGIN, PHONE_VERIFY -> properties.otp().perPhonePerHour();
        };
    }

    private OtpChallenge load(String challengeId) {
        return challenges.findById(challengeId)
                .orElseThrow(() -> ApiException.badRequest("OTP_UNKNOWN", "That request has expired"));
    }
}
