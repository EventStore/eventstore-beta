import argparse
import json
import os
import random
import uuid
from abc import ABC, abstractmethod
from argparse import Namespace
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


def read_file_to_string(file_path: str) -> str:
    try:
        with open(file_path, 'r') as file:
            return file.read().rstrip()
    except FileNotFoundError:
        raise Exception(f"File not found: {file_path}")
    except IOError:
        raise Exception(f"Error reading file: {file_path}")


def build_bundle(cert_path: str, key_path: str, ca_path: str) -> str:
    try:
        with open(cert_path, 'r') as cert_file:
            cert_content = cert_file.read().rstrip()
        with open(key_path, 'r') as key_file:
            key_content = key_file.read().rstrip()
        with open(ca_path, 'r') as ca_file:
            ca_content = ca_file.read().rstrip()
        return f"{cert_content}\n{key_content}\n{ca_content}"
    except FileNotFoundError as e:
        raise Exception(f"File not found: {e.filename}")
    except IOError as e:
        raise Exception(f"Error reading file: {e}")


def build_connection_string(args: Namespace) -> str:
    return f'esdb://{args.username}:{args.password}@{args.host}?UserCertFile={args.cert}&UserKeyFile={args.key}'


def validate_args(args: Namespace):
    if not args.password:
        raise Exception("password is required")
    if not args.host:
        raise Exception("host is required")
    # Validate cert, key, and ca files
    for arg_name, arg_value in [('cert', args.cert), ('key', args.key), ('ca', args.ca)]:
        if not arg_value:
            raise Exception(f"{arg_name} is required")
        if not os.path.isfile(arg_value):
            raise FileNotFoundError(f"{arg_name} must be a valid file path")
        if not os.access(arg_value, os.R_OK):
            raise Exception(f"{arg_name} must be a readable file")


def validate_connection_string(connection_string: str, cert_folder: str) -> tuple[str, str]:
    start_index = connection_string.find("?tlsCaFile=")
    if start_index == -1:
        start_index = connection_string.find("&tlsCaFile=")
        if start_index == -1:
            raise Exception("tlsCaFile parameter is required in the connection string")

    end_index = connection_string.find("&", start_index + 1)
    if end_index == -1:
        ca_file = connection_string[start_index + len("?tlsCaFile="):]
        if connection_string[start_index] == '?':
            connection_string = connection_string[:start_index] + "?"
        else:
            connection_string = connection_string[:start_index]
    else:
        ca_file = connection_string[start_index + len("?tlsCaFile="):end_index]
        if connection_string[start_index] == '?':
            connection_string = connection_string[:start_index + 1] + connection_string[end_index + 1:]
        else:
            connection_string = connection_string[:start_index] + connection_string[end_index:]

    ca_file = os.path.join(cert_folder, ca_file)
    connection_string = connection_string.replace("userCertFile=", f"userCertFile={os.path.join(cert_folder, '')}")
    connection_string = connection_string.replace("userKeyFile=", f"userKeyFile={os.path.join(cert_folder, '')}")

    if "userCertFile=" not in connection_string:
        raise Exception("userCertFile parameter is required in the connection string")
    if "userKeyFile=" not in connection_string:
        raise Exception("userKeyFile parameter is required in the connection string")

    return connection_string, ca_file


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sample Python app")
    parser.add_argument('--connection-string', type=str, help='EventStoreDB connection string')
    parser.add_argument('--cert-folder', type=str, help='Path to folder containing certificate files')
    args = parser.parse_args()

    conn_details = validate_connection_string(args.connection_string, args.cert_folder)
    ca = read_file_to_string(conn_details[1]) if conn_details[1] else None
    if ca is None:
        raise Exception("CA file is required")

    client = EventStoreDBClient(uri=conn_details[0], root_certificates=ca)

    stream_name = generate_events(client=client)
    read_stream(client, stream_name=stream_name)
