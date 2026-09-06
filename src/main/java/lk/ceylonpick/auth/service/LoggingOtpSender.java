package lk.ceylonpick.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lk.ceylonpick.auth.domain.OtpChallenge;

/**
 * Stand-in until an SMS/WhatsApp provider is wired up: logs the code instead of
 * sending it. It logs at WARN so that if this is ever running outside dev, the
 * fact is loud.
 *
 * <p>When the real adapter arrives, mark it {@code @Primary} — this bean stays
 * as the fallback for local runs with no provider credentials.
 */
@Component
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(OtpChallenge challenge, String plainCode) {
        log.warn("No OTP provider configured. {} code for {} is {} (challenge {})",
                challenge.getPurpose(), challenge.getPhone(), plainCode, challenge.getId());
    }
}
