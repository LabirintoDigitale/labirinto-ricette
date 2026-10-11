"""Ricerca sui siti e lettura delle ricette, usate da servizio, pannello e link diretto."""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError, ServiceValidationError

from .const import CONF_SITES, DEFAULT_SITES, DOMAIN
from .scraper import async_fetch_recipe, async_search_site, site_domain

_LOGGER = logging.getLogger(__name__)
RECIPE_CACHE_SIZE = 50


def get_entry(hass: HomeAssistant) -> ConfigEntry | None:
    entries = hass.config_entries.async_entries(DOMAIN)
    return entries[0] if entries else None


def get_sites(hass: HomeAssistant) -> list[dict[str, Any]]:
    entry = get_entry(hass)
    return entry.options.get(CONF_SITES, DEFAULT_SITES) if entry else []


def normalize(value: str | list[str]) -> list[str]:
    if isinstance(value, str):
        value = value.replace(";", ",").split(",")
    return [v.strip() for v in value if v and v.strip()]


async def async_search(
    hass: HomeAssistant, ingredients: list[str], limit: int
) -> dict[str, Any]:
    """Cerca su tutti i siti attivi e alterna i risultati."""
    if get_entry(hass) is None:
        raise ServiceValidationError("Integrazione non configurata")
    sites = [s for s in get_sites(hass) if s.get("enabled", True)]
    if not ingredients:
        raise ServiceValidationError("Inserisci almeno un ingrediente")
    if not sites:
        raise ServiceValidationError("Nessun sito di ricette attivo")

    query = " ".join(ingredients)
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

    results: list[dict] = []
    for i in range(limit):
        for items in per_site:
            if i < len(items):
                results.append(items[i])

    return {
        "query": query,
        "ingredients": ingredients,
        "results": results,
        "errors": errors,
    }


async def async_get_recipe(hass: HomeAssistant, url: str) -> dict[str, Any]:
    """Ricetta da una pagina dei siti configurati (con cache)."""
    # Solo pagine dei siti configurati: non si devono poter scaricare URL qualsiasi
    if not url.startswith(("http://", "https://")) or site_domain(url) not in {
        site_domain(s["url"]) for s in get_sites(hass)
    }:
        raise ServiceValidationError("Questo indirizzo non è di un sito configurato")
    cache: dict[str, Any] = hass.data[DOMAIN].setdefault("recipes", {})
    if url not in cache:
        try:
            recipe = await async_fetch_recipe(hass, url)
        except Exception as err:
            raise HomeAssistantError(str(err) or type(err).__name__) from err
        if len(cache) >= RECIPE_CACHE_SIZE:
            cache.pop(next(iter(cache)))
        cache[url] = recipe
    return cache[url]
