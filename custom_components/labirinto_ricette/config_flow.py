"""Config flow e options flow."""

from __future__ import annotations

import voluptuous as vol

from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.helpers import config_validation as cv

from .const import CONF_SITES, CONF_SITES_VERSION, DEFAULT_SITES, DOMAIN, SITES_VERSION


class LabirintoRicetteConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    VERSION = 1

    async def async_step_user(self, user_input=None):
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()
        if user_input is not None:
            return self.async_create_entry(
                title="Labirinto Ricette",
                data={},
                options={CONF_SITES: DEFAULT_SITES, CONF_SITES_VERSION: SITES_VERSION},
            )
        return self.async_show_form(step_id="user")

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        return LabirintoRicetteOptionsFlow()


class LabirintoRicetteOptionsFlow(config_entries.OptionsFlow):
    async def async_step_init(self, user_input=None):
        errors: dict[str, str] = {}
        sites = [dict(s) for s in self.config_entry.options.get(CONF_SITES, DEFAULT_SITES)]
        names = [s["name"] for s in sites]

        if user_input is not None:
            remove = set(user_input.get("remove", []))
            enabled = set(user_input.get("enabled", []))
            new_sites = [
                {**s, "enabled": s["name"] in enabled}
                for s in sites
                if s["name"] not in remove
            ]
            name = user_input.get("new_name", "").strip()
            url = user_input.get("new_url", "").strip()
            if name or url:
                if not name or not url.startswith(("http://", "https://")) or "{query}" not in url:
                    errors["new_url"] = "invalid_site"
                elif name in names:
                    errors["new_name"] = "duplicate"
                else:
                    new_sites.append({"name": name, "url": url, "enabled": True})
            if not errors:
                return self.async_create_entry(
                    title="", data={**self.config_entry.options, CONF_SITES: new_sites}
                )

        schema = vol.Schema(
            {
                vol.Optional(
                    "enabled",
                    default=[s["name"] for s in sites if s.get("enabled", True)],
                ): cv.multi_select(names),
                vol.Optional("remove", default=[]): cv.multi_select(names),
                vol.Optional("new_name", default=""): str,
                vol.Optional("new_url", default=""): str,
            }
        )
        return self.async_show_form(
            step_id="init",
            data_schema=schema,
            errors=errors,
            description_placeholders={
                "query": "{query}",
                "example": "https://www.sito.it/cerca/?q={query}",
            },
        )
