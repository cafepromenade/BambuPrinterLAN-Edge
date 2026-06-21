"""Relay configuration — all values come from the environment (never committed).

A printer is configured as a triplet: SERIAL, IP, ACCESS_CODE. Configure one or
more printers via PRINTERS (JSON) or the single-printer PRINTER_* shortcut.
"""
from __future__ import annotations

import json
from typing import List

from pydantic import BaseModel
from pydantic_settings import BaseSettings, SettingsConfigDict


class PrinterConfig(BaseModel):
    serial: str
    ip: str
    access_code: str
    name: str = ""
    mqtt_port: int = 8883
    ftps_port: int = 990


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Bearer token the Android app must present. Generate a random one; never commit it.
    relay_token: str = ""

    host: str = "0.0.0.0"
    port: int = 8979

    # Single-printer shortcut (optional)
    printer_serial: str = ""
    printer_ip: str = ""
    printer_access_code: str = ""
    printer_name: str = ""

    # Multi-printer JSON list (optional), e.g.
    # PRINTERS='[{"serial":"01P..","ip":"192.168.1.50","access_code":"12345678"}]'
    printers: str = ""

    def load_printers(self) -> List[PrinterConfig]:
        result: List[PrinterConfig] = []
        if self.printers.strip():
            for item in json.loads(self.printers):
                result.append(PrinterConfig(**item))
        if self.printer_serial and self.printer_ip and self.printer_access_code:
            result.append(
                PrinterConfig(
                    serial=self.printer_serial,
                    ip=self.printer_ip,
                    access_code=self.printer_access_code,
                    name=self.printer_name,
                )
            )
        # de-dupe by serial
        seen = {}
        for p in result:
            seen[p.serial] = p
        return list(seen.values())


settings = Settings()
