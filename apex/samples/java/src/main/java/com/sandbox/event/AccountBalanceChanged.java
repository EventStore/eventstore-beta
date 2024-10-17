package com.sandbox.event;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.UUID;

@Builder @Jacksonized
@Value
public class AccountBalanceChanged implements Event {
    private final UUID id;
    private final UUID accountId;
    private final double delta;
}
