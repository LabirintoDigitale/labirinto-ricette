"""Ingredienti nelle liste delle attività (entità todo.*)."""

from __future__ import annotations

from typing import Any

from homeassistant.const import ATTR_FRIENDLY_NAME, ATTR_SUPPORTED_FEATURES
from homeassistant.core import Context, HomeAssistant
from homeassistant.exceptions import ServiceValidationError

# TodoListEntityFeature.CREATE_TODO_ITEM / SET_DESCRIPTION_ON_ITEM
CREATE_ITEM = 1
SET_DESCRIPTION = 64
MAX_ITEMS = 50


def todo_lists(hass: HomeAssistant) -> list[dict[str, Any]]:
    """Liste a cui si possono aggiungere elementi."""
    lists = [
        {
            "entity_id": state.entity_id,
            "name": state.attributes.get(ATTR_FRIENDLY_NAME) or state.entity_id,
        }
        for state in hass.states.async_all("todo")
        if int(state.attributes.get(ATTR_SUPPORTED_FEATURES, 0)) & CREATE_ITEM
    ]
    return sorted(lists, key=lambda item: item["name"].casefold())


async def async_add_items(
    hass: HomeAssistant,
    entity_id: str,
    items: list[str],
    note: str = "",
    context: Context | None = None,
) -> int:
    """Aggiunge gli ingredienti alla lista, con il nome della ricetta come nota."""
    state = hass.states.get(entity_id)
    if (
        state is None
        or state.domain != "todo"
        or not int(state.attributes.get(ATTR_SUPPORTED_FEATURES, 0)) & CREATE_ITEM
    ):
        raise ServiceValidationError("Lista non trovata")
    items = [i.strip() for i in items if isinstance(i, str) and i.strip()][:MAX_ITEMS]
    if not items:
        raise ServiceValidationError("Nessun ingrediente da aggiungere")
    with_note = bool(note) and bool(
        int(state.attributes.get(ATTR_SUPPORTED_FEATURES, 0)) & SET_DESCRIPTION
    )
    for item in items:
        data: dict[str, Any] = {"item": item}
        if with_note:
            data["description"] = note
        await hass.services.async_call(
            "todo",
            "add_item",
            data,
            target={"entity_id": entity_id},
            blocking=True,
            context=context,
        )
    return len(items)
