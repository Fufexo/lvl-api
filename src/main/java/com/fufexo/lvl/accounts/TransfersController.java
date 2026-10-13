package com.fufexo.lvl.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import com.fufexo.lvl.shared.Amounts;
import com.fufexo.lvl.shared.BusinessException;
import com.fufexo.lvl.shared.CurrencyCode;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transferencias")
@SecurityRequirement(name = "bearer")
class TransfersController {

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private static final String KEY_PATTERN = "^[A-Za-z0-9_-]{8,64}$";

	record TransferRequest(@NotNull(message = "Elige la cuenta de origen.") UUID sourceAccountId,
			@Nullable UUID targetAccountId, @Nullable @Size(max = 16) String recipientGamertag,
			@NotNull(message = "Ingresa un monto.") @DecimalMin(value = "0.01", message = "El monto mínimo es 0.01.")
			@DecimalMax(value = "100000.00", message = "El monto máximo por transferencia es 100 000.")
			@Digits(integer = 6, fraction = 2, message = "Usa como máximo dos decimales.") BigDecimal amount,
			@Nullable @Size(max = 140, message = "La nota admite hasta 140 caracteres.") String note) {
	}

	record TransferView(UUID id, Transfer.Kind kind, UUID sourceAccountId, BigDecimal sourceAmount,
			CurrencyCode sourceCurrency, UUID targetAccountId, BigDecimal targetAmount, CurrencyCode targetCurrency,
			@Nullable BigDecimal fxRate, @Nullable String note, Instant createdAt) {

		static TransferView of(Transfer t) {
			return new TransferView(t.id(), t.kind(), t.sourceAccountId(), Amounts.fromMinor(t.sourceAmountMinor()),
					t.sourceCurrency(), t.targetAccountId(), Amounts.fromMinor(t.targetAmountMinor()),
					t.targetCurrency(), t.fxRate(), t.note(), t.createdAt());
		}

	}

	private final TransferService transfers;

	TransfersController(TransferService transfers) {
		this.transfers = transfers;
	}

	@PostMapping
	@Operation(summary = "Transferir",
			description = "A una cuenta propia (con cambio de moneda si corresponde) o a otro jugador por gamertag. "
					+ "Reintentar con la misma Idempotency-Key devuelve la transferencia original.")
	ResponseEntity<TransferView> create(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "Clave única por intento, de 8 a 64 caracteres (por ejemplo un UUID).")
			@RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		if (!idempotencyKey.matches(KEY_PATTERN)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
					"Idempotency-Key debe tener de 8 a 64 letras, números, guiones o guiones bajos.", null);
		}
		var outcome = transfers.transfer(new TransferService.Command(UUID.fromString(jwt.getSubject()), idempotencyKey,
				request.sourceAccountId(), request.targetAccountId(), blankToNull(request.recipientGamertag()),
				request.amount(), blankToNull(request.note())));
		return ResponseEntity.status(HttpStatus.CREATED)
			.header("Idempotent-Replayed", String.valueOf(outcome.replayed()))
			.body(TransferView.of(outcome.transfer()));
	}

	@Nullable
	private static String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

}
