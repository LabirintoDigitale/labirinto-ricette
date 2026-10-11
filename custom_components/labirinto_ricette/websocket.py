"""Comandi websocket usati dal pannello laterale."""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback

from .const import CONF_SITES, DEFAULT_SITES, DOMAIN
from .scraper import async_fetch_recipe, async_search_site, site_domain

RECIPE_CACHE_SIZE = 50

SITE_SCHEMA = vol.Schema(
    {
        vol.Required("name"): vol.All(str, vol.Strip, vol.Length(min=1, max=60)),
        vol.Required("url"): vol.All(str, vol.Strip),
        vol.Optional("enabled", default=True): bool,
    }
)


def _entry(hass: HomeAssistant) -> ConfigEntry | None:
    entries = hass.config_entries.async_entries(DOMAIN)
    return entries[0] if entries else None


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


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/config"})
@callback
def ws_config(
    hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict
) -> None:
    """Siti configurati, siti predefiniti e ultima ricerca."""
    entry = _entry(hass)
    if entry is None:
        connection.send_error(msg["id"], "not_configured", "Integrazione non configurata")
        return
    connection.send_result(
        msg["id"],
        {
            "sites": entry.options.get(CONF_SITES, DEFAULT_SITES),
            "defaults": DEFAULT_SITES,
            "last": hass.data[DOMAIN].get("last"),
        },
    )


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
    url = msg["url"]
    entry = _entry(hass)
    sites = entry.options.get(CONF_SITES, DEFAULT_SITES) if entry else []
    # Solo pagine dei siti configurati: il pannello non deve scaricare URL qualsiasi
    if not url.startswith(("http://", "https://")) or site_domain(url) not in {
        site_domain(s["url"]) for s in sites
    }:
        connection.send_error(msg["id"], "invalid_url", "Questo indirizzo non è di un sito configurato")
        return
    cache: dict[str, Any] = hass.data[DOMAIN].setdefault("recipes", {})
    if url not in cache:
        try:
            recipe = await async_fetch_recipe(hass, url)
        except Exception as err:  # noqa: BLE001 - riportato all'utente
            connection.send_error(
                msg["id"], "fetch_failed", str(err) or type(err).__name__
            )
            return
        if len(cache) >= RECIPE_CACHE_SIZE:
            cache.pop(next(iter(cache)))
        cache[url] = recipe
    connection.send_result(msg["id"], cache[url])
