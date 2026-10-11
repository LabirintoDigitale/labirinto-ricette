"""Costanti di Labirinto Ricette."""

DOMAIN = "labirinto_ricette"
CONF_SITES = "sites"
SIGNAL_UPDATE = f"{DOMAIN}_update"

CARD_URL = f"/{DOMAIN}/labirinto-ricette-card.js"

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
        "enabled": True,
    },
    {
        "name": "Cookist",
        "url": "https://www.cookist.it/?s={query}",
        "enabled": True,
    },
    {
        "name": "La Cucina Italiana",
        "url": "https://www.lacucinaitaliana.it/ricerca/?q={query}",
        "enabled": True,
    },
    {
        "name": "Cucchiaio d'Argento",
        "url": "https://www.cucchiaio.it/ricerca/?q={query}",
        "enabled": False,
    },
]
