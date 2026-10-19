"""Scheda News: notizie dal sensore della card "RSS News" e riassunti dal suo server.

Non si configura nulla qui: entità, indirizzo del server e token vengono letti dalla
card ``custom:rss-news-card`` già presente in una dashboard. Le richieste al server
delle notizie partono da Home Assistant, così funzionano anche dal link diretto e
senza problemi di HTTPS/CORS nel browser.
"""

from __future__ import annotations

import asyncio
import logging
import re
import time
from email.utils import parsedate_to_datetime
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.util import dt as dt_util

_LOGGER = logging.getLogger(__name__)

CARD_TYPE = "custom:rss-news-card"
# Gli stessi valori predefiniti della card
DEFAULT_ENTITY = "sensor.news_aggregator"
DEFAULT_SERVER = "http://192.168.1.249/news-aggregator/"
CONFIG_TTL = 5 * 60
BLOCKED_TTL = 60
MAX_ARTICLES = 200
SUMMARY_TIMEOUT = 55

_cache: dict[str, Any] = {}


def _walk(node: Any):
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from _walk(value)
    elif isinstance(node, list):
        for item in node:
            yield from _walk(item)


def _dashboards(hass: HomeAssistant) -> list[Any]:
    data = hass.data.get("lovelace")
    if data is None:
        return []
    dashboards = getattr(data, "dashboards", None)
    if dashboards is None and isinstance(data, dict):
        dashboards = data.get("dashboards")
    return list((dashboards or {}).values())


async def async_card_config(hass: HomeAssistant) -> dict[str, str]:
    """Impostazioni della prima card News trovata nelle dashboard."""
    cached = _cache.get("config")
    if cached and cached[0] > time.monotonic():
        return cached[1]
    found: dict[str, Any] = {}
    for dashboard in _dashboards(hass):
        try:
            config = await dashboard.async_load(False)
        except Exception:  # noqa: BLE001 - dashboard vuota o in YAML non valido
            continue
        card = next((n for n in _walk(config) if n.get("type") == CARD_TYPE), None)
        if card:
            found = card
            break
    server = str(found.get("feed_admin_url") or DEFAULT_SERVER).strip()
    server = re.sub(r"[^/]*\.php.*$", "", server)
    if not server.endswith("/"):
        server += "/"
    result = {
        "entity": str(found.get("entity") or DEFAULT_ENTITY),
        "server": server,
        "token": str(found.get("feed_admin_token") or "").strip(),
        "title": str(found.get("title") or ""),
        "found": bool(found),
    }
    _cache["config"] = (time.monotonic() + CONFIG_TTL, result)
    return result


async def _async_get_json(hass: HomeAssistant, url: str, params: dict, token: str, timeout: int) -> Any:
    session = async_get_clientsession(hass)
    async with asyncio.timeout(timeout):
        async with session.get(
            url, params={**params, "token": token}, headers={"X-API-Token": token}
        ) as resp:
            try:
                data = await resp.json(content_type=None)
            except ValueError as err:
                raise HomeAssistantError(f"Risposta non valida dal server ({resp.status})") from err
            if resp.status >= 400 and not isinstance(data, dict):
                raise HomeAssistantError(f"HTTP {resp.status}")
            return data


async def _async_blocked(hass: HomeAssistant, cfg: dict[str, str]) -> list[str]:
    """Percorsi bloccati con la card (si rispettano, ma qui non se ne aggiungono)."""
    cached = _cache.get("blocked")
    if cached and cached[0] > time.monotonic():
        return cached[1]
    patterns: list[str] = cached[1] if cached else []
    try:
        data = await _async_get_json(hass, cfg["server"] + "block_paths.php", {}, cfg["token"], 10)
        patterns = [b.get("pattern") for b in data.get("blocked", []) if b.get("pattern")]
    except Exception as err:  # noqa: BLE001 - senza filtro finché il server non risponde
        _LOGGER.debug("Percorsi bloccati non disponibili: %s", err)
    _cache["blocked"] = (time.monotonic() + BLOCKED_TTL, patterns)
    return patterns


def _timestamp(value: Any) -> float:
    if not isinstance(value, str) or not value:
        return 0.0
    try:
        return parsedate_to_datetime(value).timestamp()
    except (TypeError, ValueError):
        parsed = dt_util.parse_datetime(value)
        return parsed.timestamp() if parsed else 0.0


def _strip_html(text: Any) -> str:
    if not isinstance(text, str):
        return ""
    text = re.sub(r"<[^>]+>", " ", text)
    return " ".join(text.replace("&nbsp;", " ").replace("&amp;", "&").split())


def _image(article: dict[str, Any]) -> str:
    if str(article.get("image") or "").strip():
        return str(article["image"]).strip()
    for rel in article.get("related") or []:
        if isinstance(rel, dict) and str(rel.get("image") or "").strip():
            return str(rel["image"]).strip()
    return ""


async def async_articles(hass: HomeAssistant) -> dict[str, Any]:
    cfg = await async_card_config(hass)
    state = hass.states.get(cfg["entity"])
    if state is None:
        raise HomeAssistantError(f"Sensore delle notizie {cfg['entity']} non trovato")
    articles = state.attributes.get("articles")
    if not isinstance(articles, list):
        raise HomeAssistantError(f"Il sensore {cfg['entity']} non ha l'attributo articles")
    blocked = await _async_blocked(hass, cfg)
    items = []
    for a in articles:
        link = str(a.get("link") or "")
        if not link or not a.get("title") or any(p in link for p in blocked):
            continue
        topics = [str(t).strip() for t in a.get("topics") or [] if str(t).strip()]
        items.append(
            {
                "title": _strip_html(a.get("title")),
                "link": link,
                "description": _strip_html(a.get("description"))[:400],
                "image": _image(a),
                "source": str(a.get("source") or "").strip(),
                "topic": (topics[0][:1].upper() + topics[0][1:]) if topics else "",
                "pubDate": a.get("pubDate") or "",
                "ts": _timestamp(a.get("pubDate")),
            }
        )
    items.sort(key=lambda i: i["ts"], reverse=True)
    return {"title": cfg["title"], "articles": items[:MAX_ARTICLES]}


async def async_summary(hass: HomeAssistant, url: str, retry: bool = False) -> dict[str, Any]:
    """Riassunto (e traduzione) dal summarize.php del server delle notizie."""
    cfg = await async_card_config(hass)
    state = hass.states.get(cfg["entity"])
    links = {
        str(a.get("link"))
        for a in (state.attributes.get("articles") or [] if state else [])
        if isinstance(a, dict)
    }
    # Solo articoli del sensore: il server non deve scaricare indirizzi qualsiasi
    if url not in links:
        raise HomeAssistantError("Notizia non più presente")
    params = {"url": url}
    if retry:
        params["retry"] = "1"
    try:
        data = await _async_get_json(
            hass, cfg["server"] + "summarize.php", params, cfg["token"], SUMMARY_TIMEOUT
        )
    except TimeoutError as err:
        raise HomeAssistantError("Il server delle notizie non ha risposto in tempo") from err
    except HomeAssistantError:
        raise
    except Exception as err:
        raise HomeAssistantError(f"Server delle notizie non raggiungibile: {err}") from err
    if not isinstance(data, dict) or not data.get("ok"):
        raise HomeAssistantError((data or {}).get("error") or "Riassunto non disponibile")
    return data
