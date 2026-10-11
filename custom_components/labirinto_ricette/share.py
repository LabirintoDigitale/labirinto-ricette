"""Ricettario senza login: pagina e API raggiungibili con il link segreto."""

from __future__ import annotations

import hmac
from http import HTTPStatus
from pathlib import Path

from aiohttp import web

from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant, callback
from homeassistant.exceptions import HomeAssistantError
from homeassistant.loader import async_get_integration

from .const import CONF_SHARE_TOKEN, DOMAIN, PANEL_URL, SHARE_URL
from .search import async_get_recipe, async_search, get_entry, get_sites, normalize

APP_HTML = Path(__file__).parent / "www" / "app.html"
MAX_INGREDIENTS = 20


@callback
def async_register(hass: HomeAssistant) -> None:
    hass.http.register_view(ShareAppView())
    hass.http.register_view(ShareApiView())


def _valid(hass: HomeAssistant, token: str) -> bool:
    entry = get_entry(hass)
    expected = entry.data.get(CONF_SHARE_TOKEN) if entry else None
    return bool(expected) and hmac.compare_digest(token.encode(), expected.encode())


def _not_found() -> web.Response:
    return web.Response(status=HTTPStatus.NOT_FOUND, text="Link non valido")


class ShareAppView(HomeAssistantView):
    """La pagina del ricettario."""

    url = SHARE_URL + "/{token}"
    name = f"api:{DOMAIN}:app"
    requires_auth = False

    async def get(self, request: web.Request, token: str) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        if not _valid(hass, token):
            return _not_found()
        integration = await async_get_integration(hass, DOMAIN)
        html = await hass.async_add_executor_job(APP_HTML.read_text, "utf-8")
        html = html.replace("{{PANEL_URL}}", f"{PANEL_URL}?v={integration.version}")
        return web.Response(
            text=html,
            content_type="text/html",
            headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"},
        )


class ShareApiView(HomeAssistantView):
    """Configurazione (sola lettura), ricerca e ricetta per la pagina."""

    url = SHARE_URL + "/{token}/{action}"
    name = f"api:{DOMAIN}:app:api"
    requires_auth = False

    async def get(self, request: web.Request, token: str, action: str) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        if not _valid(hass, token):
            return _not_found()
        if action == "config":
            sites = [
                {"name": s["name"], "enabled": s.get("enabled", True)}
                for s in get_sites(hass)
            ]
            return self.json({"sites": sites, "defaults": [], "last": None})
        if action == "recipe":
            try:
                return self.json(await async_get_recipe(hass, request.query.get("url", "")))
            except HomeAssistantError as err:
                return self.json_message(str(err), HTTPStatus.BAD_REQUEST)
        return _not_found()

    async def post(self, request: web.Request, token: str, action: str) -> web.Response:
        hass: HomeAssistant = request.app["hass"]
        if not _valid(hass, token):
            return _not_found()
        if action != "search":
            return _not_found()
        try:
            body = await request.json()
            ingredients = normalize(body.get("ingredients", ""))[:MAX_INGREDIENTS]
            limit = max(1, min(int(body.get("max_per_site", 8)), 20))
        except (ValueError, TypeError, AttributeError):
            return self.json_message("Richiesta non valida", HTTPStatus.BAD_REQUEST)
        try:
            return self.json(await async_search(hass, ingredients, limit))
        except HomeAssistantError as err:
            return self.json_message(str(err), HTTPStatus.BAD_REQUEST)
