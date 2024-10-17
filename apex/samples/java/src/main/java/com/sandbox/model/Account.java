package com.sandbox.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder @Jacksonized
@Value
public class Account {
    private final UUID id;
    private final String name;
    private final LocalDateTime created;
}
