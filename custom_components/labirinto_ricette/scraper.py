"""Ricerca e parsing delle pagine di risultati dei siti di ricette."""

from __future__ import annotations

import asyncio
import json
from typing import Any
from urllib.parse import quote_plus, urljoin, urlparse

from bs4 import BeautifulSoup

from homeassistant.core import HomeAssistant
from homeassistant.helpers.aiohttp_client import async_get_clientsession

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    ),
    "Accept-Language": "it-IT,it;q=0.9,en;q=0.5",
    "Accept": "text/html,application/xhtml+xml",
}


def _host(url: str) -> str:
    return urlparse(url).netloc.lower().removeprefix("www.")


def _walk(node: Any):
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from _walk(value)
    elif isinstance(node, list):
        for item in node:
            yield from _walk(item)


def _image_from(value: Any) -> str | None:
    if isinstance(value, str):
        return value
    if isinstance(value, list) and value:
        return _image_from(value[0])
    if isinstance(value, dict):
        return _image_from(value.get("url"))
    return None


def _img_src(img) -> str | None:
    for attr in ("data-src", "data-lazy-src", "data-original", "src"):
        src = img.get(attr)
        if src and not src.startswith("data:"):
            return src
    srcset = img.get("srcset") or img.get("data-srcset")
    if srcset:
        return srcset.split(",")[0].strip().split(" ")[0]
    return None


def parse_results(html: str, base_url: str, limit: int) -> list[dict[str, str]]:
    """Estrae (titolo, link, immagine) da una pagina di risultati."""
    soup = BeautifulSoup(html, "html.parser")
    host = _host(base_url)
    results: list[dict[str, str]] = []
    seen: set[str] = set()

    def add(title: str | None, link: str | None, image: str | None) -> None:
        if not title or not link:
            return
        title = " ".join(title.split())
        link = urljoin(base_url, link)
        if len(title) < 4 or link in seen or _host(link) != host:
            return
        if urlparse(link).path in ("", "/"):
            return
        seen.add(link)
        results.append(
            {
                "title": title,
                "url": link,
                "image": urljoin(base_url, image) if image else "",
            }
        )

    # 1) Dati strutturati JSON-LD (Recipe / ItemList)
    for tag in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(tag.string or "")
        except (ValueError, TypeError):
            continue
        for node in _walk(data):
            types = node.get("@type")
            types = types if isinstance(types, list) else [types]
            if "Recipe" in types or "ListItem" in types:
                item = node.get("item") if isinstance(node.get("item"), dict) else node
                add(
                    item.get("name"),
                    item.get("url") or item.get("@id"),
                    _image_from(item.get("image")),
                )

    # 2) Euristica: link che contengono un'immagine
    for a in soup.find_all("a", href=True):
        img = a.find("img")
        if img is None:
            continue
        src = _img_src(img)
        if not src or "logo" in src.lower():
            continue
        heading = a.find(["h1", "h2", "h3", "h4"])
        title = (
            (heading.get_text(" ", strip=True) if heading else None)
            or a.get("title")
            or img.get("alt")
            or a.get_text(" ", strip=True)
        )
        add(title, a["href"], src)

    return results[:limit]


async def async_search_site(
    hass: HomeAssistant, site: dict[str, Any], query: str, limit: int
) -> list[dict[str, str]]:
    """Cerca su un singolo sito."""
    url = site["url"].replace("{query}", quote_plus(query))
    session = async_get_clientsession(hass)
    async with asyncio.timeout(20):
        async with session.get(url, headers=HEADERS) as resp:
            resp.raise_for_status()
            html = await resp.text()
            final_url = str(resp.url)
    items = await hass.async_add_executor_job(parse_results, html, final_url, limit)
    for item in items:
        item["site"] = site["name"]
    return items
