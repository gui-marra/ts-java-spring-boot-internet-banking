package com.javatodev.finance.exception;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SimpleBankingGlobalException extends RuntimeException {

    private String code;
    private String message;

    public SimpleBankingGlobalException(String message) {
        super(message);
        this.message = message;
    }

    public SimpleBankingGlobalException(String message, String code) {
        super(message);
        this.message = message;
        this.code = code;
    }
}
