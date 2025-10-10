package com.gps.enlace.mirror;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TokenGenerator {
    private static final char[] ALPH = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private final SecureRandom rnd = new SecureRandom();

    public String generate(int len) {
        char[] out = new char[len];
        for (int i = 0; i < len; i++) out[i] = ALPH[rnd.nextInt(ALPH.length)];
        return new String(out);
    }
}
