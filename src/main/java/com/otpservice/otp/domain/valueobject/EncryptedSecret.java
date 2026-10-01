package com.otpservice.otp.domain.valueobject;

public record EncryptedSecret(byte[] ciphertext, byte[] nonce) {

    public EncryptedSecret {
        if (ciphertext == null || ciphertext.length == 0 || nonce == null || nonce.length == 0) {
            throw new IllegalArgumentException("El secreto cifrado está incompleto");
        }
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }

    @Override
    public String toString() {
        return "EncryptedSecret[oculto]";
    }
}
