package pt.diamondcars.dcbobackend.config.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Test-only support for exercising {@link
 * pt.diamondcars.dcbobackend.config.SecurityConfig} end-to-end (TASK-007 requirement 7) without
 * ever contacting the real Auth0 issuer: builds a {@link JwtDecoder} backed by a locally generated
 * HMAC key, and signs compact JWTs with that same key, so a test can assert on how the resource
 * server reacts to a token it can actually decode and validate.
 *
 * <p>This is deliberately separate from Spring Boot's own auto-configured {@code JwtDecoder}
 * (which stays wired to the placeholder {@code AUTH0_ISSUER_URI} in production and is never
 * invoked by tests that swap this one in instead, see {@code SecurityConfigTest}).
 */
public final class TestJwtSupport {

	/** Audience the test decoder accepts; use {@link #signedToken} with a different one to
	 * exercise the audience-rejection path. */
	public static final String VALID_AUDIENCE = "https://dcbo-backend-tests/";

	private static final SecretKey SECRET_KEY = generateHmacKey();

	private TestJwtSupport() {}

	/**
	 * Builds a {@link JwtDecoder} that validates the standard timestamp claims plus a single
	 * audience, entirely offline (HMAC, no network).
	 *
	 * @param expectedAudience the only audience value this decoder accepts
	 * @return the configured decoder
	 */
	public static JwtDecoder decoderAcceptingAudience(String expectedAudience) {
		NimbusJwtDecoder decoder =
				NimbusJwtDecoder.withSecretKey(SECRET_KEY).macAlgorithm(MacAlgorithm.HS256).build();
		OAuth2TokenValidator<Jwt> audienceValidator =
				new JwtClaimValidator<List<String>>(
						JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(expectedAudience));
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(audienceValidator));
		return decoder;
	}

	/**
	 * Signs a compact JWT with the key {@link #decoderAcceptingAudience} validates against, with a
	 * 5-minute expiry from now.
	 *
	 * @param subject the {@code sub} claim
	 * @param audience the {@code aud} claim
	 * @return the signed, compact JWT
	 */
	public static String signedToken(String subject, List<String> audience) {
		try {
			JWTClaimsSet claims =
					new JWTClaimsSet.Builder()
							.subject(subject)
							.audience(audience)
							.issueTime(Date.from(Instant.now()))
							.expirationTime(Date.from(Instant.now().plusSeconds(300)))
							.build();
			SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			signedJwt.sign(new MACSigner(SECRET_KEY.getEncoded()));
			return signedJwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException("Failed to sign a test JWT", e);
		}
	}

	private static SecretKey generateHmacKey() {
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
			keyGenerator.init(256);
			return keyGenerator.generateKey();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("HmacSHA256 must be available on any JVM", e);
		}
	}
}
