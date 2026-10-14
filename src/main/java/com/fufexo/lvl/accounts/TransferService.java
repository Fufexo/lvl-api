package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.auth.UserDirectory;
import com.fufexo.lvl.fx.ExchangeRates;
import com.fufexo.lvl.shared.Amounts;
import com.fufexo.lvl.shared.BusinessException;
import com.fufexo.lvl.shared.Hashing;
import com.fufexo.lvl.shared.Ids;
import com.fufexo.lvl.shared.Instants;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves money between accounts. Every request carries an idempotency key: retrying it (a double
 * click, a timeout, a flaky network) returns the original transfer instead of paying twice.
 */
@Service
class TransferService {

	/** Exactly one of {@code targetAccountId} (own account) or {@code recipientGamertag} is set. */
	record Command(UUID userId, String idempotencyKey, UUID sourceAccountId, @Nullable UUID targetAccountId,
			@Nullable String recipientGamertag, BigDecimal amount, @Nullable String note) {

		/** Identifies the request body, to tell a retry apart from a key reused for something else. */
		String fingerprint() {
			return Hashing.sha256Hex(String.join("|", sourceAccountId.toString(), String.valueOf(targetAccountId),
					String.valueOf(recipientGamertag).toLowerCase(Locale.ROOT),
					amount.stripTrailingZeros().toPlainString(), String.valueOf(note)));
		}

	}

	record Outcome(Transfer transfer, boolean replayed) {
	}

	private final AccountRepository accounts;

	private final TransferRepository transfers;

	private final Ledger ledger;

	private final UserDirectory users;

	private final ExchangeRates rates;

	private final TransactionTemplate transaction;

	private final Clock clock;

	TransferService(AccountRepository accounts, TransferRepository transfers, Ledger ledger, UserDirectory users,
			ExchangeRates rates, TransactionTemplate transaction, Clock clock) {
		this.accounts = accounts;
		this.transfers = transfers;
		this.ledger = ledger;
		this.users = users;
		this.rates = rates;
		this.transaction = transaction;
		this.clock = clock;
	}

	Outcome transfer(Command command) {
		String fingerprint = command.fingerprint();
		var previous = transfers.findByInitiatedByAndIdempotencyKey(command.userId(), command.idempotencyKey());
		if (previous.isPresent()) {
			return replay(previous.get(), fingerprint);
		}
		try {
			return new Outcome(transaction.execute((status) -> execute(command, fingerprint)), false);
		}
		catch (DataIntegrityViolationException ex) {
			// A concurrent request with the same key committed first; answer like a retry.
			return transfers.findByInitiatedByAndIdempotencyKey(command.userId(), command.idempotencyKey())
				.map((winner) -> replay(winner, fingerprint))
				.orElseThrow(() -> ex);
		}
	}

	private Outcome replay(Transfer transfer, String fingerprint) {
		if (!fingerprint.equals(transfer.requestHash())) {
			throw BusinessException.unprocessable("IDEMPOTENCY_KEY_REUSED",
					"Esa clave de idempotencia ya se usó con otros datos.", null);
		}
		return new Outcome(transfer, true);
	}

	private Transfer execute(Command command, String fingerprint) {
		Instant now = Instants.now(clock);
		Account source = accounts.findByIdAndOwnerId(command.sourceAccountId(), command.userId())
			.orElseThrow(() -> BusinessException.notFound("ACCOUNT_NOT_FOUND", "No encontramos esa cuenta.",
					"sourceAccountId"));
		Destination destination = resolveDestination(command, source);
		Account target = destination.account();
		if (target.id().equals(source.id())) {
			throw BusinessException.unprocessable("SAME_ACCOUNT", "Elige una cuenta distinta a la de origen.",
					"targetAccountId");
		}

		long sourceMinor = Amounts.toMinor(command.amount());
		BigDecimal fxRate = null;
		long targetMinor = sourceMinor;
		if (source.currency() != target.currency()) {
			var quote = rates.usdToPen();
			if (quote.fallback()) {
				throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "FX_UNAVAILABLE",
						"El tipo de cambio no está disponible ahora. Intenta en unos minutos.", "amount");
			}
			fxRate = quote.usdToPen();
			targetMinor = FxConversion.convert(sourceMinor, source.currency(), target.currency(), fxRate);
			if (targetMinor <= 0) {
				throw BusinessException.unprocessable("AMOUNT_TOO_SMALL", "El monto es muy pequeño para convertirlo.",
						"amount");
			}
		}

		Transfer transfer = transfers.save(new Transfer(Ids.next(), destination.kind(), command.userId(),
				command.idempotencyKey(), fingerprint, source, sourceMinor, target, targetMinor, fxRate,
				command.note(), now));
		try {
			ledger.post(transfer.id(), legs(destination, source, sourceMinor, target, targetMinor, command.note()),
					now);
		}
		catch (Ledger.InsufficientFunds ex) {
			throw BusinessException.unprocessable("INSUFFICIENT_FUNDS", "No tienes saldo suficiente en esa cuenta.",
					"amount");
		}
		return transfer;
	}

	private record Destination(Account account, Transfer.Kind kind, @Nullable String senderGamertag,
			@Nullable String recipientGamertag) {
	}

	private Destination resolveDestination(Command command, Account source) {
		if ((command.targetAccountId() == null) == (command.recipientGamertag() == null)) {
			throw BusinessException.unprocessable("TARGET_REQUIRED", "Elige una cuenta destino o un gamertag.",
					"targetAccountId");
		}
		if (command.targetAccountId() != null) {
			Account target = accounts.findByIdAndOwnerId(command.targetAccountId(), command.userId())
				.orElseThrow(() -> BusinessException.notFound("ACCOUNT_NOT_FOUND", "No encontramos esa cuenta.",
						"targetAccountId"));
			return new Destination(target, Transfer.Kind.INTERNAL, null, null);
		}
		UUID recipientId = users.findIdByGamertag(command.recipientGamertag())
			.orElseThrow(() -> BusinessException.notFound("RECIPIENT_NOT_FOUND", "No hay ningún jugador con ese gamertag.",
					"recipientGamertag"));
		if (recipientId.equals(command.userId())) {
			throw BusinessException.unprocessable("SELF_TRANSFER",
					"Para mover dinero entre tus cuentas, elige la cuenta destino.", "recipientGamertag");
		}
		// P2P stays in the source currency: the recipient receives exactly what was sent.
		Account target = accounts.findByOwnerIdAndCurrency(recipientId, source.currency())
			.orElseThrow(() -> new IllegalStateException("User " + recipientId + " has no " + source.currency()));
		String sender = users.gamertagOf(command.userId()).orElseThrow();
		String recipient = users.gamertagOf(recipientId).orElseThrow();
		return new Destination(target, Transfer.Kind.P2P, sender, recipient);
	}

	private static List<Ledger.Leg> legs(Destination destination, Account source, long sourceMinor, Account target,
			long targetMinor, @Nullable String note) {
		String suffix = (note == null || note.isBlank()) ? "" : " · " + note.strip();
		List<Ledger.Leg> legs = new ArrayList<>();
		if (destination.kind() == Transfer.Kind.P2P) {
			legs.add(new Ledger.Leg(source.id(), -sourceMinor, "Envío a @" + destination.recipientGamertag() + suffix));
			legs.add(new Ledger.Leg(target.id(), targetMinor, "Recibido de @" + destination.senderGamertag() + suffix));
			return legs;
		}
		if (source.currency() == target.currency()) {
			String label = "Transferencia entre cuentas" + suffix;
			legs.add(new Ledger.Leg(source.id(), -sourceMinor, label));
			legs.add(new Ledger.Leg(target.id(), targetMinor, label));
			return legs;
		}
		// Currency exchange: each currency balances through its FX clearing account.
		String label = "Cambio " + source.currency() + " → " + target.currency() + suffix;
		legs.add(new Ledger.Leg(source.id(), -sourceMinor, label));
		legs.add(new Ledger.Leg(SystemAccounts.fxClearing(source.currency()), sourceMinor, label));
		legs.add(new Ledger.Leg(SystemAccounts.fxClearing(target.currency()), -targetMinor, label));
		legs.add(new Ledger.Leg(target.id(), targetMinor, label));
		return legs;
	}

}
