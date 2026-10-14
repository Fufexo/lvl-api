package com.fufexo.lvl.shared;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OpenAPI description of the error body {@link ApiExceptionHandler} produces. Documentation only:
 * at runtime the body is Spring's {@code ProblemDetail}.
 */
@Schema(name = "Problem", description = "Error en formato RFC 9457, con un código estable y el campo afectado.")
public record ProblemDoc(@Schema(example = "about:blank") String type,
		@Schema(example = "Unprocessable Content") String title, @Schema(example = "422") int status,
		@Schema(example = "No tienes saldo suficiente en esa cuenta.") String detail,
		@Schema(example = "/api/transfers") String instance,
		@Schema(example = "INSUFFICIENT_FUNDS", description = "Estable: el cliente decide con esto, no con el texto.") String code,
		@Schema(description = "Presente cuando el error se refiere a campos del request.") List<FieldError> errors) {

	@Schema(name = "ProblemField")
	public record FieldError(@Schema(example = "amount") String field,
			@Schema(example = "No tienes saldo suficiente en esa cuenta.") String message) {
	}

}
