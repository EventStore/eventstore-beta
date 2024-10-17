package com.sandbox.event;

import com.sandbox.model.Account;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

@Builder @Jacksonized
@Value
public class AccountCreated implements Event {
    private final UUID id;
    private final Account account;
}
