package com.sandbox.events;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.eventstore.dbclient.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.sandbox.event.AccountBalanceChanged;
import com.sandbox.event.AccountCreated;
import com.sandbox.event.Event;
import com.sandbox.model.Account;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TestAccountEvents {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventStoreDBClient eventStoreDBClient;

    @Autowired
    private EventStoreDBProjectionManagementClient eventStoreDBProjectionManagementClient;

    @ParameterizedTest
    @MethodSource("accountEventsShouldBePersistedArgumentProvider")
    public void accountEventsShouldBePersisted(int streamCount) throws Exception {
        long totalEvents = 0;
        log.info("Persisting event data...");
        LocalDateTime start = LocalDateTime.now();
        long writeTimeTakenMs = 0;
        for (int streamIdx = 0; streamIdx < streamCount; streamIdx++) {
            UUID accountId = UUID.randomUUID();
            Account account = Account.builder()
                    .id(accountId)
                    .created(LocalDateTime.now())
                    .name(accountId.toString())
                    .build();
            List<Event> events = List.of(
                    AccountCreated.builder()
                            .id(UUID.randomUUID())
                            .account(account)
                            .build(),
                    AccountBalanceChanged.builder()
                            .id(UUID.randomUUID())
                            .accountId(account.getId())
                            .delta(ThreadLocalRandom.current().nextDouble())
                            .build(),
                    AccountBalanceChanged.builder()
                            .id(UUID.randomUUID())
                            .accountId(account.getId())
                            .delta(ThreadLocalRandom.current().nextDouble())
                            .build(),
                    AccountBalanceChanged.builder()
                            .id(UUID.randomUUID())
                            .accountId(account.getId())
                            .delta(ThreadLocalRandom.current().nextDouble())
                            .build(),
                    AccountBalanceChanged.builder()
                            .id(UUID.randomUUID())
                            .accountId(account.getId())
                            .delta(ThreadLocalRandom.current().nextDouble())
                            .build()
            );
            EventData[] esdbEvents = events
            .stream()
            .map(event -> createEventData(event.getId(), event.getClass().getSimpleName(), event))
            .toArray(EventData[]::new);

            totalEvents += events.size();
            // Persist the event data.
            String streamName = "account_" + accountId;
            LocalDateTime writeStart = LocalDateTime.now();
            WriteResult writeResult = eventStoreDBClient.appendToStream(streamName, esdbEvents).get();
            LocalDateTime writeStop = LocalDateTime.now();
            writeTimeTakenMs += Duration.between(writeStart, writeStop).toMillis();
            assertThat(writeResult).isNotNull();
            assertThat(writeResult.getLogPosition()).isNotNull();
            // Read the event data back.
            ReadResult readResult = eventStoreDBClient.readStream(streamName, ReadStreamOptions.get().fromStart()).get();
            assertThat(readResult.getEvents()).isNotNull();
            assertThat(readResult.getEvents().size()).isEqualTo(events.size());
            for(int idx = 0; idx < events.size(); idx++) {
                byte[] data = objectMapper.writeValueAsBytes(events.get(idx));
                byte[] storedData = readResult.getEvents().get(idx).getOriginalEvent().getEventData();
                assertThat(data).isEqualTo(storedData);
                assertThat(new String(data)).isEqualTo(new String(storedData));
            }
        }
        LocalDateTime stop = LocalDateTime.now();
        long writeTimeTakenSecs = writeTimeTakenMs / 1000;
        long timeTakenSecs = Duration.between(start, stop).getSeconds();
        log.info("Persisted events: total={}, timeTaken={}/s, writeTimeTaken={}/s", totalEvents, timeTakenSecs, writeTimeTakenSecs);

    }

    private EventData createEventData(UUID id, String type, Object event) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(event);
            return EventData.builderAsJson(id, type, data).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Stream<Arguments> accountEventsShouldBePersistedArgumentProvider() {
        return Stream.of(
                arguments(10)
        );
    }

    @TestConfiguration
    public static class TestAccountEventsConfiguration {

        @Bean
        public EventStoreDBClientSettings eventStoreDBClientSettings(
                @Value("${esdb.url}") String url,
                @Value("${esdb.certPath}") String certPath) {
            for (String file : Arrays.asList("ca.crt", "tls.crt", "tls.key")) {
                url = url.replaceAll(file, certPath + "/" + file);
            }
            return EventStoreDBConnectionString.parseOrThrow(url);
        }

        @Bean
        public EventStoreDBClient eventStoreDBClient(EventStoreDBClientSettings settings) {
            return EventStoreDBClient.create(settings);
        }

        @Bean
        public EventStoreDBProjectionManagementClient eventStoreDBProjectionManagementClient(
                EventStoreDBClientSettings settings) {
            return EventStoreDBProjectionManagementClient.create(settings);
        }

        @Bean
        public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
            return new Jackson2ObjectMapperBuilder()
                    .modules(new ParameterNamesModule(), new Jdk8Module(), new JavaTimeModule())
                    .dateFormat(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss"));
        }
    }
}
