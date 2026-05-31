package spaghettichef.local.security;

public final class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}
