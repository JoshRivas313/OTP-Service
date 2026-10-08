package com.otpservice.otp.adapter.config;

import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoModeGuardTest {

    private static DemoModeGuard guard(boolean demoMode, SmsProvider sms, EmailProvider email) {
        OtpProperties otp = TestOtpProperties.otp().demoMode(demoMode).build();
        SmsProperties smsProperties = new SmsProperties(sms,
                new SmsProperties.Twilio("", "", ""), new SmsProperties.Infobip("", "", ""));
        EmailProperties emailProperties = new EmailProperties(email, "Un Solo Uso", "",
                new EmailProperties.Brevo("", "https://api.brevo.com"));
        return new DemoModeGuard(otp, smsProperties, emailProperties);
    }

    @Test
    void demoModeIsAllowedWithConsoleProviders() {
        assertThatCode(() -> guard(true, SmsProvider.CONSOLE, EmailProvider.CONSOLE)
                .checkNotCombinedWithRealProvider()).doesNotThrowAnyException();
    }

    @Test
    void realProvidersAreAllowedWithoutDemoMode() {
        assertThatCode(() -> guard(false, SmsProvider.TWILIO, EmailProvider.BREVO)
                .checkNotCombinedWithRealProvider()).doesNotThrowAnyException();
    }

    @Test
    void demoModeIsRejectedWithARealSmsProvider() {
        assertThatThrownBy(() -> guard(true, SmsProvider.TWILIO, EmailProvider.CONSOLE)
                .checkNotCombinedWithRealProvider())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sms.provider=twilio");
        assertThatThrownBy(() -> guard(true, SmsProvider.INFOBIP, EmailProvider.CONSOLE)
                .checkNotCombinedWithRealProvider())
                .hasMessageContaining("sms.provider=infobip");
    }

    @Test
    void demoModeIsRejectedWithARealEmailProvider() {
        assertThatThrownBy(() -> guard(true, SmsProvider.CONSOLE, EmailProvider.BREVO)
                .checkNotCombinedWithRealProvider())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email.provider=brevo");
    }
}
