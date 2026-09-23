package com.cadentia.auth.api;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiscoveryController {

    private final RSAKey signingKey;

    public DiscoveryController(RSAKey signingKey) {
        this.signingKey = signingKey;
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        RSAKey publicKey = signingKey.toPublicJWK();
        return Map.of("keys", new Object[]{Map.of(
                "kty", publicKey.getKeyType().getValue(),
                "kid", publicKey.getKeyID(),
                "use", "sig",
                "alg", "RS256",
                "n", publicKey.getModulus().toString(),
                "e", publicKey.getPublicExponent().toString())});
    }
}
