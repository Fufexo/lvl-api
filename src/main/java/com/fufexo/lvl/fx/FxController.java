package com.fufexo.lvl.fx;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx")
@Tag(name = "Tipo de cambio")
class FxController {

	private final ExchangeRates rates;

	FxController(ExchangeRates rates) {
		this.rates = rates;
	}

	@GetMapping("/usd-pen")
	@Operation(summary = "Tipo de cambio USD → PEN", description = "Público. Se actualiza como máximo una vez por hora.")
	FxQuote usdToPen() {
		return rates.usdToPen();
	}

}
