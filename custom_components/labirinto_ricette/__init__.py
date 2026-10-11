"""Labirinto Ricette: cerca ricette sui siti di cucina partendo dagli ingredienti."""

from __future__ import annotations

import secrets
from pathlib import Path

import voluptuous as vol

from homeassistant.components import frontend, panel_custom
from homeassistant.components.frontend import add_extra_js_url
from homeassistant.components.http import StaticPathConfig
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import (
    HomeAssistant,
    ServiceCall,
    ServiceResponse,
    SupportsResponse,
)
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.typing import ConfigType
from homeassistant.loader import async_get_integration

from . import share, websocket
from .const import (
    CARD_URL,
    CONF_SHARE_TOKEN,
    CONF_SITES,
    DOMAIN,
    OLD_SITE_URLS,
    PANEL_PATH,
    PANEL_URL,
    SIGNAL_UPDATE,
)
from .search import async_search, normalize

PLATFORMS = ["sensor"]
CONFIG_SCHEMA = cv.config_entry_only_config_schema(DOMAIN)

SEARCH_SCHEMA = vol.Schema(
    {
        vol.Required("ingredients"): vol.Any(cv.string, [cv.string]),
        vol.Optional("max_per_site", default=6): vol.All(
            vol.Coerce(int), vol.Range(min=1, max=20)
        ),
    }
)


async def async_setup(hass: HomeAssistant, config: ConfigType) -> bool:
    """Registra servizio, comandi websocket, carta Lovelace e file del pannello."""
    hass.data.setdefault(DOMAIN, {})

    www = Path(__file__).parent / "www"
    await hass.http.async_register_static_paths(
        [
            StaticPathConfig(CARD_URL, str(www / "labirinto-ricette-card.js"), False),
            StaticPathConfig(PANEL_URL, str(www / "labirinto-ricette-panel.js"), False),
        ]
    )
    add_extra_js_url(hass, CARD_URL)
    websocket.async_register(hass)
    share.async_register(hass)

    async def handle_search(call: ServiceCall) -> ServiceResponse:
        response = await async_search(
            hass, normalize(call.data["ingredients"]), call.data["max_per_site"]
        )
        hass.data[DOMAIN]["last"] = response
        async_dispatcher_send(hass, SIGNAL_UPDATE)
        return response

    hass.services.async_register(
        DOMAIN,
        "search",
        handle_search,
        schema=SEARCH_SCHEMA,
        supports_response=SupportsResponse.ONLY,
    )
    return True


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    # I siti vengono letti dalle opzioni a ogni ricerca: cambiarli non richiede reload.
    sites = entry.options.get(CONF_SITES)
    if sites and any(s["url"] in OLD_SITE_URLS for s in sites):
        hass.config_entries.async_update_entry(
            entry,
            options={
                **entry.options,
                CONF_SITES: [
                    {**s, "url": OLD_SITE_URLS.get(s["url"], s["url"])} for s in sites
                ],
            },
        )
    if CONF_SHARE_TOKEN not in entry.data:
        hass.config_entries.async_update_entry(
            entry, data={**entry.data, CONF_SHARE_TOKEN: secrets.token_urlsafe(16)}
        )
    integration = await async_get_integration(hass, DOMAIN)
    await panel_custom.async_register_panel(
        hass,
        frontend_url_path=PANEL_PATH,
        webcomponent_name="labirinto-ricette-panel",
        sidebar_title="Ricette",
        sidebar_icon="mdi:chef-hat",
        module_url=f"{PANEL_URL}?v={integration.version}",
        require_admin=False,
    )
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    frontend.async_remove_panel(hass, PANEL_PATH)
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
