package com.rods.backtestingstrategies.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret); // Actually I used HEX in current example, but standard is
                                                          // often Base64. Let's stick to simple Bytes for now or assume
                                                          // secret is compatible.
        // Wait, I put a HEX string in application.yml?
        // Let's modify the code to parse hex if I used hex, or better, just use the
        // string bytes if it's simpler.
        // Actually, the standard JJWT way is to use a Base64 encoded key or just raw
        // bytes.
        // To be safe and consistent with the hex I generated: 5367566B5970...
        // Hex decoding is safer for the string I put. But Decoders.BASE64 expects
        // Base64.
        // Let's assume I'll change the secret in YAML to a proper Base64 or just use
        // string bytes.
        // For simplicity, let's just use
        // Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)) and expect the user to
        // provide a Base64 secret, OR
        // Use the hex decoder if I want to support that specific hex string.
        // I'll stick to Base64 for standardness.
        // Wait, the secret I put `5367...` is clearly hex.
        // I will change the code to use `Decoders.BASE64` and I will update the secret
        // in YAML to a Base64 string to be clean.
        // Or I can just use `secret.getBytes()` if I don't care about the format.
        // Let's go with standard Base64.
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
