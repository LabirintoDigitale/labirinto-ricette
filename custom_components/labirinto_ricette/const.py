"""Costanti di Labirinto Ricette."""

DOMAIN = "labirinto_ricette"
CONF_SITES = "sites"
CONF_SHARE_TOKEN = "share_token"
SIGNAL_UPDATE = f"{DOMAIN}_update"

CARD_URL = f"/{DOMAIN}/labirinto-ricette-card.js"
PANEL_URL = f"/{DOMAIN}/labirinto-ricette-panel.js"
PANEL_PATH = "labirinto-ricette"
# Ricettario senza login: {SHARE_URL}/<token>
SHARE_URL = f"/api/{DOMAIN}/app"

# {query} viene sostituito con gli ingredienti separati da "+"
DEFAULT_SITES = [
    {
        "name": "Giallo Zafferano",
        "url": "https://www.giallozafferano.it/ricerca-ricette/{query}/",
        "enabled": True,
    },
    {
        "name": "HelloFresh",
        "url": "https://www.hellofresh.it/recipes/search/?q={query}",
        # risultati letti dall'API di HelloFresh (vedi scraper.py)
        "enabled": True,
    },
    {
        "name": "Cookist",
        "url": "https://www.cookist.it/?s={query}",
        "enabled": True,
    },
    {
        "name": "La Cucina Italiana",
        "url": "https://www.lacucinaitaliana.it/search/?q={query}",
        "enabled": True,
    },
    {
        "name": "Cucchiaio d'Argento",
        "url": "https://www.cucchiaio.it/ricerca/?q={query}",
        "enabled": False,
    },
]

# Siti predefiniti aggiunti nelle versioni successive: vengono proposti una volta
# anche alle installazioni esistenti (CONF_SITES_VERSION tiene il conto)
CONF_SITES_VERSION = "sites_version"
SITES_VERSION = 2
NEW_SITES = {
    2: [
        {
            "name": "Nutribees",
            "url": "https://www.nutribees.com/shop-piatti?q={query}",
            "enabled": True,
        }
    ],
}
DEFAULT_SITES = DEFAULT_SITES + NEW_SITES[2]

# URL predefiniti cambiati nel tempo: vengono aggiornati nelle installazioni esistenti
OLD_SITE_URLS = {
    "https://www.lacucinaitaliana.it/ricerca/?q={query}": "https://www.lacucinaitaliana.it/search/?q={query}",
}
