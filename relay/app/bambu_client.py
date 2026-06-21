"""Bambu LAN transport client.

Implements the documented LAN protocol (see docs/Bambu Printer LAN_Protocol_Layer.md):
  - MQTT over TLS on port 8883, username `bblp`, password = access code,
    self-signed cert (verification disabled — TOFU at the app layer).
  - Subscribe  device/{serial}/report   (status push)
  - Publish    device/{serial}/request  (commands)
  - `pushall` for a full state snapshot, then merge deltas into a cached state.
  - FTPS (implicit TLS, port 990) to upload sliced .3mf files to the printer SD.

This is a clean-room re-implementation from observed protocol behavior. The
proprietary Bambu networking plugin is NOT used.
"""
from __future__ import annotations

import ftplib
import json
import socket
import ssl
import threading
import time
from typing import Callable, Dict, Optional

import paho.mqtt.client as mqtt

from .config import PrinterConfig


class BambuLanClient:
    """One MQTT connection to one printer, with a cached merged status."""

    def __init__(self, cfg: PrinterConfig, on_status: Callable[[str, dict], None]):
        self.cfg = cfg
        self._on_status = on_status
        self._status: Dict = {}
        self._lock = threading.Lock()
        self._seq = 0
        self.connected = False

        self._client = mqtt.Client(
            mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"bambuprinterlan-relay-{cfg.serial}",
            protocol=mqtt.MQTTv311,
        )
        self._client.username_pw_set("bblp", cfg.access_code)
        # Printer presents a self-signed cert; do not verify the chain (LAN, TOFU).
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        self._client.tls_set_context(ctx)
        self._client.tls_insecure_set(True)
        self._client.on_connect = self._handle_connect
        self._client.on_message = self._handle_message
        self._client.on_disconnect = self._handle_disconnect

    # ---- lifecycle ----------------------------------------------------------
    def start(self) -> None:
        self._client.connect_async(self.cfg.ip, self.cfg.mqtt_port, keepalive=30)
        self._client.loop_start()

    def stop(self) -> None:
        self._client.loop_stop()
        try:
            self._client.disconnect()
        except Exception:
            pass

    @property
    def report_topic(self) -> str:
        return f"device/{self.cfg.serial}/report"

    @property
    def request_topic(self) -> str:
        return f"device/{self.cfg.serial}/request"

    # ---- MQTT callbacks -----------------------------------------------------
    def _handle_connect(self, client, userdata, flags, reason_code, properties=None):
        if reason_code == 0 or getattr(reason_code, "value", 1) == 0:
            self.connected = True
            client.subscribe(self.report_topic, qos=0)
            self.request_pushall()
        else:
            self.connected = False

    def _handle_disconnect(self, client, userdata, *args):
        self.connected = False

    def _handle_message(self, client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode("utf-8"))
        except Exception:
            return
        with self._lock:
            _deep_merge(self._status, payload)
            snapshot = json.loads(json.dumps(self._status))
        self._on_status(self.cfg.serial, snapshot)

    # ---- commands -----------------------------------------------------------
    def _next_seq(self) -> str:
        self._seq += 1
        return str(self._seq)

    def publish(self, category: str, command_obj: dict) -> None:
        body = dict(command_obj)
        body.setdefault("sequence_id", self._next_seq())
        self._client.publish(self.request_topic, json.dumps({category: body}), qos=0)

    def request_pushall(self) -> None:
        self.publish("pushing", {"command": "pushall", "version": 1, "push_target": 1})

    def send_command(self, category: str, command: dict) -> None:
        """Forward an arbitrary {category: {command,...}} from the app."""
        self.publish(category, command)

    def gcode_line(self, gcode: str) -> None:
        self.publish("print", {"command": "gcode_line", "param": gcode})

    def status(self) -> dict:
        with self._lock:
            return json.loads(json.dumps(self._status))

    # ---- FTPS upload --------------------------------------------------------
    def upload_3mf(self, remote_name: str, data: bytes) -> None:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        ftps = _ImplicitFTPS(context=ctx)
        ftps.connect(self.cfg.ip, self.cfg.ftps_port, timeout=30)
        try:
            ftps.login("bblp", self.cfg.access_code)
            ftps.prot_p()
            import io

            ftps.storbinary(f"STOR {remote_name}", io.BytesIO(data))
        finally:
            try:
                ftps.quit()
            except Exception:
                pass


class _ImplicitFTPS(ftplib.FTP_TLS):
    """ftplib speaks explicit FTPS; Bambu uses implicit TLS on 990."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._sock = None

    @property
    def sock(self):
        return self._sock

    @sock.setter
    def sock(self, value):
        if value is not None and not isinstance(value, ssl.SSLSocket):
            value = self.context.wrap_socket(value)
        self._sock = value

    def connect(self, host="", port=0, timeout=-999, source_address=None):
        if host:
            self.host = host
        if port:
            self.port = port
        if timeout != -999:
            self.timeout = timeout
        self._sock = socket.create_connection((self.host, self.port), self.timeout, source_address)
        self.af = self._sock.family
        self._sock = self.context.wrap_socket(self._sock, server_hostname=None)
        self.file = self._sock.makefile("r", encoding=self.encoding)
        self.welcome = self.getresp()
        return self.welcome


def _deep_merge(dst: dict, src: dict) -> None:
    for k, v in src.items():
        if isinstance(v, dict) and isinstance(dst.get(k), dict):
            _deep_merge(dst[k], v)
        else:
            dst[k] = v
