package com.otpservice.otp.application.protocol;

import com.otpservice.otp.domain.valueobject.OtpProtocol;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CodeProtocols {

    private final Map<OtpProtocol, CodeProtocol> byProtocol = new EnumMap<>(OtpProtocol.class);

    public CodeProtocols(List<CodeProtocol> protocols) {
        protocols.forEach(protocol -> byProtocol.put(protocol.protocol(), protocol));
    }

    public CodeProtocol get(OtpProtocol protocol) {
        CodeProtocol found = byProtocol.get(protocol);
        if (found == null) {
            throw new IllegalStateException("No hay implementación para el protocolo " + protocol);
        }
        return found;
    }
}
