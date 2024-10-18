# Sample Python App with EventStore Integration

This Python script connects to an EventStore DB, generates events for an account, and reads them back from the stream. It also allows you to pass in connection details and certificate paths via command-line arguments.

## Requirements

- Python 3.10+
- An EventStore DB instance running
- Certificates for secure communication with the EventStore DB running on EventStore Cloud

## Setup

### 1. Install Requirements

Create a virtual environment and then install the requirements.

```bash
python -m venv venv
source venv/bin/activate  # On Windows use `venv\Scripts\activate`

pip install -r requirements.txt
```

### 2. Prepare paramaters

### Certificates

Ensure you have downloaded the security certificates from the esdb cloud console and untarred the certificate_bundle.tar.gz file.

### Connection string

Grab the connection string from the eventstore cloud console.

An example connection string looks like the following:

```
esdb+discover://admin:somepassword@esdb.cs8lpa0a78v4qvbn1nlg.cs8i1c8a78v4qvbn1mtg.sites.dev.eventstore.cloud:2113?userCertFile=tls.crt&tlsCaFile=ca.crt&userKeyFile=tls.key
```

## Running the Application

### 1. Run the script

```bash
python sample.py --connection-string "<CONNECTION_STRING>" --cert-folder Path/to/certs
```

### 2. Output

The sample application should create a stream and then emit 6 events. It will then read the stream back and print out the event data of each event.
