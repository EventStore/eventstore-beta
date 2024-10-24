import argparse
import json
import random
import uuid
from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime, timezone

from dataclasses_json import LetterCase, dataclass_json
from esdbclient import EventStoreDBClient, events


class Event(ABC):
    @abstractmethod
    def get_event_type(self) -> str:
        pass


class AccountCreated(Event):
    def __init__(self, id: str, name: str, created: str):
        self.id = id
        self.name = name
        self.created = created

    def get_event_type(self) -> str:
        return "AccountCreated"

    def toJson(self):
        data = self.__dict__.copy()
        data['created'] = self.created.isoformat()
        return json.dumps(data).encode('utf-8')


class AccountBalanceUpdated(Event):
    def __init__(self, id: str, delta: int, event_time: str):
        self.id = id
        self.delta = delta  # in cents
        self.event_time = event_time

    def get_event_type(self) -> str:
        return "AccountBalanceUpdated"

    def toJson(self):
        data = self.__dict__.copy()
        data['event_time'] = self.event_time.isoformat()
        return json.dumps(data).encode('utf-8')


@dataclass_json(letter_case=LetterCase.CAMEL)
@dataclass
class Account:
    id: str
    name: str
    created: str


# returns stream name
def generate_events(client: EventStoreDBClient) -> str:
    account = Account(id=str(uuid.uuid4()), name="Test",created=datetime.now(timezone.utc))
    stream_name = f'accounts-{account.id}'
    print(f"Stream Name: {stream_name}")
    account_created_event = AccountCreated(account.id, account.name, account.created)
    stream_current_version = client.get_current_version(stream_name=stream_name)
    client.append_event(stream_name=stream_name,
                        current_version=stream_current_version,
                        event=events.NewEvent(type=account_created_event.get_event_type(),
                                              data=account_created_event.toJson(),
                                              metadata=None,
                                              id=uuid.uuid4()))

    for _ in range(5):
        stream_current_version = client.get_current_version(stream_name=stream_name)
        temp = AccountBalanceUpdated(account.id, random.randint(-1000, 1000), datetime.now(timezone.utc))
        client.append_event(stream_name=stream_name,
                            current_version=stream_current_version,
                            event=events.NewEvent(type=temp.get_event_type(),
                                                  data=temp.toJson(),
                                                  metadata=None,
                                                  id=uuid.uuid4()))

    return stream_name


def read_stream(client: EventStoreDBClient, stream_name: str):
    stream_events = client.get_stream(stream_name=stream_name)
    for s in stream_events:
        try:
            print(f'\nEventType: {s.type}')
            print(json.dumps(json.loads(s.data.decode('utf-8')), indent=2))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            print(f"Error decoding or parsing JSON data: {e}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sample Python app")
    parser.add_argument('--connection-string', type=str, help='EventStoreDB connection string')
    args = parser.parse_args()

    client = EventStoreDBClient(uri=args.connection_string)

    stream_name = generate_events(client=client)
    read_stream(client, stream_name=stream_name)
