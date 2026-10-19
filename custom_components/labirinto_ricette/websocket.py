"""Comandi websocket usati dal pannello laterale."""

from __future__ import annotations

from typing import Any

import secrets

import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.core import HomeAssistant, callback

from homeassistant.exceptions import HomeAssistantError

from .const import CONF_SHARE_TOKEN, CONF_SITES, DEFAULT_SITES, DOMAIN, SHARE_URL
from .scraper import async_search_site
from .search import async_get_recipe, get_entry as _entry
from .news import async_articles, async_news_available, async_summary
from .todo import async_add_items, todo_lists

SITE_SCHEMA = vol.Schema(
    {
        vol.Required("name"): vol.All(str, vol.Strip, vol.Length(min=1, max=60)),
        vol.Required("url"): vol.All(str, vol.Strip),
        vol.Optional("enabled", default=True): bool,
    }
)


def _site_error(site: dict[str, Any]) -> str | None:
    url = site["url"]
    if not url.startswith(("http://", "https://")):
        return f"{site['name']}: l'URL deve iniziare con http:// o https://"
    if "{query}" not in url:
        return f"{site['name']}: l'URL deve contenere {{query}} al posto degli ingredienti"
    return None


@callback
def async_register(hass: HomeAssistant) -> None:
    websocket_api.async_register_command(hass, ws_config)
    websocket_api.async_register_command(hass, ws_save_sites)
    websocket_api.async_register_command(hass, ws_test_site)
    websocket_api.async_register_command(hass, ws_recipe)
    websocket_api.async_register_command(hass, ws_share_regenerate)
    websocket_api.async_register_command(hass, ws_favorites_list)
    websocket_api.async_register_command(hass, ws_favorites_get)
    websocket_api.async_register_command(hass, ws_favorites_add)
    websocket_api.async_register_command(hass, ws_favorites_remove)
    websocket_api.async_register_command(hass, ws_todo_lists)
    websocket_api.async_register_command(hass, ws_todo_add)
    websocket_api.async_register_command(hass, ws_news_list)
    websocket_api.async_register_command(hass, ws_news_summary)


def _share_path(token: str | None) -> str | None:
    return f"{SHARE_URL}/{token}" if token else None


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/config"})
@websocket_api.async_response
async def ws_config(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Siti configurati, siti predefiniti e ultima ricerca."""
    entry = _entry(hass)
    if entry is None:
        connection.send_error(msg["id"], "not_configured", "Integrazione non configurata")
        return
    result = {
        "sites": entry.options.get(CONF_SITES, DEFAULT_SITES),
        "defaults": DEFAULT_SITES,
        "last": hass.data[DOMAIN].get("last"),
        "news": await async_news_available(hass),
    }
    if connection.user.is_admin:
        result["share_path"] = _share_path(entry.data.get(CONF_SHARE_TOKEN))
    connection.send_result(msg["id"], result)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/share/regenerate"})
@websocket_api.require_admin
@callback
def ws_share_regenerate(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Nuovo link per il ricettario senza login: il vecchio smette di funzionare."""
    entry = _entry(hass)
    if entry is None:
        connection.send_error(msg["id"], "not_configured", "Integrazione non configurata")
        return
    token = secrets.token_urlsafe(16)
    hass.config_entries.async_update_entry(
        entry, data={**entry.data, CONF_SHARE_TOKEN: token}
    )
    connection.send_result(msg["id"], {"share_path": _share_path(token)})


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/sites/save",
        vol.Required("sites"): [SITE_SCHEMA],
    }
)
@websocket_api.require_admin
@callback
def ws_save_sites(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Salva l'elenco completo dei siti."""
    entry = _entry(hass)
    if entry is None:
        connection.send_error(msg["id"], "not_configured", "Integrazione non configurata")
        return
    sites: list[dict[str, Any]] = msg["sites"]
    seen: set[str] = set()
    for site in sites:
        if error := _site_error(site):
            connection.send_error(msg["id"], "invalid_site", error)
            return
        key = site["name"].casefold()
        if key in seen:
            connection.send_error(
                msg["id"], "duplicate", f"Il sito \"{site['name']}\" è presente due volte"
            )
            return
        seen.add(key)
    hass.config_entries.async_update_entry(
        entry, options={**entry.options, CONF_SITES: sites}
    )
    connection.send_result(msg["id"], {"sites": sites})


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/sites/test",
        vol.Required("url"): vol.All(str, vol.Strip),
        vol.Optional("query", default="pasta"): str,
    }
)
@websocket_api.require_admin
@websocket_api.async_response
async def ws_test_site(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Prova un URL di ricerca e dice quante ricette trova."""
    site = {"name": "Prova", "url": msg["url"]}
    if error := _site_error(site):
        connection.send_result(msg["id"], {"ok": False, "error": error})
        return
    try:
        items = await async_search_site(hass, site, msg["query"], 5)
    except Exception as err:  # noqa: BLE001 - riportato all'utente
        connection.send_result(
            msg["id"], {"ok": False, "error": str(err) or type(err).__name__}
        )
        return
    connection.send_result(msg["id"], {"ok": True, "count": len(items), "results": items})


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/recipe",
        vol.Required("url"): vol.All(str, vol.Strip),
    }
)
@websocket_api.async_response
async def ws_recipe(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Ingredienti, descrizione e passaggi di una ricetta."""
    try:
        recipe = await async_get_recipe(hass, msg["url"])
    except HomeAssistantError as err:
        connection.send_error(msg["id"], "fetch_failed", str(err))
        return
    connection.send_result(msg["id"], recipe)


# ---------------------------------------------------------------------------
# Preferiti


def _favorites(hass: HomeAssistant):
    return hass.data[DOMAIN]["favorites"]


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/favorites/list"})
@callback
def ws_favorites_list(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    connection.send_result(msg["id"], {"favorites": _favorites(hass).summaries()})


@websocket_api.websocket_command(
    {vol.Required("type"): f"{DOMAIN}/favorites/get", vol.Required("fav_id"): str}
)
@callback
def ws_favorites_get(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    try:
        connection.send_result(msg["id"], _favorites(hass).get(msg["fav_id"]))
    except HomeAssistantError as err:
        connection.send_error(msg["id"], "not_found", str(err))


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/favorites/add",
        vol.Required("url"): vol.All(str, vol.Strip),
        vol.Optional("site", default=""): str,
        vol.Optional("title", default=""): str,
        vol.Optional("image", default=""): str,
    }
)
@websocket_api.async_response
async def ws_favorites_add(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Salva testo e immagini della ricetta."""
    try:
        fav = await _favorites(hass).async_add(
            msg["url"], msg["site"], msg["title"], msg["image"]
        )
    except HomeAssistantError as err:
        connection.send_error(msg["id"], "save_failed", str(err))
        return
    connection.send_result(msg["id"], {"id": fav["id"]})


@websocket_api.websocket_command(
    {vol.Required("type"): f"{DOMAIN}/favorites/remove", vol.Required("fav_id"): str}
)
@websocket_api.async_response
async def ws_favorites_remove(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    await _favorites(hass).async_remove(msg["fav_id"])
    connection.send_result(msg["id"], {})


# ---------------------------------------------------------------------------
# Liste delle attività


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/todo/lists"})
@callback
def ws_todo_lists(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    connection.send_result(msg["id"], {"lists": todo_lists(hass)})


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/todo/add",
        vol.Required("entity_id"): str,
        vol.Required("items"): [str],
        vol.Optional("note", default=""): str,
    }
)
@websocket_api.async_response
async def ws_todo_add(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    try:
        added = await async_add_items(
            hass, msg["entity_id"], msg["items"], msg["note"], connection.context(msg)
        )
    except HomeAssistantError as err:
        connection.send_error(msg["id"], "add_failed", str(err))
        return
    connection.send_result(msg["id"], {"added": added})


# ---------------------------------------------------------------------------
# News (sola lettura)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/news/list"})
@websocket_api.async_response
async def ws_news_list(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    try:
        connection.send_result(msg["id"], await async_articles(hass))
    except HomeAssistantError as err:
        connection.send_error(msg["id"], "news_failed", str(err))


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/news/summary",
        vol.Required("url"): str,
        vol.Optional("retry", default=False): bool,
    }
)
@websocket_api.async_response
async def ws_news_summary(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    try:
        connection.send_result(
            msg["id"], await async_summary(hass, msg["url"], msg["retry"])
        )
    except HomeAssistantError as err:
        connection.send_error(msg["id"], "summary_failed", str(err))
