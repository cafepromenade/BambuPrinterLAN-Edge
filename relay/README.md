# Bambu Printer LAN Relay

Optional bridge server. Holds **one** MQTT connection per printer and fans out
live status to **many** Bambu Printer LAN app clients over WebSocket — sidestepping the
printer's small concurrent-MQTT-client cap — and enables **remote access without
Bambu cloud** when reached over a private tunnel (Tailscale/WireGuard).

Clean-room re-implementation of the Bambu LAN protocol (MQTT 8883 + FTPS 990).
The proprietary Bambu networking plugin is **not** used. See
[`../docs/Bambu Printer LAN_Protocol_Layer.md`](../docs/Bambu Printer LAN_Protocol_Layer.md).

## Quick start (Docker)
```bash
cp .env.example .env       # fill RELAY_TOKEN + printer SERIAL/IP/ACCESS_CODE
docker compose up -d --build
curl -s localhost:8979/healthz
```

## Quick start (plain Python)
```bash
python -m venv .venv && . .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env        # fill in values
uvicorn app.main:app --host 0.0.0.0 --port 8979
```

## Auth is optional (LAN-only by default)
The relay runs on a trusted LAN, so **no token is required** — leave `RELAY_TOKEN`
empty and the API is open. Only set `RELAY_TOKEN` if you expose the relay beyond
the LAN (e.g. over a Tailscale/WireGuard tunnel); then clients must send
`Authorization: Bearer <RELAY_TOKEN>`.

## API (`/healthz` is always open; others require a token only if `RELAY_TOKEN` is set)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/healthz` | liveness |
| GET | `/printers` | configured printers + connection state |
| GET | `/printers/{serial}/status` | latest merged status |
| POST | `/printers/{serial}/command` | `{"category":"print","command":{"command":"pause"}}` |
| POST | `/printers/{serial}/gcode` | `{"gcode":"G28\n"}` |
| POST | `/printers/{serial}/pushall` | force a full status snapshot |
| POST | `/printers/{serial}/files` | multipart upload → FTPS to SD |
| WS | `/printers/{serial}/status?token=…` | live status stream |

## Security
- `RELAY_TOKEN`, printer IP and access code live only in `.env` (gitignored) or
  the host environment — never committed.
- Printer TLS certs are self-signed; the relay does not verify the chain (LAN
  TOFU). The app pins the relay's own cert/token.
- **Do not** expose port 8979 publicly. Use a private tunnel.

## LAN discovery in Docker
UDP multicast doesn't cross Docker's bridge. For auto-discovery use Linux
`network_mode: host` (see `docker-compose.yml`); otherwise set printer IPs
explicitly (works everywhere).
