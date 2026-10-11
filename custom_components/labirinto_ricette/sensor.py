"""Sensore con l'ultima ricerca."""

from __future__ import annotations

from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, SIGNAL_UPDATE


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    async_add_entities([LastSearchSensor(entry)])


class LastSearchSensor(SensorEntity):
    _attr_should_poll = False
    _attr_icon = "mdi:chef-hat"
    _attr_name = "Labirinto Ricette risultati"
    _attr_native_unit_of_measurement = "ricette"
    _unrecorded_attributes = frozenset({"results", "errors"})

    def __init__(self, entry: ConfigEntry) -> None:
        self._attr_unique_id = f"{entry.entry_id}_last_search"
        self._refresh()

    def _refresh(self) -> None:
        last = self.hass.data[DOMAIN].get("last") if self.hass else None
        last = last or {}
        self._attr_native_value = len(last.get("results", []))
        self._attr_extra_state_attributes = {
            "query": last.get("query", ""),
            "results": last.get("results", []),
            "errors": last.get("errors", {}),
        }

    async def async_added_to_hass(self) -> None:
        self._refresh()

        @callback
        def _update() -> None:
            self._refresh()
            self.async_write_ha_state()

        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_UPDATE, _update))
