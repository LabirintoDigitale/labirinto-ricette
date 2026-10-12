"""Ricerca sui siti e lettura delle ricette, usate da servizio, pannello e link diretto."""

from __future__ import annotations

import asyncio
import logging
import re
from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError, ServiceValidationError

from .const import CONF_SITES, DEFAULT_SITES, DOMAIN
from .scraper import (
    is_api_item,
    async_fetch_recipe,
    async_get_page,
    async_search_site,
    site_domain,
)

_LOGGER = logging.getLogger(__name__)
RECIPE_CACHE_SIZE = 50

# Controllo dei risultati: scarta le pagine che non sono ricette (newsletter,
# pubblicità, articoli) cercando i dati strutturati della ricetta nella pagina.
EXTRA_CANDIDATES = 4
CHECK_CONCURRENCY = 10
CHECK_TIMEOUT = 12
CHECK_CACHE_SIZE = 1000
RECIPE_MARK = re.compile(r'"@type"\s*:\s*(?:\[[^\]]*)?"Recipe"|"recipeIngredient"')


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
    sem = asyncio.Semaphore(CHECK_CONCURRENCY)

    async def site_search(site: dict[str, Any]) -> list[dict]:
        items = await async_search_site(hass, site, query, limit + EXTRA_CANDIDATES)
        return await _async_only_recipes(hass, items, limit, sem)

    outcomes = await asyncio.gather(
        *(site_search(s) for s in sites), return_exceptions=True
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


async def _async_is_recipe(
    hass: HomeAssistant, url: str, sem: asyncio.Semaphore
) -> bool | None:
    """True/False se la pagina è/non è una ricetta, None se non si è potuta leggere."""
    if is_api_item(url) or url in hass.data[DOMAIN].get("recipes", {}):
        return True
    cache: dict[str, bool] = hass.data[DOMAIN].setdefault("checked", {})
    if url in cache:
        return cache[url]
    async with sem:
        try:
            async with asyncio.timeout(CHECK_TIMEOUT):
                html, _ = await async_get_page(hass, url)
        except Exception:  # noqa: BLE001 - pagina non verificabile, si tiene
            return None
    is_recipe = bool(RECIPE_MARK.search(html))
    if len(cache) >= CHECK_CACHE_SIZE:
        cache.pop(next(iter(cache)))
    cache[url] = is_recipe
    return is_recipe


async def _async_only_recipes(
    hass: HomeAssistant, items: list[dict], limit: int, sem: asyncio.Semaphore
) -> list[dict]:
    if not items:
        return items
    checks = await asyncio.gather(*(_async_is_recipe(hass, i["url"], sem) for i in items))
    if True not in checks:
        # Il sito non pubblica i dati strutturati: non si possono distinguere
        return items[:limit]
    return [item for item, ok in zip(items, checks) if ok is not False][:limit]
