"""Ricerca e parsing delle pagine di risultati e delle ricette."""

from __future__ import annotations

import asyncio
import html as htmllib
import json
import re
import time
from typing import Any
from urllib.parse import quote_plus, urldefrag, urljoin, urlparse

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

# Testi alternativi delle immagini che non sono titoli
GENERIC_TITLES = {"immagine", "image", "foto", "photo", "img", "video", "ricetta", "leggi"}


def _host(url: str) -> str:
    return urlparse(url).netloc.lower().removeprefix("www.")


def site_domain(url: str) -> str:
    """Dominio del sito senza sottodomini (ricette.giallozafferano.it -> giallozafferano.it)."""
    return ".".join(_host(url).split(":")[0].split(".")[-2:])


def _walk(node: Any):
    if isinstance(node, dict):
        if node.get("@type") == "BreadcrumbList":
            return
        yield node
        for value in node.values():
            yield from _walk(value)
    elif isinstance(node, list):
        for item in node:
            yield from _walk(item)


def _types(node: dict) -> list:
    types = node.get("@type")
    return types if isinstance(types, list) else [types]


def _json_ld(soup: BeautifulSoup):
    for tag in soup.find_all("script", type="application/ld+json"):
        try:
            yield json.loads(tag.string or "", strict=False)
        except (ValueError, TypeError):
            continue


def _clean(text: Any) -> str:
    """Toglie entità HTML, tag e spazi in eccesso."""
    if not isinstance(text, str):
        return ""
    text = htmllib.unescape(text)
    if "<" in text:
        text = BeautifulSoup(text, "html.parser").get_text(" ")
    return " ".join(text.split())


def _image_from(value: Any) -> str | None:
    if isinstance(value, str):
        return value
    if isinstance(value, list) and value:
        return _image_from(value[0])
    if isinstance(value, dict):
        return _image_from(value.get("url") or value.get("contentUrl"))
    return None


def _images_from(value: Any) -> list[str]:
    if isinstance(value, list):
        return [i for v in value if (i := _image_from(v))]
    image = _image_from(value)
    return [image] if image else []


def _img_src(img) -> str | None:
    for attr in ("data-src", "data-lazy-src", "data-original", "src"):
        src = img.get(attr)
        if src and not src.startswith("data:"):
            return src
    srcset = img.get("srcset") or img.get("data-srcset")
    if srcset:
        return srcset.split(",")[0].strip().split(" ")[0]
    return None


def _strip_prefix(title: str) -> str:
    """Toglie il prefisso "Ricetta" dai titoli (es. Misya)."""
    return re.sub(r"^ricetta\s*:?\s+", "", title, flags=re.IGNORECASE)


def _good_title(text: str | None) -> str | None:
    """Scarta titoli generici ("Immagine") e slug ("gnocchi-di-cavolo-nero")."""
    if not text:
        return None
    text = _strip_prefix(" ".join(htmllib.unescape(text).split()))
    if len(text) < 4 or text.lower() in GENERIC_TITLES:
        return None
    if " " not in text and ("-" in text or "_" in text):
        return None
    return text


def parse_results(html: str, base_url: str, limit: int) -> list[dict[str, str]]:
    """Estrae (titolo, link, immagine) da una pagina di risultati."""
    soup = BeautifulSoup(html, "html.parser")
    domain = site_domain(base_url)

    def norm(link: str | None) -> str | None:
        if not link:
            return None
        link = urldefrag(urljoin(base_url, link))[0]
        parsed = urlparse(link)
        if parsed.scheme not in ("http", "https") or site_domain(link) != domain:
            return None
        if parsed.path in ("", "/") or parsed.path.rstrip("/") == urlparse(base_url).path.rstrip("/"):
            return None
        return link

    # Titoli candidati per ogni link: testo dei link senza immagine, attributi title,
    # titoli h1-h4. Servono quando foto e titolo sono due link separati (es. Cookist).
    titles: dict[str, str] = {}
    for a in soup.find_all("a", href=True):
        link = norm(a["href"])
        if not link or link in titles:
            continue
        heading = a.find(["h1", "h2", "h3", "h4"])
        for candidate in (
            heading.get_text(" ", strip=True) if heading else None,
            None if a.find("img") else a.get_text(" ", strip=True),
            a.get("title"),
            a.get("aria-label"),
        ):
            if title := _good_title(candidate):
                titles[link] = title
                break

    results: list[dict[str, str]] = []
    seen: set[str] = set()

    def add(title: str | None, link: str | None, image: str | None) -> None:
        link = norm(link)
        if not link:
            return
        if link in seen:
            # stesso link già trovato (es. da JSON-LD senza foto): completa l'immagine
            for item in results:
                if item["url"] == link and not item["image"] and image:
                    item["image"] = urljoin(base_url, image)
            return
        title = titles.get(link) or _good_title(title)
        if not title:
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
    for data in _json_ld(soup):
        for node in _walk(data):
            types = _types(node)
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
        add(a.get("title") or img.get("alt"), a["href"], src)

    return results[:limit]


# ---------------------------------------------------------------------------
# Pagina della singola ricetta


def _duration(value: Any) -> str:
    """PT1H5M -> "1 h 5 min"."""
    if not isinstance(value, str):
        return ""
    m = re.fullmatch(r"P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:\d+S)?", value.strip())
    if not m:
        return ""
    days, hours, minutes = (int(x) if x else 0 for x in m.groups())
    hours += days * 24
    parts = ([f"{hours} h"] if hours else []) + ([f"{minutes} min"] if minutes else [])
    return " ".join(parts)


def _steps(value: Any) -> list[dict[str, Any]]:
    """recipeInstructions -> [{text, images}] o [{heading}] per le sezioni."""
    steps: list[dict[str, Any]] = []
    if isinstance(value, str):
        for line in re.split(r"\n+", htmllib.unescape(value)):
            if text := _clean(line):
                steps.append({"text": text, "images": []})
        return steps
    if isinstance(value, dict):
        value = [value]
    if not isinstance(value, list):
        return steps
    for item in value:
        if isinstance(item, str):
            if text := _clean(item):
                steps.append({"text": text, "images": []})
        elif isinstance(item, dict):
            if "HowToSection" in _types(item):
                if name := _clean(item.get("name")):
                    steps.append({"heading": name})
                steps.extend(_steps(item.get("itemListElement")))
            elif text := _clean(item.get("text") or item.get("name")):
                steps.append({"text": text, "images": _images_from(item.get("image"))})
    return steps


def _step_images_from_page(soup: BeautifulSoup, steps: list[dict[str, Any]], page_url: str) -> None:
    """Se i dati strutturati non hanno le foto dei passaggi, le cerca vicino al testo."""
    if any(s.get("images") for s in steps):
        return
    paragraphs = [
        (" ".join(p.get_text(" ").split()), p) for p in soup.find_all(["p", "li", "div"])
        if p.find(["p", "div"]) is None
    ]
    starts = [s["text"][:40] for s in steps if "text" in s]
    for step in steps:
        if "text" not in step:
            continue
        start = step["text"][:40]
        for text, node in paragraphs:
            if not text.startswith(start):
                continue
            # Parte dal paragrafo stesso e sale, ma mai fino a un blocco che
            # contiene anche altri passaggi (le foto sarebbero di tutti).
            container = node
            for _ in range(4):
                imgs = [i for img in container.find_all("img") if (i := _img_src(img))]
                if imgs:
                    step["images"] = [urljoin(page_url, i) for i in imgs[:4]]
                    break
                container = container.parent
                if container is None:
                    break
                block = " ".join(container.get_text(" ").split())
                if any(other != start and other in block for other in starts):
                    break
            break
    # Stesse foto su più passaggi: non sono foto dei passaggi
    seen: dict[tuple, int] = {}
    for step in steps:
        if step.get("images"):
            key = tuple(step["images"])
            seen[key] = seen.get(key, 0) + 1
    for step in steps:
        if step.get("images") and seen[tuple(step["images"])] > 1:
            step["images"] = []


def parse_recipe(html: str, page_url: str) -> dict[str, Any]:
    """Estrae titolo, descrizione, ingredienti e passaggi dalla pagina di una ricetta."""
    soup = BeautifulSoup(html, "html.parser")
    recipe: dict[str, Any] | None = None
    for data in _json_ld(soup):
        for node in _walk(data):
            if "Recipe" in _types(node):
                recipe = node
                break
        if recipe:
            break

    def meta(*names: str) -> str:
        for name in names:
            tag = soup.find("meta", property=name) or soup.find("meta", attrs={"name": name})
            if tag and tag.get("content"):
                return _clean(tag["content"])
        return ""

    result: dict[str, Any] = {
        "url": page_url,
        "title": meta("og:title") or _clean(soup.title.string if soup.title else ""),
        "description": meta("og:description", "description"),
        "image": meta("og:image"),
        "yield": "",
        "times": {},
        "ingredients": [],
        "steps": [],
    }
    if recipe is None:
        return result

    result["title"] = _strip_prefix(_clean(recipe.get("name")) or result["title"])
    result["description"] = _clean(recipe.get("description")) or result["description"]
    result["image"] = _image_from(recipe.get("image")) or result["image"]
    yld = recipe.get("recipeYield")
    if isinstance(yld, list):
        yld = next((y for y in yld if isinstance(y, str) and not y.isdigit()), yld[0] if yld else "")
    result["yield"] = _clean(str(yld)) if yld not in (None, "") else ""
    result["times"] = {
        key: d
        for key, src in (("prep", "prepTime"), ("cook", "cookTime"), ("total", "totalTime"))
        if (d := _duration(recipe.get(src)))
    }
    ingredients = recipe.get("recipeIngredient") or recipe.get("ingredients") or []
    if isinstance(ingredients, str):
        ingredients = [ingredients]
    result["ingredients"] = [t for i in ingredients if (t := _clean(i))]
    result["steps"] = _steps(recipe.get("recipeInstructions"))
    _step_images_from_page(soup, result["steps"], page_url)
    for step in result["steps"]:
        if "images" in step:
            step["images"] = [urljoin(page_url, i) for i in step["images"]]
    if result["image"]:
        result["image"] = urljoin(page_url, result["image"])
    return result


# ---------------------------------------------------------------------------


async def async_get_page(hass: HomeAssistant, url: str) -> tuple[str, str]:
    session = async_get_clientsession(hass)
    async with asyncio.timeout(20):
        async with session.get(url, headers=HEADERS) as resp:
            resp.raise_for_status()
            return await resp.text(), str(resp.url)


async def async_search_site(
    hass: HomeAssistant, site: dict[str, Any], query: str, limit: int
) -> list[dict[str, str]]:
    """Cerca su un singolo sito."""
    url = site["url"].replace("{query}", quote_plus(query))
    if _is_hellofresh(url):
        items = await _async_search_hellofresh(hass, url, query, limit)
    else:
        html, final_url = await async_get_page(hass, url)
        items = await hass.async_add_executor_job(parse_results, html, final_url, limit)
    for item in items:
        item["site"] = site["name"]
    return items


async def async_fetch_recipe(hass: HomeAssistant, url: str) -> dict[str, Any]:
    """Scarica e analizza la pagina di una ricetta."""
    if url in HF_ITEMS:
        return hellofresh_recipe(HF_ITEMS[url])
    html, final_url = await async_get_page(hass, url)
    return await hass.async_add_executor_job(parse_recipe, html, final_url)


# ---------------------------------------------------------------------------
# HelloFresh: i risultati arrivano via JavaScript da un'API che usa il token da
# ospite incluso nella pagina (lo stesso che usa il browser di ogni visitatore).

HF_IMG = "https://img.hellofresh.com/f_auto,fl_lossy,q_auto,w_800/hellofresh_s3"
HF_TOKEN_TTL = 50 * 60
HF_ITEMS_MAX = 300
HF_TOKENS: dict[str, tuple[str, float]] = {}
HF_ITEMS: dict[str, dict[str, Any]] = {}


def _is_hellofresh(url: str) -> bool:
    return site_domain(url).startswith("hellofresh.")


def _hf_image(path: str | None, link: str | None = None) -> str:
    if path:
        return HF_IMG + (path if path.startswith("/") else "/" + path)
    return link or ""


async def _async_hf_token(hass: HomeAssistant, page_url: str, refresh: bool = False) -> str:
    host = urlparse(page_url).netloc
    cached = HF_TOKENS.get(host)
    if cached and not refresh and cached[1] > time.monotonic():
        return cached[0]
    html, _ = await async_get_page(hass, page_url)
    match = re.search(r'"access_token":"([^"]+)"', html)
    if not match:
        raise ValueError("HelloFresh: token non trovato nella pagina")
    HF_TOKENS[host] = (match.group(1), time.monotonic() + HF_TOKEN_TTL)
    return match.group(1)


async def _async_search_hellofresh(
    hass: HomeAssistant, page_url: str, query: str, limit: int
) -> list[dict[str, str]]:
    host = urlparse(page_url).netloc
    tld = host.rsplit(".", 1)[-1].lower()
    params = {
        "country": tld.upper(),
        "locale": f"{tld}-{tld.upper()}",
        "q": query,
        "take": str(limit),
    }
    session = async_get_clientsession(hass)
    for attempt in range(2):
        token = await _async_hf_token(hass, page_url, refresh=attempt > 0)
        async with asyncio.timeout(20):
            async with session.get(
                f"https://{host}/gw/recipes/recipes/search",
                params=params,
                headers={**HEADERS, "Accept": "application/json", "Authorization": f"Bearer {token}"},
            ) as resp:
                if resp.status == 401 and attempt == 0:
                    continue  # token scaduto: ne prende uno nuovo
                resp.raise_for_status()
                data = await resp.json(content_type=None)
        break
    results: list[dict[str, str]] = []
    for item in data.get("items", []):
        link = item.get("websiteUrl")
        if not link or not item.get("name") or item.get("isAddon"):
            continue  # gli "addon" sono prodotti extra, non ricette
        if len(HF_ITEMS) >= HF_ITEMS_MAX:
            HF_ITEMS.pop(next(iter(HF_ITEMS)))
        HF_ITEMS[link] = item
        results.append(
            {
                "title": _clean(item["name"]),
                "url": link,
                "image": _hf_image(item.get("imagePath"), item.get("imageLink")),
            }
        )
    return results[:limit]


def hellofresh_recipe(item: dict[str, Any]) -> dict[str, Any]:
    """Ricetta completa dai dati dell'API (con le foto di ogni passaggio)."""
    names = {i.get("id"): _clean(i.get("name")) for i in item.get("ingredients", [])}
    yields = item.get("yields") or []
    portion = next((y for y in yields if y.get("yields") == 2), yields[0] if yields else {})
    ingredients: list[str] = []
    for ing in portion.get("ingredients", []):
        name = names.get(ing.get("id"))
        if not name:
            continue
        amount = ing.get("amount")
        unit = ing.get("unit") or ""
        qty = f"{amount:g} {unit}".strip() if isinstance(amount, (int, float)) else ""
        ingredients.append(f"{name} {qty}".strip())
    if not ingredients:
        ingredients = [n for n in names.values() if n]
    steps = []
    for step in sorted(item.get("steps", []), key=lambda s: int(s.get("index") or 0)):
        text = _clean(step.get("instructions"))
        if text:
            steps.append(
                {
                    "text": text,
                    "images": [
                        img
                        for i in step.get("images", [])
                        if (img := _hf_image(i.get("path"), i.get("link")))
                    ],
                }
            )
    title = _clean(item.get("name"))
    if headline := _clean(item.get("headline")):
        title = f"{title} {headline}"
    return {
        "url": item.get("websiteUrl", ""),
        "title": title,
        "description": _clean(item.get("description")),
        "image": _hf_image(item.get("imagePath"), item.get("imageLink")),
        "yield": f"{portion['yields']} persone" if portion.get("yields") else "",
        # prepTime di HelloFresh è il tempo totale mostrato sul sito
        "times": {"total": d}
        if (d := _duration(item.get("prepTime")) or _duration(item.get("totalTime")))
        else {},
        "ingredients": ingredients,
        "steps": steps,
    }
