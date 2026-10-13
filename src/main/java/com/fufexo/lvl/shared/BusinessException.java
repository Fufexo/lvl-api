package com.fufexo.lvl.shared;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpStatus;

/**
 * A rule of the domain rejected the request. It reaches the client as an RFC 9457 problem with a
 * stable {@code code} and, when it concerns one input, the {@code field} so the form can show the
 * message next to it.
 */
public class BusinessException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	@Nullable
	private final String field;

	public BusinessException(HttpStatus status, String code, String message, @Nullable String field) {
		super(message);
		this.status = status;
		this.code = code;
		this.field = field;
	}

	public static BusinessException notFound(String code, String message, @Nullable String field) {
		return new BusinessException(HttpStatus.NOT_FOUND, code, message, field);
	}

	public static BusinessException conflict(String code, String message, @Nullable String field) {
		return new BusinessException(HttpStatus.CONFLICT, code, message, field);
	}

	public static BusinessException unprocessable(String code, String message, @Nullable String field) {
		return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, code, message, field);
	}

	public static BusinessException unauthorized(String code, String message) {
		return new BusinessException(HttpStatus.UNAUTHORIZED, code, message, null);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	@Nullable
	public String field() {
		return field;
	}

}
