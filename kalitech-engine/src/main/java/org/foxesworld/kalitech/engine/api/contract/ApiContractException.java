package org.foxesworld.kalitech.engine.api.contract;

public final class ApiContractException extends RuntimeException {

    private final String apiId;
    private final String signature;
    private final int paramIndex;
    private final String rule;

    public ApiContractException(String apiId, String signature, int paramIndex, String rule, String message) {
        super(message);
        this.apiId = apiId;
        this.signature = signature;
        this.paramIndex = paramIndex;
        this.rule = rule;
    }

    public String apiId() {
        return apiId;
    }

    public String signature() {
        return signature;
    }

    public int paramIndex() {
        return paramIndex;
    }

    public String rule() {
        return rule;
    }
}