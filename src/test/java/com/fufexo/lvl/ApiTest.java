package com.fufexo.lvl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;

import com.fufexo.lvl.fx.ExchangeRates;
import com.fufexo.lvl.fx.FxQuote;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Integration tests against a real Postgres (Testcontainers) through the full HTTP stack. Every
 * test signs up its own players, so tests share one database without cleaning it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class ApiTest {

	protected static final BigDecimal USD_TO_PEN = new BigDecimal("3.75");

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@MockitoBean
	protected ExchangeRates rates;

	protected record Player(UUID id, String gamertag, String accessToken, String refreshToken, UUID pen, UUID usd) {

		public String bearer() {
			return "Bearer " + accessToken;
		}

	}

	@BeforeEach
	void fixExchangeRate() {
		given(rates.usdToPen()).willReturn(new FxQuote(USD_TO_PEN, Instant.parse("2026-10-13T00:00:00Z"), true));
	}

	protected static String uniqueGamertag() {
		return "p" + Long.toString(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), 36).substring(0, 10);
	}

	protected MvcTestResult register(String gamertag, String email, String password) {
		return mvc.post()
			.uri("/api/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"gamertag": "%s", "email": "%s", "password": "%s"}
					""".formatted(gamertag, email, password))
			.exchange();
	}

	protected Player signUp() {
		String gamertag = uniqueGamertag();
		MvcTestResult result = register(gamertag, gamertag + "@lvl.test", "contraseña-segura");
		assertThat(result).hasStatus(HttpStatus.CREATED);
		String body = body(result);
		String accessToken = JsonPath.read(body, "$.accessToken");
		UUID id = UUID.fromString(JsonPath.read(body, "$.user.id"));
		Cookie refresh = result.getResponse().getCookie("lvl_refresh");
		assertThat(refresh).isNotNull();

		String accounts = body(mvc.get().uri("/api/accounts").header("Authorization", "Bearer " + accessToken).exchange());
		List<String> ids = JsonPath.read(accounts, "$[*].id");
		List<String> currencies = JsonPath.read(accounts, "$[*].currency");
		UUID pen = UUID.fromString(ids.get(currencies.indexOf("PEN")));
		UUID usd = UUID.fromString(ids.get(currencies.indexOf("USD")));
		return new Player(id, gamertag, accessToken, refresh.getValue(), pen, usd);
	}

	protected BigDecimal balance(Player player, UUID accountId) {
		String accounts = body(mvc.get().uri("/api/accounts").header("Authorization", player.bearer()).exchange());
		List<Number> balances = JsonPath.read(accounts, "$[?(@.id == '%s')].balance".formatted(accountId));
		return new BigDecimal(balances.getFirst().toString());
	}

	/** Money is never created or destroyed: per currency the ledger sums to zero, and every balance matches it. */
	protected void assertLedgerIsConsistent() {
		List<Long> perCurrency = jdbc.queryForList(
				"select sum(amount_minor) from ledger_entries group by currency", Long.class);
		assertThat(perCurrency).allSatisfy((total) -> assertThat(total).isZero());
		Integer drifted = jdbc.queryForObject("""
				select count(*) from accounts a
				where a.balance_minor <> coalesce((select sum(e.amount_minor) from ledger_entries e
				                                   where e.account_id = a.id), 0)
				""", Integer.class);
		assertThat(drifted).isZero();
	}

	protected static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
