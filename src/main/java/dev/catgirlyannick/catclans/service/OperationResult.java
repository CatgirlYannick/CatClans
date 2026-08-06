package dev.catgirlyannick.catclans.service;

import java.util.Optional;

public record OperationResult<T>(OperationCode code, T value) {

    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<>(OperationCode.SUCCESS, value);
    }

    public static <T> OperationResult<T> failure(OperationCode code) {
        if (code == OperationCode.SUCCESS) {
            throw new IllegalArgumentException("SUCCESS requires a result value");
        }
        return new OperationResult<>(code, null);
    }

    public boolean successful() {
        return code == OperationCode.SUCCESS;
    }

    public Optional<T> optionalValue() {
        return Optional.ofNullable(value);
    }
}
