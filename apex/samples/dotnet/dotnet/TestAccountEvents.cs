using System.Text.Json.Serialization;

namespace apex;

using EventStore.Client;
using NUnit.Framework;
using System.Text;
using System.Text.Json;
using System.Collections.Generic;
using System.IO;

public class Esdb
{
    public string Url { get; set; }
    public string CertificateDir { get; set; }

    public string NormalizedUrl()
    {
        string url = Url;
        foreach (var file in new[] { "ca.crt", "tls.crt", "tls.key" })
        {
            url = url.Replace(file, $"{CertificateDir}/{file}");
        }
        return url;
    }
}

public class Config
{
    public Esdb esdb { get; set; }
}

[JsonDerivedType(typeof(AccountCreated))]
[JsonDerivedType(typeof(AccountBalanceChanged))]
public interface IEvent
{
    string Id { get; }
}

public record Account(string Id, string Name, DateTime Created) : IEvent
{
}

public record AccountCreated(string Id, Account Account) : IEvent
{
}

public record AccountBalanceChanged(string Id, string AccountId, decimal Delta) : IEvent
{
}

public class Tests
{
    private EventStoreClient client;
    private Random random;
    private JsonSerializerOptions jsonOptions;
    
    [SetUp]
    public void Setup()
    {
        var config = GetConfig();
        random = new Random();
        client = new EventStoreClient(EventStoreClientSettings.Create(config.esdb.NormalizedUrl()));
        jsonOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DefaultIgnoreCondition = JsonIgnoreCondition.Never
        };
    }

    [TearDown]
    public void TearDown()
    {
        client.Dispose();
    }

    [Test]
    public async Task ShouldPersistEvents()
    {
        for (int i = 0; i < 10; i++)
        {
            Account account = new Account(Uuid.NewUuid().ToString(), Name: "test", Created: DateTime.UtcNow);
            List<IEvent> events = new List<IEvent>
            {
                new AccountCreated(Uuid.NewUuid().ToString(), account),
                new AccountBalanceChanged(Uuid.NewUuid().ToString(), account.Id, random.Next(-100, 100)),
                new AccountBalanceChanged(Uuid.NewUuid().ToString(), account.Id, random.Next(-100, 100)),
                new AccountBalanceChanged(Uuid.NewUuid().ToString(), account.Id, random.Next(-100, 100)),
                new AccountBalanceChanged(Uuid.NewUuid().ToString(), account.Id, random.Next(-100, 100)),
                new AccountBalanceChanged(Uuid.NewUuid().ToString(), account.Id, random.Next(-100, 100)),
            };
            string streamName = "chris-" + account.Id;
            List<EventData> eventData = events.Select(e => ToEventData(e)).ToList();
            client.AppendToStreamAsync(streamName, StreamState.Any, eventData).GetAwaiter().GetResult();
            // Read events back.
            EventStoreClient.ReadStreamResult readResult =
                client.ReadStreamAsync(Direction.Forwards, streamName, StreamPosition.Start);
            int idx = 0;
            await foreach (ResolvedEvent resolvedEvent in readResult)
            {
                Assert.That(Encoding.UTF8.GetString(ToEventData(events[idx++]).Data.Span),
                    Is.EqualTo(Encoding.UTF8.GetString(resolvedEvent.Event.Data.Span)));
            }
        }
    }

    static Config GetConfig()
    {
        var options = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            PropertyNameCaseInsensitive = true
        };
        var testDir = TestContext.CurrentContext.TestDirectory;
        var jsonData = File.ReadAllText(Path.Combine(testDir, "config.json"));
        return JsonSerializer.Deserialize<Config>(jsonData, options);
    }
    
    private EventData ToEventData<T>(T e) where T : IEvent
    {
        var eventId = Uuid.Parse(e.Id);
        var eventType = e.GetType().Name;
        var data = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(e, jsonOptions));
        var metadata = JsonSerializer.SerializeToUtf8Bytes(new { Timestamp = DateTime.UtcNow });
        return new EventData(eventId, eventType, data, metadata);
    }
}