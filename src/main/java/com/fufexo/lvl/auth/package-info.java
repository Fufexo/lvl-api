/**
 * Users, sign-up and sign-in. Issues short-lived JWT access tokens and rotating refresh tokens.
 * Other modules only see {@link com.fufexo.lvl.auth.UserDirectory} and
 * {@link com.fufexo.lvl.auth.UserRegistered}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Auth")
package com.fufexo.lvl.auth;
