package com.fufexo.lvl.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import com.fufexo.lvl.ApiTest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTest extends ApiTest {

	@Test
	void signUpOpensBothAccountsWithTheWelcomeBonus() {
		Player player = signUp();

		assertThat(balance(player, player.pen())).isEqualByComparingTo("500.00");
		assertThat(balance(player, player.usd())).isEqualByComparingTo("50.00");
		assertLedgerIsConsistent();
	}

	@Test
	void refreshCookieIsHttpOnlyStrictAndScopedToAuth() {
		String gamertag = uniqueGamertag();
		MvcTestResult result = register(gamertag, gamertag + "@lvl.test", "contraseña-segura");

		String setCookie = result.getResponse().getHeader("Set-Cookie");
		assertThat(setCookie).contains("lvl_refresh=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
	}

	@Test
	void gamertagsAreUniqueIgnoringCase() {
		Player player = signUp();

		MvcTestResult result = register(player.gamertag().toUpperCase(), "otro@lvl.test", "contraseña-segura");

		assertThat(result).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("GAMERTAG_TAKEN"))
			.hasPathSatisfying("$.errors[0].field", (field) -> assertThat(field).asString().isEqualTo("gamertag"));
	}

	@Test
	void invalidSignUpReturnsAnErrorPerField() {
		MvcTestResult result = register("a", "no-es-correo", "corta");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("VALIDATION_FAILED"))
			.hasPathSatisfying("$.errors[*].field",
					(fields) -> assertThat(fields).asList().containsExactlyInAnyOrder("gamertag", "email", "password"));
	}

	@Test
	void logsInWithGamertagOrEmail() {
		Player player = signUp();

		assertThat(login(player.gamertag().toUpperCase(), "contraseña-segura")).hasStatusOk();
		assertThat(login(player.gamertag() + "@lvl.test", "contraseña-segura")).hasStatusOk();
	}

	@Test
	void wrongPasswordAndUnknownUserGetTheSameAnswer() {
		Player player = signUp();

		MvcTestResult wrongPassword = login(player.gamertag(), "otra-contraseña");
		MvcTestResult unknownUser = login("nadie_" + uniqueGamertag(), "otra-contraseña");

		assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(unknownUser).hasStatus(HttpStatus.UNAUTHORIZED);
		// Same body: the response does not reveal whether the gamertag exists.
		assertThat(body(wrongPassword)).isEqualTo(body(unknownUser));
	}

	@Test
	void repeatedFailuresPauseSignInForThatAccount() {
		Player player = signUp();
		for (int i = 0; i < 5; i++) {
			assertThat(login(player.gamertag(), "otra-contraseña")).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		// Even the right password waits until the window passes: guessing cannot continue.
		assertThat(login(player.gamertag(), "contraseña-segura")).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("TOO_MANY_ATTEMPTS"));
	}

	@Test
	void passwordsBcryptCannotFullyReadAreRejectedOnTheField() {
		// 40 characters but 80 bytes in UTF-8: above BCrypt's 72-byte limit.
		String password = "ñ".repeat(40);
		String gamertag = uniqueGamertag();

		assertThat(register(gamertag, gamertag + "@lvl.test", password)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.hasPathSatisfying("$.errors[0].field", (field) -> assertThat(field).asString().isEqualTo("password"));
		assertThat(login(gamertag, password)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void protectedEndpointsRequireAToken() {
		assertThat(mvc.get().uri("/api/accounts")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/me").header("Authorization", "Bearer no-es-un-jwt"))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void meReturnsTheProfile() {
		Player player = signUp();

		assertThat(mvc.get().uri("/api/me").header("Authorization", player.bearer())).hasStatusOk()
			.bodyJson()
			.hasPathSatisfying("$.gamertag", (gamertag) -> assertThat(gamertag).asString().isEqualTo(player.gamertag()));
	}

	@Test
	void refreshRotatesTheToken() {
		Player player = signUp();

		MvcTestResult refreshed = refresh(player.refreshToken());

		assertThat(refreshed).hasStatusOk().bodyJson().hasPath("$.accessToken");
		Cookie rotated = refreshed.getResponse().getCookie("lvl_refresh");
		assertThat(rotated).isNotNull();
		assertThat(rotated.getValue()).isNotEqualTo(player.refreshToken());
		assertThat(refresh(rotated.getValue())).hasStatusOk();
	}

	@Test
	void reusingASpentRefreshTokenRevokesTheWholeSession() {
		Player player = signUp();
		String rotated = refresh(player.refreshToken()).getResponse().getCookie("lvl_refresh").getValue();

		// Someone replays the old token (e.g. it was stolen): rejected…
		assertThat(refresh(player.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("SESSION_REVOKED"));
		// …and the legitimate, newer token stops working too.
		assertThat(refresh(rotated)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutRevokesTheRefreshTokenAndClearsTheCookie() {
		Player player = signUp();

		MvcTestResult logout = mvc.post()
			.uri("/api/auth/logout")
			.cookie(new Cookie("lvl_refresh", player.refreshToken()))
			.exchange();

		assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
		assertThat(refresh(player.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshWithoutCookieIsRejected() {
		assertThat(mvc.post().uri("/api/auth/refresh")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult login(String login, String password) {
		return mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"login": "%s", "password": "%s"}
					""".formatted(login, password))
			.exchange();
	}

	private MvcTestResult refresh(String token) {
		return mvc.post().uri("/api/auth/refresh").cookie(new Cookie("lvl_refresh", token)).exchange();
	}

}
