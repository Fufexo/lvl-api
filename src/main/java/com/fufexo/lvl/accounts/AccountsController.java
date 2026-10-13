package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.shared.Amounts;
import com.fufexo.lvl.shared.BusinessException;
import com.fufexo.lvl.shared.CurrencyCode;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@Validated
@Tag(name = "Cuentas")
@SecurityRequirement(name = "bearer")
class AccountsController {

	record AccountView(UUID id, CurrencyCode currency, BigDecimal balance) {

		static AccountView of(Account account) {
			return new AccountView(account.id(), account.currency(), Amounts.fromMinor(account.balanceMinor()));
		}

	}

	record MovementView(UUID id, UUID transferId, Transfer.Kind kind, String description, BigDecimal amount,
			CurrencyCode currency, BigDecimal balanceAfter, Instant createdAt) {

		static MovementView of(Movement movement) {
			return new MovementView(movement.id(), movement.transferId(), movement.kind(), movement.description(),
					Amounts.fromMinor(movement.amountMinor()), movement.currency(),
					Amounts.fromMinor(movement.balanceAfterMinor()), movement.createdAt());
		}

	}

	/** {@code nextCursor} is absent on the last page. */
	record MovementPage(List<MovementView> items, @Nullable UUID nextCursor) {
	}

	private final AccountRepository accounts;

	private final LedgerEntryRepository entries;

	AccountsController(AccountRepository accounts, LedgerEntryRepository entries) {
		this.accounts = accounts;
		this.entries = entries;
	}

	@GetMapping
	@Transactional(readOnly = true)
	@Operation(summary = "Mis cuentas y saldos")
	List<AccountView> list(@AuthenticationPrincipal Jwt jwt) {
		return accounts.findByOwnerIdOrderByCurrency(userId(jwt)).stream().map(AccountView::of).toList();
	}

	@GetMapping("/{accountId}/movements")
	@Transactional(readOnly = true)
	@Operation(summary = "Movimientos de una cuenta",
			description = "Del más reciente al más antiguo. Para la página siguiente, envía nextCursor como cursor.")
	MovementPage movements(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
			@RequestParam(required = false) @Nullable UUID cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		accounts.findByIdAndOwnerId(accountId, userId(jwt))
			.orElseThrow(() -> BusinessException.notFound("ACCOUNT_NOT_FOUND", "No encontramos esa cuenta.", null));
		// Fetch one extra row to know whether another page exists.
		Limit page = Limit.of(limit + 1);
		List<Movement> rows = (cursor == null) ? entries.latest(accountId, page)
				: entries.before(accountId, cursorTime(cursor, accountId), cursor, page);
		boolean hasMore = rows.size() > limit;
		List<Movement> visible = hasMore ? rows.subList(0, limit) : rows;
		UUID next = hasMore ? visible.getLast().id() : null;
		return new MovementPage(visible.stream().map(MovementView::of).toList(), next);
	}

	private Instant cursorTime(UUID cursor, UUID accountId) {
		return entries.createdAtOf(cursor, accountId)
			.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR",
					"El cursor no es válido para esta cuenta.", "cursor"));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
