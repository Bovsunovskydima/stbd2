package ua.lab.cafenet.api;

import java.util.List;

public record ApiFailure(int status, String error, String message, List<String> fields) {
}
