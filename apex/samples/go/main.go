package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/url"
	"os"
	"path/filepath"
	"time"

	"github.com/EventStore/EventStore-Client-Go/v4/esdb"
	"github.com/google/uuid"
)

type Event interface {
	GetEventType() string
	GetData() ([]byte, error)
}

type AccountCreated struct {
	Id   uuid.UUID `json:"id"`
	Name string    `json:"name"`
	Time time.Time `json:"time"`
}

func (e AccountCreated) GetEventType() string {
	return "AccountCreated"
}

func (e AccountCreated) GetData() ([]byte, error) {
	return json.Marshal(e)
}

type AccountUpdated struct {
	Id    uuid.UUID `json:"id"`
	Delta int       `json:"delta"`
	Time  time.Time `json:"time"`
}

func (e AccountUpdated) GetEventType() string {
	return "AccountUpdated"
}

func (e AccountUpdated) GetData() ([]byte, error) {
	return json.Marshal(e)
}

type Account struct {
	Id      uuid.UUID `json:"id"`
	Name    string    `json:"name"`
	Created time.Time `json:"created"`
}

func validateConnectionString(connectionString string, certFolder string) (string, error) {
	parsedURL, err := url.Parse(connectionString)
	if err != nil {
		return "", fmt.Errorf("invalid connection string: %v", err)
	}

	queryParams := parsedURL.Query()

	for _, param := range []string{"userCertFile", "userKeyFile", "tlsCaFile"} {
		if value, exists := queryParams[param]; exists {
			queryParams.Set(param, filepath.Join(certFolder, value[0]))
		} else {
			return "", fmt.Errorf("%s parameter is required in the connection string", param)
		}
	}

	parsedURL.RawQuery = queryParams.Encode()
	return parsedURL.String(), nil
}

func buildEsdbConfig(connectionString string) (*esdb.Configuration, error) {
	config, err := esdb.ParseConnectionString(connectionString)
	if err != nil {
		return nil, fmt.Errorf("invalid connection string: %v", err)
	}

	return config, nil
}

func buildEvents(events []Event) ([]*esdb.EventData, error) {
	var esdbEvents []*esdb.EventData
	for _, event := range events {
		data, err := event.GetData()
		if err != nil {
			return nil, fmt.Errorf("error marshalling event data: %v", err)
		}

		e := &esdb.EventData{
			EventID:     uuid.New(),
			EventType:   event.GetEventType(),
			ContentType: esdb.ContentTypeJson,
			Data:        data,
			Metadata:    nil,
		}
		esdbEvents = append(esdbEvents, e)
	}
	return esdbEvents, nil
}

func writeStream(client *esdb.Client) (*string, error) {
	ctx := context.Background()

	account := Account{
		Id:      uuid.New(),
		Name:    "Test Account",
		Created: time.Now(),
	}

	events := []Event{
		AccountCreated{
			Id:   account.Id,
			Name: account.Name,
			Time: account.Created,
		},
		AccountUpdated{
			Id:    account.Id,
			Delta: 100,
			Time:  time.Now(),
		},
		AccountUpdated{
			Id:    account.Id,
			Delta: 250,
			Time:  time.Now(),
		},
		AccountUpdated{
			Id:    account.Id,
			Delta: -399,
			Time:  time.Now(),
		},
		AccountUpdated{
			Id:    account.Id,
			Delta: 800,
			Time:  time.Now(),
		},
		AccountUpdated{
			Id:    account.Id,
			Delta: -400,
			Time:  time.Now(),
		}}

	eventData, err := buildEvents(events)
	if err != nil {
		return nil, err
	}
	streamName := "account-" + account.Id.String()

	// Create a new stream

	for _, ed := range eventData {
		_, err := client.AppendToStream(ctx, streamName, esdb.AppendToStreamOptions{ExpectedRevision: esdb.Any{}}, *ed)
		if err != nil {
			return nil, err
		}
	}

	return &streamName, nil
}

func readStream(client *esdb.Client, streamName string) error {
	ctx := context.Background()
	stream, err := client.ReadStream(ctx, streamName, esdb.ReadStreamOptions{Direction: esdb.Forwards, From: esdb.Start{}}, 100)
	if err != nil {
		return err
	}
	defer stream.Close()

	for {
		event, err := stream.Recv()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			panic(err)
		}
		fmt.Println(string(event.Event.Data))
	}
	return nil
}

func main() {
	if len(os.Args) < 3 {
		fmt.Println("Usage: go run main.go <connection_string> <cert_folder>")
		os.Exit(1)
	}

	address := os.Args[1]
	certFolder := os.Args[2]

	url, err := validateConnectionString(address, certFolder)
	if err != nil {
		log.Fatalf("Error validating connection string: %v", err)
	}

	config, err := buildEsdbConfig(url)
	if err != nil {
		log.Fatalf("Error building ESDB configuration for client: %v", err)
	}

	client, err := esdb.NewClient(config)
	if err != nil {
		log.Fatalf("Error creating ESDB client: %v", err)
	}

	streamName, err := writeStream(client)
	if err != nil {
		log.Fatalf("Error writing stream: %v", err)
	}

	fmt.Printf("Stream Name: %s\n", *streamName)
	err = readStream(client, *streamName)
	if err != nil {
		log.Fatalf("Error reading stream: %v", err)
	}
}
