package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;

import java.math.BigDecimal;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "banking_core_account")
public class BankAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private AccountStatus status;

    private BigDecimal availableBalance;

    private BigDecimal actualBalance;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity user;

}
