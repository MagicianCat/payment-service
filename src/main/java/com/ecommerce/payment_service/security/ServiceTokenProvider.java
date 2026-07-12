package com.ecommerce.payment_service.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// Generates short-lived service-to-service JWTs so payment-service can call
// other secured services (e.g. order-service) from background/scheduled contexts,
// where there is no end-user request to propagate a token from.
@Component
public class ServiceTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    private static final long SERVICE_TOKEN_VALIDITY_MS = 5 * 60 * 1000; // 5 minutes — short-lived by design

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateServiceToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "SERVICE");

        return Jwts.builder()
                .claims(claims)
                .subject("payment-service")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SERVICE_TOKEN_VALIDITY_MS))
                .signWith(getSigningKey())
                .compact();
    }
}