"""Bambu Printer LAN relay server.

Holds one MQTT connection per printer and fans out live status to many app
clients over WebSocket. Forwards commands and FTPS uploads. Auth via a bearer
token (RELAY_TOKEN). Designed to run remotely behind Tailscale/WireGuard — do
not expose the port directly to the internet.

Endpoints:
  GET  /healthz
  GET  /printers
  GET  /printers/{serial}/status
  POST /printers/{serial}/command      {"category":"print","command":{...}}
  POST /printers/{serial}/gcode        {"gcode":"G28\n"}
  POST /printers/{serial}/pushall
  POST /printers/{serial}/files        multipart file -> FTPS upload to SD
  WS   /printers/{serial}/status       live status stream
"""
from __future__ import annotations

import asyncio
import contextlib
from typing import Dict, List, Set

from fastapi import (
    Depends,
    FastAPI,
    File,
    Form,
    HTTPException,
    UploadFile,
    WebSocket,
    WebSocketDisconnect,
)
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel

from .bambu_client import BambuLanClient
import json
import os

from .config import PrinterConfig, settings

app = FastAPI(title="Bambu Printer LAN Relay", version="0.1.0")
_bearer = HTTPBearer(auto_error=False)

_clients: Dict[str, BambuLanClient] = {}
_subscribers: Dict[str, Set[WebSocket]] = {}
_loop: asyncio.AbstractEventLoop | None = None

# Printers added at runtime from the app are persisted here (survives restart).
_PERSIST = os.environ.get("RELAY_DATA", "/data") + "/printers.json"


def _load_persisted() -> list[PrinterConfig]:
    try:
        with open(_PERSIST, "r", encoding="utf-8") as f:
            return [PrinterConfig(**p) for p in json.load(f)]
    except Exception:
        return []


def _save_persisted() -> None:
    runtime = [c.cfg for c in _clients.values()]
    try:
        os.makedirs(os.path.dirname(_PERSIST), exist_ok=True)
        with open(_PERSIST, "w", encoding="utf-8") as f:
            json.dump([c.model_dump() for c in runtime], f)
    except Exception:
        pass


def _start_client(cfg: PrinterConfig) -> None:
    if cfg.serial in _clients:
        _clients[cfg.serial].stop()
    client = BambuLanClient(cfg, _on_status)
    _clients[cfg.serial] = client
    _subscribers.setdefault(cfg.serial, set())
    client.start()


# ---- auth -------------------------------------------------------------------
# Auth is OPTIONAL: the relay runs on a trusted LAN. If RELAY_TOKEN is unset the
# relay is open (no token required). Set RELAY_TOKEN only if you expose it beyond
# the LAN (e.g. over a tunnel) and want bearer-token protection.
def require_token(creds: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> None:
    if not settings.relay_token:
        return  # open on LAN — no token configured
    if creds is None or creds.credentials != settings.relay_token:
        raise HTTPException(401, "Invalid relay token.")


def _check_ws_token(ws: WebSocket) -> bool:
    if not settings.relay_token:
        return True  # open on LAN
    token = ws.query_params.get("token") or ""
    auth = ws.headers.get("authorization", "")
    if auth.lower().startswith("bearer "):
        token = auth[7:]
    return token == settings.relay_token


# ---- status fan-out ---------------------------------------------------------
def _on_status(serial: str, snapshot: dict) -> None:
    if _loop is None:
        return
    asyncio.run_coroutine_threadsafe(_broadcast(serial, snapshot), _loop)


async def _broadcast(serial: str, snapshot: dict) -> None:
    for ws in list(_subscribers.get(serial, set())):
        try:
            await ws.send_json({"serial": serial, "status": snapshot})
        except Exception:
            _subscribers.get(serial, set()).discard(ws)


# ---- lifecycle --------------------------------------------------------------
@app.on_event("startup")
async def _startup() -> None:
    global _loop
    _loop = asyncio.get_running_loop()
    seen: Set[str] = set()
    for cfg in settings.load_printers() + _load_persisted():
        if cfg.serial in seen:
            continue
        seen.add(cfg.serial)
        _start_client(cfg)


@app.on_event("shutdown")
async def _shutdown() -> None:
    for client in _clients.values():
        client.stop()


# ---- models -----------------------------------------------------------------
class CommandBody(BaseModel):
    category: str
    command: dict


class GcodeBody(BaseModel):
    gcode: str


class AddPrinterBody(BaseModel):
    serial: str
    ip: str
    access_code: str
    name: str = ""


# ---- routes -----------------------------------------------------------------
@app.get("/healthz")
def healthz() -> dict:
    return {"ok": True, "printers": list(_clients.keys())}


@app.get("/printers", dependencies=[Depends(require_token)])
def list_printers() -> List[dict]:
    return [
        {"serial": c.cfg.serial, "name": c.cfg.name or c.cfg.serial,
         "ip": c.cfg.ip, "connected": c.connected}
        for c in _clients.values()
    ]


@app.post("/printers", dependencies=[Depends(require_token)])
def add_printer(body: AddPrinterBody) -> dict:
    if not body.serial or not body.ip or not body.access_code:
        raise HTTPException(400, "serial, ip and access_code are required.")
    cfg = PrinterConfig(serial=body.serial, ip=body.ip,
                        access_code=body.access_code, name=body.name)
    _start_client(cfg)
    _save_persisted()
    return {"ok": True, "serial": cfg.serial}


@app.delete("/printers/{serial}", dependencies=[Depends(require_token)])
def remove_printer(serial: str) -> dict:
    client = _clients.pop(serial, None)
    if client is None:
        raise HTTPException(404, "Unknown printer serial.")
    client.stop()
    _subscribers.pop(serial, None)
    _save_persisted()
    return {"ok": True}


def _get(serial: str) -> BambuLanClient:
    client = _clients.get(serial)
    if client is None:
        raise HTTPException(404, "Unknown printer serial.")
    return client


@app.get("/printers/{serial}/status", dependencies=[Depends(require_token)])
def get_status(serial: str) -> dict:
    return _get(serial).status()


@app.post("/printers/{serial}/command", dependencies=[Depends(require_token)])
def post_command(serial: str, body: CommandBody) -> dict:
    _get(serial).send_command(body.category, body.command)
    return {"ok": True}


@app.post("/printers/{serial}/gcode", dependencies=[Depends(require_token)])
def post_gcode(serial: str, body: GcodeBody) -> dict:
    _get(serial).gcode_line(body.gcode)
    return {"ok": True}


@app.post("/printers/{serial}/pushall", dependencies=[Depends(require_token)])
def post_pushall(serial: str) -> dict:
    _get(serial).request_pushall()
    return {"ok": True}


@app.post("/printers/{serial}/files", dependencies=[Depends(require_token)])
async def upload_file(serial: str, file: UploadFile = File(...), name: str = Form("")) -> dict:
    client = _get(serial)
    data = await file.read()
    remote = name or file.filename or "upload.3mf"
    await asyncio.to_thread(client.upload_3mf, remote, data)
    return {"ok": True, "remote": remote, "bytes": len(data)}


@app.websocket("/printers/{serial}/status")
async def ws_status(ws: WebSocket, serial: str) -> None:
    if not _check_ws_token(ws):
        await ws.close(code=4401)
        return
    if serial not in _clients:
        await ws.close(code=4404)
        return
    await ws.accept()
    _subscribers[serial].add(ws)
    # send current snapshot immediately
    with contextlib.suppress(Exception):
        await ws.send_json({"serial": serial, "status": _clients[serial].status()})
    try:
        while True:
            await ws.receive_text()  # keepalive / ignore inbound
    except WebSocketDisconnect:
        pass
    finally:
        _subscribers[serial].discard(ws)
