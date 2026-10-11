"""Ricette preferite: testo e immagini salvati in locale, anche se il sito sparisce."""

from __future__ import annotations

import asyncio
import copy
import hashlib
import logging
import re
from http import HTTPStatus
from pathlib import Path
from typing import Any

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .const import DOMAIN
from .scraper import HEADERS
from .search import async_get_recipe

_LOGGER = logging.getLogger(__name__)

IMG_URL = f"/api/{DOMAIN}/img"
MAX_IMAGES = 60
MAX_IMAGE_BYTES = 8 * 1024 * 1024
EXTENSIONS = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
    "image/avif": ".avif",
}
NAME_RE = re.compile(r"^[0-9a-f]{24}\.(jpg|png|webp|gif|avif)$")


def favorite_id(url: str) -> str:
    return hashlib.sha1(url.encode()).hexdigest()[:16]


class Favorites:
    """Archivio dei preferiti (.storage/labirinto_ricette.favorites + immagini)."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self.store: Store[dict[str, Any]] = Store(hass, 1, f"{DOMAIN}.favorites")
        self.image_dir = Path(hass.config.path(DOMAIN, "images"))
        self.items: dict[str, dict[str, Any]] = {}
        self._lock = asyncio.Lock()

    async def async_load(self) -> None:
        data = await self.store.async_load() or {}
        self.items = data.get("favorites", {})

    def _schedule_save(self) -> None:
        self.store.async_delay_save(lambda: {"favorites": self.items}, 1)

    def summaries(self) -> list[dict[str, Any]]:
        items = sorted(self.items.values(), key=lambda f: f["saved_at"], reverse=True)
        return [
            {
                "id": f["id"],
                "url": f["url"],
                "title": f["title"],
                "site": f["site"],
                "image": f["recipe"].get("image", ""),
                "saved_at": f["saved_at"],
            }
            for f in items
        ]

    def get(self, fid: str) -> dict[str, Any]:
        if fid not in self.items:
            raise HomeAssistantError("Preferito non trovato")
        return self.items[fid]

    async def async_add(
        self, url: str, site: str = "", title: str = "", image: str = ""
    ) -> dict[str, Any]:
        fid = favorite_id(url)
        async with self._lock:
            if fid in self.items:
                return self.items[fid]
            recipe = copy.deepcopy(await async_get_recipe(self.hass, url))
            if not recipe.get("image") and image:
                recipe["image"] = image

            sources = [recipe["image"]] if recipe.get("image") else []
            for step in recipe.get("steps", []):
                sources.extend(step.get("images", []))
            sources = list(dict.fromkeys(s for s in sources if s.startswith("http")))
            local: dict[str, str] = {}
            files: list[str] = []
            for src in sources[:MAX_IMAGES]:
                try:
                    name = await self._async_download(src)
                except Exception as err:  # noqa: BLE001 - l'immagine resta remota
                    _LOGGER.debug("Immagine non salvata %s: %s", src, err)
                    continue
                local[src] = f"{IMG_URL}/{name}"
                files.append(name)

            if recipe.get("image") in local:
                recipe["image"] = local[recipe["image"]]
            for step in recipe.get("steps", []):
                if "images" in step:
                    step["images"] = [local.get(i, i) for i in step["images"]]

            fav = {
                "id": fid,
                "url": url,
                "site": site,
                "title": recipe.get("title") or title,
                "saved_at": dt_util.utcnow().isoformat(),
                "files": files,
                "recipe": recipe,
            }
            self.items[fid] = fav
            self._schedule_save()
            return fav

    async def async_remove(self, fid: str) -> None:
        async with self._lock:
            fav = self.items.pop(fid, None)
            if fav is None:
                return
            used = {name for f in self.items.values() for name in f.get("files", [])}
            orphans = [self.image_dir / n for n in fav.get("files", []) if n not in used]
            await self.hass.async_add_executor_job(_delete, orphans)
            self._schedule_save()

    async def _async_download(self, url: str) -> str:
        session = async_get_clientsession(self.hass)
        async with asyncio.timeout(30):
            async with session.get(url, headers=HEADERS) as resp:
                resp.raise_for_status()
                ctype = resp.headers.get("Content-Type", "").split(";")[0].strip().lower()
                if ctype not in EXTENSIONS:
                    raise ValueError(f"non è un'immagine ({ctype})")
                data = bytearray()
                async for chunk in resp.content.iter_chunked(65536):
                    data.extend(chunk)
                    if len(data) > MAX_IMAGE_BYTES:
                        raise ValueError("immagine troppo grande")
        name = hashlib.sha1(url.encode()).hexdigest()[:24] + EXTENSIONS[ctype]
        await self.hass.async_add_executor_job(_write, self.image_dir / name, bytes(data))
        return name


def _write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def _delete(paths: list[Path]) -> None:
    for path in paths:
        path.unlink(missing_ok=True)


class ImageView(HomeAssistantView):
    """Immagini salvate dei preferiti (nomi non indovinabili, servite senza login
    perché i tag <img> non possono mandare il token)."""

    url = IMG_URL + "/{name}"
    name = f"api:{DOMAIN}:img"
    requires_auth = False

    def __init__(self, favorites: Favorites) -> None:
        self.favorites = favorites

    async def get(self, request: web.Request, name: str) -> web.StreamResponse:
        path = self.favorites.image_dir / name
        if not NAME_RE.match(name) or not await request.app["hass"].async_add_executor_job(
            path.is_file
        ):
            return web.Response(status=HTTPStatus.NOT_FOUND)
        return web.FileResponse(path, headers={"Cache-Control": "public, max-age=31536000, immutable"})
