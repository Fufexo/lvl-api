package com.fufexo.lvl.shared;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as {@code application/problem+json} with a machine-readable
 * {@code code} and, for input problems, an {@code errors} list of {@code {field, message}}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	record FieldMessage(String field, String message) {
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
		problem.setProperty("code", ex.code());
		if (ex.field() != null) {
			problem.setProperty("errors", List.of(new FieldMessage(ex.field(), ex.getMessage())));
		}
		return ResponseEntity.status(ex.status()).body(problem);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Algo salió mal de nuestro lado. Intenta de nuevo en unos segundos.");
		problem.setProperty("code", "INTERNAL_ERROR");
		return ResponseEntity.internalServerError().body(problem);
	}

	@Override
	@Nullable
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldMessage> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> new FieldMessage(error.getField(), error.getDefaultMessage()))
			.toList();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Revisa los datos enviados.");
		problem.setProperty("code", "VALIDATION_FAILED");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

}
