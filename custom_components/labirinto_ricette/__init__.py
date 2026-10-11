"""Labirinto Ricette: cerca ricette sui siti di cucina partendo dagli ingredienti."""

from __future__ import annotations

import asyncio
import logging
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
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.typing import ConfigType
from homeassistant.loader import async_get_integration

from . import websocket
from .const import (
    CARD_URL,
    CONF_SITES,
    DEFAULT_SITES,
    DOMAIN,
    OLD_SITE_URLS,
    PANEL_PATH,
    PANEL_URL,
    SIGNAL_UPDATE,
)
from .scraper import async_search_site

_LOGGER = logging.getLogger(__name__)
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


def _normalize(value: str | list[str]) -> list[str]:
    if isinstance(value, str):
        value = value.replace(";", ",").split(",")
    return [v.strip() for v in value if v and v.strip()]


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

    async def handle_search(call: ServiceCall) -> ServiceResponse:
        entries = hass.config_entries.async_entries(DOMAIN)
        if not entries:
            raise ServiceValidationError("Integrazione non configurata")
        entry = entries[0]
        sites = [
            s
            for s in entry.options.get(CONF_SITES, DEFAULT_SITES)
            if s.get("enabled", True)
        ]
        ingredients = _normalize(call.data["ingredients"])
        if not ingredients:
            raise ServiceValidationError("Inserisci almeno un ingrediente")
        if not sites:
            raise ServiceValidationError("Nessun sito di ricette attivo")

        query = " ".join(ingredients)
        limit = call.data["max_per_site"]
        outcomes = await asyncio.gather(
            *(async_search_site(hass, s, query, limit) for s in sites),
            return_exceptions=True,
        )

        per_site: list[list[dict]] = []
        errors: dict[str, str] = {}
        for site, outcome in zip(sites, outcomes):
            if isinstance(outcome, Exception):
                _LOGGER.warning("Ricerca su %s fallita: %s", site["name"], outcome)
                errors[site["name"]] = str(outcome) or type(outcome).__name__
            else:
                per_site.append(outcome)

        # Alterna i risultati dei vari siti
        results: list[dict] = []
        for i in range(limit):
            for items in per_site:
                if i < len(items):
                    results.append(items[i])

        response = {
            "query": query,
            "ingredients": ingredients,
            "results": results,
            "errors": errors,
        }
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
