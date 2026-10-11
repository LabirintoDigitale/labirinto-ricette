/* Labirinto Ricette - carta Lovelace */
class LabirintoRicetteCard extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._built = false;
  }

  setConfig(config) {
    this._config = { title: "Cosa cucino?", max_per_site: 6, ...config };
    this._built = false;
    if (this._hass) this._build();
  }

  set hass(hass) {
    this._hass = hass;
    if (!this._built && this._config) this._build();
  }

  getCardSize() {
    return 8;
  }

  static getStubConfig() {
    return {};
  }

  _build() {
    this._built = true;
    const root = this.shadowRoot;
    root.replaceChildren();

    const style = document.createElement("style");
    style.textContent = `
      ha-card { padding: 16px; }
      h2 { margin: 0 0 12px; font-size: 1.3em; font-weight: 500; }
      .bar { display: flex; gap: 8px; }
      input { flex: 1; padding: 10px; font-size: 1em; border-radius: 8px;
        border: 1px solid var(--divider-color); background: var(--card-background-color);
        color: var(--primary-text-color); }
      button { padding: 10px 16px; border: 0; border-radius: 8px; cursor: pointer;
        background: var(--primary-color); color: var(--text-primary-color, #fff); font-size: 1em; }
      button:disabled { opacity: .5; cursor: default; }
      .status { margin: 10px 0; color: var(--secondary-text-color); font-size: .9em; }
      .grid { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
      a.item { text-decoration: none; color: inherit; border-radius: 10px; overflow: hidden;
        background: var(--secondary-background-color); display: flex; flex-direction: column; }
      a.item img, .ph { width: 100%; aspect-ratio: 4/3; object-fit: cover; background: var(--divider-color); }
      .info { padding: 8px; }
      .t { font-weight: 500; font-size: .92em; line-height: 1.25;
        display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
      .s { margin-top: 4px; font-size: .75em; color: var(--secondary-text-color); }
    `;

    const card = document.createElement("ha-card");
    const h = document.createElement("h2");
    h.textContent = this._config.title;
    const bar = document.createElement("div");
    bar.className = "bar";
    this._input = document.createElement("input");
    this._input.placeholder = "Ingredienti: pasta, zucchine, uova";
    this._input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") this._search();
    });
    this._btn = document.createElement("button");
    this._btn.textContent = "Cerca";
    this._btn.addEventListener("click", () => this._search());
    bar.append(this._input, this._btn);
    this._status = document.createElement("div");
    this._status.className = "status";
    this._grid = document.createElement("div");
    this._grid.className = "grid";
    card.append(h, bar, this._status, this._grid);
    root.append(style, card);
  }

  async _search() {
    const q = this._input.value.trim();
    if (!q) return;
    this._btn.disabled = true;
    this._status.textContent = "Cerco ricette…";
    this._grid.replaceChildren();
    try {
      const res = await this._hass.callWS({
        type: "call_service",
        domain: "labirinto_ricette",
        service: "search",
        service_data: { ingredients: q, max_per_site: this._config.max_per_site },
        return_response: true,
      });
      const r = res.response || {};
      const items = r.results || [];
      let msg = items.length ? `${items.length} ricette trovate` : "Nessuna ricetta trovata";
      const failed = Object.keys(r.errors || {});
      if (failed.length) msg += ` · non hanno risposto: ${failed.join(", ")}`;
      this._status.textContent = msg;
      for (const it of items) this._grid.append(this._item(it));
    } catch (err) {
      this._status.textContent = "Errore: " + (err.message || err);
    } finally {
      this._btn.disabled = false;
    }
  }

  _item(it) {
    const a = document.createElement("a");
    a.className = "item";
    a.href = it.url;
    a.target = "_blank";
    a.rel = "noopener noreferrer";
    if (it.image) {
      const img = document.createElement("img");
      img.src = it.image;
      img.loading = "lazy";
      img.referrerPolicy = "no-referrer";
      img.alt = it.title;
      a.append(img);
    } else {
      const ph = document.createElement("div");
      ph.className = "ph";
      a.append(ph);
    }
    const info = document.createElement("div");
    info.className = "info";
    const t = document.createElement("div");
    t.className = "t";
    t.textContent = it.title;
    const s = document.createElement("div");
    s.className = "s";
    s.textContent = it.site;
    info.append(t, s);
    a.append(info);
    return a;
  }
}

customElements.define("labirinto-ricette-card", LabirintoRicetteCard);
window.customCards = window.customCards || [];
window.customCards.push({
  type: "labirinto-ricette-card",
  name: "Labirinto Ricette",
  description: "Cerca ricette dagli ingredienti sui tuoi siti preferiti",
});
