package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import com.fufexo.lvl.ApiTest;
import com.fufexo.lvl.fx.FxQuote;
import com.fufexo.lvl.shared.BusinessException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class TransferApiTest extends ApiTest {

	@Autowired
	private TransferService transferService;

	@Test
	void exchangesBetweenOwnAccountsAtTheCurrentRate() {
		Player player = signUp();

		MvcTestResult result = transfer(player, key(), """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 75.00}
				""".formatted(player.pen(), player.usd()));

		assertThat(result).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.hasPathSatisfying("$.kind", (kind) -> assertThat(kind).asString().isEqualTo("INTERNAL"))
			.hasPathSatisfying("$.targetAmount", (amount) -> assertThat(amount).asNumber().isEqualTo(20.0))
			.hasPathSatisfying("$.fxRate", (rate) -> assertThat(rate).asNumber().isEqualTo(3.75));
		assertThat(balance(player, player.pen())).isEqualByComparingTo("425.00");
		assertThat(balance(player, player.usd())).isEqualByComparingTo("70.00");
		assertLedgerIsConsistent();
	}

	@Test
	void sendsMoneyToAnotherPlayerByGamertag() {
		Player alice = signUp();
		Player bob = signUp();

		MvcTestResult result = transfer(alice, key(), """
				{"sourceAccountId": "%s", "recipientGamertag": "%s", "amount": 40, "note": "Pizza"}
				""".formatted(alice.pen(), bob.gamertag().toUpperCase()));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(balance(alice, alice.pen())).isEqualByComparingTo("460.00");
		assertThat(balance(bob, bob.pen())).isEqualByComparingTo("540.00");

		String bobMovements = body(mvc.get()
			.uri("/api/accounts/{id}/movements", bob.pen())
			.header("Authorization", bob.bearer())
			.exchange());
		assertThat(JsonPath.<String>read(bobMovements, "$.items[0].description"))
			.isEqualTo("Recibido de @" + alice.gamertag() + " · Pizza");
		assertLedgerIsConsistent();
	}

	@Test
	void refusesToExchangeAtAMadeUpRate() {
		Player player = signUp();
		given(rates.usdToPen()).willReturn(new FxQuote(new BigDecimal("3.75"), Instant.now(), false, true));

		MvcTestResult result = transfer(player, key(), """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10}
				""".formatted(player.pen(), player.usd()));

		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("FX_UNAVAILABLE"));
		assertThat(balance(player, player.pen())).isEqualByComparingTo("500.00");
	}

	@Test
	void rejectsTransfersWithoutEnoughFundsOnTheAmountField() {
		Player player = signUp();

		MvcTestResult result = transfer(player, key(), """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 500.01}
				""".formatted(player.pen(), player.usd()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("INSUFFICIENT_FUNDS"))
			.hasPathSatisfying("$.errors[0].field", (field) -> assertThat(field).asString().isEqualTo("amount"));
		assertThat(balance(player, player.pen())).isEqualByComparingTo("500.00");
	}

	@Test
	void retryingWithTheSameKeyDoesNotPayTwice() {
		Player player = signUp();
		String key = key();
		String request = """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 100}
				""".formatted(player.pen(), player.usd());

		MvcTestResult first = transfer(player, key, request);
		MvcTestResult retry = transfer(player, key, request);

		assertThat(first).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "false");
		assertThat(retry).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "true");
		assertThat(JsonPath.<String>read(body(retry), "$.id")).isEqualTo(JsonPath.read(body(first), "$.id"));
		assertThat(balance(player, player.pen())).isEqualByComparingTo("400.00");
	}

	@Test
	void reusingAKeyForADifferentTransferIsRejected() {
		Player player = signUp();
		String key = key();
		transfer(player, key, """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10}
				""".formatted(player.pen(), player.usd()));

		MvcTestResult other = transfer(player, key, """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 20}
				""".formatted(player.pen(), player.usd()));

		assertThat(other).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
			.bodyJson()
			.hasPathSatisfying("$.code", (code) -> assertThat(code).asString().isEqualTo("IDEMPOTENCY_KEY_REUSED"));
	}

	@Test
	void cannotMoveMoneyFromSomeoneElsesAccount() {
		Player victim = signUp();
		Player attacker = signUp();

		MvcTestResult result = transfer(attacker, key(), """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10}
				""".formatted(victim.pen(), attacker.pen()));

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/accounts/{id}/movements", victim.pen()).header("Authorization", attacker.bearer()))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(balance(victim, victim.pen())).isEqualByComparingTo("500.00");
	}

	@Test
	void requiresAnIdempotencyKey() {
		Player player = signUp();

		assertThat(mvc.post()
			.uri("/api/transfers")
			.header("Authorization", player.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 10}
					""".formatted(player.pen(), player.usd())))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void validatesAmounts() {
		Player player = signUp();

		MvcTestResult result = transfer(player, key(), """
				{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": 1.005}
				""".formatted(player.pen(), player.usd()));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.hasPathSatisfying("$.errors[0].field", (field) -> assertThat(field).asString().isEqualTo("amount"));
	}

	@Test
	void paginatesMovementsNewestFirst() {
		Player player = signUp();
		for (int i = 1; i <= 3; i++) {
			transfer(player, key(), """
					{"sourceAccountId": "%s", "targetAccountId": "%s", "amount": %d}
					""".formatted(player.pen(), player.usd(), i));
		}

		String first = body(mvc.get()
			.uri("/api/accounts/{id}/movements?limit=2", player.pen())
			.header("Authorization", player.bearer())
			.exchange());
		List<Number> firstAmounts = JsonPath.read(first, "$.items[*].amount");
		String cursor = JsonPath.read(first, "$.nextCursor");
		MvcTestResult second = mvc.get()
			.uri("/api/accounts/{id}/movements?limit=2&cursor={cursor}", player.pen(), cursor)
			.header("Authorization", player.bearer())
			.exchange();
		List<Number> secondAmounts = JsonPath.read(body(second), "$.items[*].amount");

		assertThat(firstAmounts).extracting(Number::doubleValue).containsExactly(-3.0, -2.0);
		assertThat(secondAmounts).extracting(Number::doubleValue).containsExactly(-1.0, 500.0);
		assertThat(second).bodyJson().doesNotHavePath("$.nextCursor");
	}

	@Test
	void concurrentTransfersNeverOverdrawAnAccount() throws Exception {
		Player player = signUp();
		// 20 parallel transfers of S/ 40 from S/ 500: exactly 12 fit.
		List<Callable<Boolean>> attempts = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			attempts.add(() -> {
				try {
					transferService.transfer(new TransferService.Command(player.id(), key(), player.pen(), player.usd(),
							null, new BigDecimal("40"), null));
					return true;
				}
				catch (BusinessException ex) {
					assertThat(ex.code()).isEqualTo("INSUFFICIENT_FUNDS");
					return false;
				}
			});
		}

		long succeeded;
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Boolean>> results = executor.invokeAll(attempts);
			succeeded = results.stream().filter(this::get).count();
		}

		assertThat(succeeded).isEqualTo(12);
		assertThat(balance(player, player.pen())).isEqualByComparingTo("20.00");
		assertLedgerIsConsistent();
	}

	private boolean get(Future<Boolean> future) {
		try {
			return future.get();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private MvcTestResult transfer(Player player, String idempotencyKey, String json) {
		return mvc.post()
			.uri("/api/transfers")
			.header("Authorization", player.bearer())
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	private static String key() {
		return UUID.randomUUID().toString();
	}

}
