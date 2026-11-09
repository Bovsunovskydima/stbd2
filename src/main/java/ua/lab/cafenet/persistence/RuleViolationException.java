package ua.lab.cafenet.persistence;

public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }

    public RuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
