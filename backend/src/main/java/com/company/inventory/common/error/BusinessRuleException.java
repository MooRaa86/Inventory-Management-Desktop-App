package com.company.inventory.common.error;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(422, code, message);
    }
}
