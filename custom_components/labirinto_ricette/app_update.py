"""APK dell'app Android servito da Home Assistant, con la sua versione.

L'APK e app.json ({"version_code", "version_name"}) sono allegati alla release
GitHub della stessa versione dell'integrazione: vengono scaricati la prima
volta che servono e tenuti in config/labirinto_ricette/app, così l'app può
controllare se è aggiornata e scaricare la nuova versione da Home Assistant.
"""

from __future__ import annotations

import asyncio
import json
import logging
from http import HTTPStatus
from pathlib import Path
from typing import Any

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.loader import async_get_integration

from .const import DOMAIN

_LOGGER = logging.getLogger(__name__)

RELEASE_URL = "https://github.com/LabirintoDigitale/labirinto-ricette/releases/download/v{version}/{name}"
APK_NAME = "LabirintoRicette.apk"
INFO_NAME = "app.json"
APK_URL = f"/api/{DOMAIN}/app.apk"
MAX_APK_BYTES = 100 * 1024 * 1024


class AppUpdate:
    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self.dir = Path(hass.config.path(DOMAIN, "app"))
        self._lock = asyncio.Lock()
        self._info: dict[str, Any] | None = None

    async def _version(self) -> str:
        return (await async_get_integration(self.hass, DOMAIN)).version or ""

    async def _fetch(self, name: str, target: Path) -> None:
        url = RELEASE_URL.format(version=await self._version(), name=name)
        session = async_get_clientsession(self.hass)
        async with asyncio.timeout(120):
            async with session.get(url) as resp:
                if resp.status == HTTPStatus.NOT_FOUND:
                    raise HomeAssistantError("Questa versione non ha l'app Android")
                resp.raise_for_status()
                data = bytearray()
                async for chunk in resp.content.iter_chunked(65536):
                    data.extend(chunk)
                    if len(data) > MAX_APK_BYTES:
                        raise HomeAssistantError("File dell'app troppo grande")
        await self.hass.async_add_executor_job(_write, target, bytes(data))

    async def async_info(self) -> dict[str, Any]:
        """Versione dell'app che corrisponde a questa versione dell'integrazione."""
        async with self._lock:
            if self._info is None:
                version = await self._version()
                path = self.dir / f"app-{version}.json"
                if not await self.hass.async_add_executor_job(path.is_file):
                    try:
                        await self._fetch(INFO_NAME, path)
                    except HomeAssistantError:
                        raise
                    except Exception as err:
                        raise HomeAssistantError(f"Non riesco a leggere la versione dell'app: {err}") from err
                raw = await self.hass.async_add_executor_job(path.read_text)
                data = json.loads(raw)
                self._info = {
                    "version_code": int(data["version_code"]),
                    "version_name": str(data.get("version_name", "")),
                    "apk_path": APK_URL,
                }
            return self._info

    async def async_apk(self) -> Path:
        """Percorso dell'APK (scaricato la prima volta)."""
        info = await self.async_info()
        async with self._lock:
            path = self.dir / f"LabirintoRicette-{info['version_code']}.apk"
            if not await self.hass.async_add_executor_job(path.is_file):
                await self._fetch(APK_NAME, path)
                await self.hass.async_add_executor_job(_prune, self.dir, path.name)
            return path


def _write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".part")
    tmp.write_bytes(data)
    tmp.replace(path)


def _prune(folder: Path, keep: str) -> None:
    """Tiene solo l'APK attuale."""
    for old in folder.glob("LabirintoRicette-*.apk"):
        if old.name != keep:
            old.unlink(missing_ok=True)


class AppApkView(HomeAssistantView):
    """Scarica l'APK (serve il login, o un link firmato dal pannello)."""

    url = APK_URL
    name = f"api:{DOMAIN}:app_apk"
    requires_auth = True

    def __init__(self, app: AppUpdate) -> None:
        self.app = app

    async def get(self, request: web.Request) -> web.StreamResponse:
        try:
            path = await self.app.async_apk()
        except HomeAssistantError as err:
            return self.json_message(str(err), HTTPStatus.NOT_FOUND)
        except Exception as err:  # noqa: BLE001 - GitHub non raggiungibile
            _LOGGER.warning("APK non scaricato: %s", err)
            return self.json_message("Non riesco a scaricare l'app", HTTPStatus.BAD_GATEWAY)
        return web.FileResponse(
            path,
            headers={
                "Content-Type": "application/vnd.android.package-archive",
                "Content-Disposition": f'attachment; filename="{APK_NAME}"',
            },
        )
