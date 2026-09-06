package lk.ceylonpick.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.ceylonpick.auth.AuthProperties;
import lk.ceylonpick.auth.api.OrderOtp;
import lk.ceylonpick.auth.domain.OtpChallenge;
import lk.ceylonpick.shared.Phones;
import lk.ceylonpick.shared.web.ApiException;

/** Auth's side of {@link OrderOtp}; the limits all come from {@link OtpService}. */
@Service
public class OrderOtpService implements OrderOtp {

    private final OtpService otp;
    private final AuthProperties properties;

    public OrderOtpService(OtpService otp, AuthProperties properties) {
        this.otp = otp;
        this.properties = properties;
    }

    @Override
    @Transactional
    public IssuedOrderOtp issue(String orderId, String phone) {
        String normalised = Phones.normalise(phone);
        if (normalised == null) {
            throw ApiException.badRequest("INVALID_PHONE", "Enter a valid Sri Lankan mobile number");
        }
        var issued = otp.issue(OtpChallenge.Purpose.ORDER_CONFIRM, normalised, null, orderId, null);
        return toResponse(issued);
    }

    @Override
    @Transactional
    public void confirm(String challengeId, String code, String orderId) {
        OtpChallenge verified = otp.verify(challengeId, code, OtpChallenge.Purpose.ORDER_CONFIRM);
        if (!orderId.equals(verified.getOrderId())) {
            // A code issued for a different order must not confirm this one.
            throw ApiException.badRequest("OTP_WRONG_ORDER", "This code belongs to another order");
        }
        otp.consume(verified);
    }

    @Override
    @Transactional
    public IssuedOrderOtp resend(String challengeId) {
        return toResponse(otp.resend(challengeId));
    }

    private IssuedOrderOtp toResponse(OtpService.IssuedOtp issued) {
        return new IssuedOrderOtp(
                issued.challenge().getId(),
                Phones.mask(issued.challenge().getPhone()),
                properties.otp().exposeCode() ? issued.plainCode() : null);
    }
}
