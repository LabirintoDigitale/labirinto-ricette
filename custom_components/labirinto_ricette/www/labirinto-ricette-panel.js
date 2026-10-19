/* Labirinto Ricette - pannello laterale */
const DOMAIN = "labirinto_ricette";

const STYLE = `
  :host { display: block; min-height: 100vh; background: var(--primary-background-color);
    color: var(--primary-text-color); font-family: var(--paper-font-body1_-_font-family, inherit); }
  .toolbar { display: flex; align-items: center; gap: 4px; height: 56px; padding: 0 12px;
    background: var(--app-header-background-color, var(--primary-color));
    color: var(--app-header-text-color, var(--text-primary-color, #fff));
    border-bottom: var(--app-header-border-bottom, none); position: sticky; top: 0; z-index: 2; }
  .toolbar .title { flex: 1; font-size: 20px; font-weight: 400; margin-left: 8px; }
  .toolbar button.icon { background: none; border: 0; color: inherit; cursor: pointer;
    display: flex; align-items: center; gap: 6px; padding: 8px 10px; border-radius: 20px; font-size: 14px; }
  .toolbar button.icon:hover { background: rgba(255,255,255,.12); }
  main { max-width: 900px; margin: 0 auto; padding: 16px; box-sizing: border-box; }
  .box { background: var(--card-background-color); border-radius: var(--ha-card-border-radius, 12px);
    box-shadow: var(--ha-card-box-shadow, none); border: 1px solid var(--divider-color); padding: 16px; margin-bottom: 16px; }
  h2 { margin: 0 0 12px; font-size: 18px; font-weight: 500; }
  .hint { color: var(--secondary-text-color); font-size: 13px; margin: 6px 0 0; }
  .bar { display: flex; gap: 8px; }
  input[type=text], input[type=search] { flex: 1; min-width: 0; padding: 12px; font-size: 16px; border-radius: 8px;
    border: 1px solid var(--divider-color); background: var(--primary-background-color);
    color: var(--primary-text-color); box-sizing: border-box; }
  input[type=text]:focus, input[type=search]:focus { outline: 2px solid var(--primary-color); outline-offset: -1px; }
  button.btn { padding: 10px 16px; border: 0; border-radius: 8px; cursor: pointer; font-size: 15px;
    background: var(--primary-color); color: var(--text-primary-color, #fff); white-space: nowrap; }
  button.btn.flat { background: none; color: var(--primary-color); border: 1px solid var(--divider-color); }
  button.btn.danger { background: none; color: var(--error-color, #db4437); border: 1px solid var(--divider-color); }
  button.btn:disabled { opacity: .5; cursor: default; }
  .chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
  .chip { display: inline-flex; align-items: center; gap: 4px; padding: 4px 6px 4px 12px; border-radius: 16px;
    background: var(--secondary-background-color); font-size: 14px; }
  .chip button { background: none; border: 0; cursor: pointer; color: var(--secondary-text-color);
    font-size: 16px; line-height: 1; padding: 2px 6px; }
  .status { margin: 4px 0 12px; color: var(--secondary-text-color); font-size: 14px; }
  .status .err { color: var(--error-color, #db4437); }
  .list { display: flex; flex-direction: column; gap: 10px; }
  a.item { display: flex; gap: 12px; text-decoration: none; color: inherit; padding: 8px;
    background: var(--card-background-color); border: 1px solid var(--divider-color); border-radius: 12px; }
  a.item:hover { border-color: var(--primary-color); }
  a.item img, a.item .ph { flex: none; width: 120px; height: 90px; object-fit: cover; border-radius: 8px;
    background: var(--secondary-background-color); }
  .info { min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 6px; }
  .t { font-size: 16px; font-weight: 500; line-height: 1.3; display: -webkit-box; -webkit-line-clamp: 3;
    -webkit-box-orient: vertical; overflow: hidden; }
  .s { font-size: 13px; color: var(--secondary-text-color); }
  .site { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--divider-color); }
  .site:last-child { border-bottom: 0; }
  .site .info { flex: 1; }
  .site .u { font-size: 12px; color: var(--secondary-text-color); word-break: break-all; }
  .site .actions { display: flex; gap: 6px; flex: none; }
  .site.off .t { color: var(--secondary-text-color); }
  input[type=checkbox] { width: 20px; height: 20px; accent-color: var(--primary-color); flex: none; }
  .form { display: flex; flex-direction: column; gap: 8px; }
  .row { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; margin-top: 12px; }
  .msg { font-size: 14px; margin-top: 8px; }
  .msg.ok { color: var(--success-color, #43a047); }
  .msg.err { color: var(--error-color, #db4437); }
  .empty { text-align: center; padding: 32px 16px; color: var(--secondary-text-color); }
  .empty ha-icon { --mdc-icon-size: 48px; display: block; margin-bottom: 8px; }
  .tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
  .tab { display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 20px; cursor: pointer;
    border: 1px solid var(--divider-color); background: var(--card-background-color); color: var(--primary-text-color);
    font-size: 14px; }
  .tab ha-icon { --mdc-icon-size: 18px; color: var(--secondary-text-color); }
  .tab.on { background: var(--primary-color); border-color: var(--primary-color); color: var(--text-primary-color, #fff); }
  .tab.on ha-icon { color: inherit; }
  .heart.on { color: #e53935; }
  .picker { margin-top: 8px; padding: 12px; border-radius: 12px; background: var(--secondary-background-color); }
  .row.left { justify-content: flex-start; margin-top: 8px; }
  button.btn ha-icon { --mdc-icon-size: 18px; vertical-align: -3px; }
  .heart.busy { animation: pulse 1s ease-in-out infinite; }
  @keyframes pulse { 50% { opacity: .35; } }
  .nmeta { font-size: 12px; color: var(--secondary-text-color); }
  .nmeta .topic { font-weight: 700; text-transform: uppercase; letter-spacing: .5px; color: var(--primary-color); }
  .s.nd { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
  .nbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
  .dlg-foot { display: flex; justify-content: flex-end; padding: 12px 16px; border-top: 1px solid var(--divider-color); }
  .dlg-foot a.btn { text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }
  ul.sum { margin: 12px 0 0; padding-left: 20px; font-size: 15px; line-height: 1.55; }
  ul.sum li { margin-bottom: 10px; }
  ul.sum.orig { font-style: italic; font-size: 14px; color: var(--secondary-text-color); }
  .orig-title { margin-top: 16px; padding-top: 10px; border-top: 1px dashed var(--divider-color); font-size: 12px;
    font-weight: 700; text-transform: uppercase; letter-spacing: .6px; color: var(--secondary-text-color); }
  button.btn.small { padding: 2px 8px; font-size: 12px; }
  .overlay { position: fixed; inset: 0; z-index: 10; background: rgba(0,0,0,.5);
    display: flex; align-items: center; justify-content: center; padding: 24px; box-sizing: border-box; }
  .dlg { background: var(--card-background-color); color: var(--primary-text-color); border-radius: 16px;
    width: 100%; max-width: 760px; max-height: 100%; display: flex; flex-direction: column; overflow: hidden;
    box-shadow: 0 10px 40px rgba(0,0,0,.35); }
  .dlg-head { display: flex; align-items: center; gap: 8px; padding: 12px 8px 12px 20px;
    border-bottom: 1px solid var(--divider-color); }
  .dlg-title { flex: 1; font-size: 20px; font-weight: 500; line-height: 1.25; }
  .x { background: none; border: 0; cursor: pointer; color: var(--secondary-text-color); padding: 8px;
    border-radius: 50%; display: flex; }
  .x:hover { background: var(--secondary-background-color); }
  .dlg-body { flex: 1 1 auto; overflow-y: auto; padding: 0 20px 20px; }
  .loading { padding: 48px 0; text-align: center; color: var(--secondary-text-color); }
  .hero { display: block; width: calc(100% + 40px); margin: 0 -20px; max-height: 360px; object-fit: cover; }
  .desc { font-size: 15px; line-height: 1.5; margin: 16px 0 8px; white-space: pre-line; }
  .meta { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0; }
  .meta .chip { padding: 4px 12px; gap: 6px; }
  .meta ha-icon { --mdc-icon-size: 18px; color: var(--secondary-text-color); }
  h3 { font-size: 17px; font-weight: 500; margin: 20px 0 8px; }
  ul.ingr { list-style: none; padding: 0; margin: 0; columns: 2 240px; column-gap: 24px; }
  ul.ingr li { break-inside: avoid; padding: 5px 0; font-size: 15px; }
  ul.ingr label { display: flex; gap: 10px; align-items: flex-start; cursor: pointer; }
  ul.ingr input { margin-top: 1px; }
  ul.ingr li.have span { font-weight: 600; }
  ul.ingr li.done span { text-decoration: line-through; color: var(--secondary-text-color); }
  ol.steps { padding-left: 22px; margin: 0; }
  ol.steps li { margin-bottom: 16px; font-size: 15px; line-height: 1.55; }
  ol.steps li::marker { font-weight: 600; color: var(--primary-color); }
  ol.steps li.sec { list-style: none; margin-left: -22px; font-weight: 600; font-size: 16px; }
  ol.steps p { margin: 0 0 8px; }
  .pics { display: grid; gap: 6px; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); }
  .pics img { width: 100%; aspect-ratio: 4/3; object-fit: cover; border-radius: 8px;
    background: var(--secondary-background-color); }
  a.btn.link { text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }
  a.btn.link ha-icon { --mdc-icon-size: 18px; }
  @media (max-width: 600px) {
    .overlay { padding: 0; }
    .dlg { border-radius: 0; height: 100%; max-height: none; }
    .dlg-body { padding: 0 16px 16px; }
    .hero { width: calc(100% + 32px); margin: 0 -16px; }
    ul.ingr { columns: 1; }
    main { padding: 12px; }
    a.item img, a.item .ph { width: 96px; height: 72px; }
    .site { flex-wrap: wrap; }
    .site .actions { width: 100%; justify-content: flex-end; }
  }
`;

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") node.className = v;
    else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else node[k] = v;
  }
  for (const c of children) if (c != null) node.append(c);
  return node;
}

class LabirintoRicettePanel extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this._view = "search";
    this._ingredients = [];
    this._results = null;
    this._errors = {};
    this._sites = [];
    this._defaults = [];
    this._loaded = false;
  }

  set hass(hass) {
    const first = !this._hass;
    this._hass = hass;
    if (this._menu) this._menu.hass = hass;
    if (first) {
      this._render();
      this._load();
    }
  }

  set narrow(narrow) {
    this._narrow = narrow;
    if (this._menu) this._menu.narrow = narrow;
  }

  set panel(panel) {}

  get _isAdmin() {
    return !!(this._hass && this._hass.user && this._hass.user.is_admin);
  }

  async _load() {
    try {
      const cfg = await this._hass.callWS({ type: `${DOMAIN}/config` });
      this._sites = cfg.sites || [];
      this._defaults = cfg.defaults || [];
      this._sharePath = cfg.share_path || null;
      if (cfg.last && !this._results) {
        this._ingredients = cfg.last.ingredients || [];
        this._results = cfg.last.results || [];
        this._errors = cfg.last.errors || {};
      }
      this._loaded = true;
    } catch (err) {
      this._loadError = err.message || String(err);
    }
    await this._loadFavorites();
    this._render();
  }

  async _loadFavorites() {
    try {
      const r = await this._hass.callWS({ type: `${DOMAIN}/favorites/list` });
      this._favs = r.favorites || [];
    } catch (_e) {
      this._favs = this._favs || [];
    }
  }

  _favFor(url) {
    return (this._favs || []).find((f) => f.url === url);
  }

  _render() {
    const root = this.shadowRoot;
    root.replaceChildren();
    this._menu = document.createElement("ha-menu-button");
    this._menu.hass = this._hass;
    this._menu.narrow = this._narrow;

    const toolbar = el("div", { class: "toolbar" }, this._menu,
      el("div", { class: "title", textContent: this._view === "settings" ? "Siti di ricette" : "Ricette" }));
    if (this._view !== "settings") {
      if (this._isAdmin) {
        toolbar.append(el("button", { class: "icon", title: "Configura i siti", onclick: () => this._openSettings() },
          el("ha-icon", { icon: "mdi:cog" }), el("span", { textContent: "Siti" })));
      }
    } else {
      toolbar.append(el("button", { class: "icon", title: "Torna alla ricerca", onclick: () => this._closeSettings() },
        el("ha-icon", { icon: "mdi:close" })));
    }

    const main = el("main");
    if (this._loadError) {
      main.append(el("div", { class: "box" }, el("div", { class: "msg err", textContent: "Errore: " + this._loadError })));
    } else if (this._view === "search" || this._view === "favorites" || this._view === "news") {
      const n = (this._favs || []).length;
      const tab = (view, icon, text) => el("button", {
        class: "tab" + (this._view === view ? " on" : ""),
        onclick: () => { this._view = view; this._render(); },
      }, el("ha-icon", { icon }), text);
      main.append(el("div", { class: "tabs" },
        tab("search", "mdi:fridge-outline", "Ingredienti"),
        tab("favorites", "mdi:heart", n ? `I miei preferiti (${n})` : "I miei preferiti"),
        tab("news", "mdi:newspaper-variant-outline", "News")));
      if (this._view === "search") this._renderSearch(main);
      else if (this._view === "favorites") this._renderFavorites(main);
      else this._renderNews(main);
    } else {
      this._renderSettings(main);
    }
    root.append(el("style", { textContent: STYLE }), toolbar, main);
    if (this._overlay) root.append(this._overlay);
  }

  /* ---------- Ricerca ---------- */

  _renderSearch(main) {
    const input = el("input", {
      type: "text",
      placeholder: "Cosa hai in frigo? es. zucchine, uova, parmigiano",
      enterKeyHint: "search",
    });
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        if (input.value.trim()) this._addIngredients(input.value);
        else this._search();
      }
    });
    const addBtn = el("button", { class: "btn flat", textContent: "Aggiungi", onclick: () => this._addIngredients(input.value) });
    this._searchBtn = el("button", {
      class: "btn",
      textContent: "Cerca ricette",
      onclick: () => {
        if (input.value.trim()) this._addIngredients(input.value, false);
        this._search();
      },
    });
    this._input = input;

    const chips = el("div", { class: "chips" });
    for (const ing of this._ingredients) {
      chips.append(el("span", { class: "chip" }, ing,
        el("button", { title: "Rimuovi", textContent: "×", onclick: () => this._removeIngredient(ing) })));
    }
    if (this._ingredients.length) {
      chips.append(el("button", { class: "btn flat", textContent: "Svuota", onclick: () => {
        this._ingredients = [];
        this._render();
        this._focusInput();
      } }));
    }

    const enabled = this._sites.filter((s) => s.enabled !== false).map((s) => s.name);
    main.append(el("div", { class: "box" },
      el("h2", { textContent: "Cosa hai in frigo?" }),
      el("div", { class: "bar" }, input, addBtn),
      chips,
      el("div", { class: "row" }, this._searchBtn),
      el("p", { class: "hint", textContent: this._loaded
        ? (enabled.length ? "Cerco su: " + enabled.join(", ") : "Nessun sito attivo: aggiungine uno da ⚙ Siti.")
        : "" }),
    ));

    this._status = el("div", { class: "status" });
    this._list = el("div", { class: "list" });
    main.append(this._status, this._list);
    this._renderResults();
  }

  _focusInput() {
    if (this._input) this._input.focus();
  }

  _addIngredients(text, rerender = true) {
    const parts = text.split(/[,;\n]+/).map((s) => s.trim()).filter(Boolean);
    for (const p of parts) {
      if (!this._ingredients.some((i) => i.toLowerCase() === p.toLowerCase())) this._ingredients.push(p);
    }
    if (rerender) {
      this._render();
      this._focusInput();
    }
  }

  _removeIngredient(ing) {
    this._ingredients = this._ingredients.filter((i) => i !== ing);
    this._render();
  }

  async _search() {
    if (!this._ingredients.length) {
      this._status.textContent = "Scrivi almeno un ingrediente.";
      this._focusInput();
      return;
    }
    this._searching = true;
    this._render();
    try {
      const res = await this._hass.callWS({
        type: "call_service",
        domain: DOMAIN,
        service: "search",
        service_data: { ingredients: this._ingredients, max_per_site: 8 },
        return_response: true,
      });
      const r = res.response || {};
      this._results = r.results || [];
      this._errors = r.errors || {};
      this._searchError = null;
    } catch (err) {
      this._searchError = err.message || String(err);
    }
    this._searching = false;
    this._render();
  }

  _renderResults() {
    if (this._searchBtn) this._searchBtn.disabled = !!this._searching;
    if (this._searching) {
      this._status.textContent = "Cerco ricette…";
      return;
    }
    if (this._searchError) {
      this._status.append(el("span", { class: "err", textContent: "Errore: " + this._searchError }));
      return;
    }
    if (this._results == null) {
      this._list.append(el("div", { class: "empty" }, el("ha-icon", { icon: "mdi:fridge-outline" }),
        "Aggiungi gli ingredienti che hai in casa e premi “Cerca ricette”."));
      return;
    }
    const items = this._results;
    this._status.textContent = items.length ? `${items.length} ricette trovate` : "Nessuna ricetta trovata.";
    const failed = Object.keys(this._errors || {});
    if (failed.length) {
      this._status.append(" · ", el("span", { class: "err", textContent: "non hanno risposto: " + failed.join(", ") }));
    }
    for (const it of items) this._list.append(this._item(it));
  }

  _renderFavorites(main) {
    const favs = this._favs || [];
    if (!favs.length) {
      main.append(el("div", { class: "empty" }, el("ha-icon", { icon: "mdi:heart-outline" }),
        "Nessun preferito. Apri una ricetta e tocca il ♥ in alto per salvarla qui: testo e foto restano salvati anche se il sito la toglie."));
      return;
    }
    const list = el("div", { class: "list" });
    const count = el("div", { class: "status" });
    const input = el("input", {
      type: "search",
      placeholder: "Cerca nei preferiti per nome…",
      value: this._favFilter || "",
      enterKeyHint: "search",
    });
    // Filtra senza ridisegnare la pagina, così il campo non perde il focus
    const fill = () => {
      this._favFilter = input.value;
      const words = input.value.toLowerCase().split(/\s+/).filter(Boolean);
      const shown = favs.filter((f) => {
        const text = `${f.title} ${f.site}`.toLowerCase();
        return words.every((w) => text.includes(w));
      });
      list.replaceChildren(...shown.map((f) => this._item(f, f.id)));
      count.textContent = words.length
        ? (shown.length ? `${shown.length} di ${favs.length} preferiti` : "Nessun preferito con questo nome.")
        : "";
    };
    input.addEventListener("input", fill);
    main.append(el("div", { class: "box" }, el("div", { class: "bar" }, input)), count, list);
    fill();
  }

  _item(it, favId = null) {
    const a = el("a", { class: "item", href: it.url, target: "_blank", rel: "noopener noreferrer" });
    a.addEventListener("click", (e) => {
      // Ctrl/Cmd/clic centrale aprono comunque il sito
      if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
      e.preventDefault();
      this._openRecipe(it, favId);
    });
    if (it.image) {
      a.append(el("img", { src: it.image, loading: "lazy", referrerPolicy: "no-referrer", alt: "" }));
    } else {
      a.append(el("div", { class: "ph" }));
    }
    a.append(el("div", { class: "info" },
      el("div", { class: "t", textContent: it.title }),
      el("div", { class: "s", textContent: it.site })));
    return a;
  }

  /* ---------- Finestra della ricetta ---------- */

  async _openRecipe(it, favId = null) {
    this._closeRecipe();
    const body = el("div", { class: "dlg-body" }, el("div", { class: "loading", textContent: "Carico la ricetta…" }));
    const close = el("button", { class: "x", title: "Chiudi", onclick: () => this._closeRecipe() }, el("ha-icon", { icon: "mdi:close" }));
    const dialog = el("div", { class: "dlg", role: "dialog", ariaModal: "true", ariaLabel: it.title },
      el("div", { class: "dlg-head" }, el("div", { class: "dlg-title", textContent: it.title }), this._heart(it), close),
      body);
    const overlay = el("div", { class: "overlay", onclick: (e) => { if (e.target === overlay) this._closeRecipe(); } }, dialog);
    this._onKey = (e) => { if (e.key === "Escape") this._closeRecipe(); };
    window.addEventListener("keydown", this._onKey);
    this._overlay = overlay;
    this.shadowRoot.append(overlay);
    close.focus();

    let recipe;
    try {
      // Dai preferiti: la copia salvata (funziona anche se il sito non c'è più)
      const fav = favId || (this._favFor(it.url) || {}).id;
      if (fav) {
        const saved = await this._hass.callWS({ type: `${DOMAIN}/favorites/get`, fav_id: fav });
        recipe = { ...saved.recipe, savedAt: saved.saved_at };
      } else {
        recipe = await this._hass.callWS({ type: `${DOMAIN}/recipe`, url: it.url });
      }
    } catch (err) {
      if (this._overlay !== overlay) return;
      body.replaceChildren(
        el("div", { class: "msg err", textContent: "Non riesco a leggere la ricetta: " + (err.message || err) }),
        this._siteLink(it.url));
      return;
    }
    if (this._overlay !== overlay) return; // chiusa nel frattempo
    body.replaceChildren(...this._recipeContent(recipe, it));
    body.scrollTop = 0;
  }

  _heart(it) {
    const btn = el("button", { class: "x heart" });
    const paint = () => {
      const saved = !!this._favFor(it.url);
      btn.replaceChildren(el("ha-icon", { icon: saved ? "mdi:heart" : "mdi:heart-outline" }));
      btn.classList.toggle("on", saved);
      btn.title = saved ? "Togli dai preferiti" : "Salva nei preferiti";
    };
    paint();
    btn.addEventListener("click", async () => {
      const fav = this._favFor(it.url);
      if (fav && !confirm("Togliere la ricetta dai preferiti? La copia salvata (testo e foto) verrà cancellata.")) return;
      btn.disabled = true;
      btn.classList.add("busy");
      btn.title = fav ? "Tolgo…" : "Salvo testo e foto…";
      try {
        if (fav) {
          await this._hass.callWS({ type: `${DOMAIN}/favorites/remove`, fav_id: fav.id });
        } else {
          await this._hass.callWS({
            type: `${DOMAIN}/favorites/add`, url: it.url, site: it.site || "", title: it.title || "", image: it.image || "",
          });
        }
        await this._loadFavorites();
      } catch (err) {
        alert("Non riesco a salvare: " + (err.message || err));
      }
      btn.disabled = false;
      btn.classList.remove("busy");
      paint();
      this._render(); // aggiorna contatore ed elenco preferiti sotto la finestra
    });
    return btn;
  }

  async _todoPicker(box, items, recipeTitle) {
    box.replaceChildren(el("div", { class: "msg", textContent: "Carico le liste…" }));
    let lists;
    try {
      lists = (await this._hass.callWS({ type: `${DOMAIN}/todo/lists` })).lists;
    } catch (err) {
      box.replaceChildren(el("div", { class: "msg err", textContent: "Errore: " + (err.message || err) }));
      return;
    }
    if (!lists.length) {
      box.replaceChildren(el("div", { class: "msg err", textContent:
        "Non ci sono liste delle attività in Home Assistant a cui aggiungere elementi." }));
      return;
    }
    let last = null;
    try { last = localStorage.getItem("labirinto-ricette-todo"); } catch (_e) { /* niente storage */ }
    const msg = el("div", { class: "msg" });
    const buttons = lists.map((l) => el("button", {
      class: "btn" + (l.entity_id === last ? "" : " flat"),
      onclick: async () => {
        buttons.forEach((b) => (b.disabled = true));
        msg.className = "msg";
        msg.textContent = "Aggiungo…";
        try {
          const r = await this._hass.callWS({
            type: `${DOMAIN}/todo/add`, entity_id: l.entity_id, items, note: recipeTitle ? `Per: ${recipeTitle}` : "",
          });
          try { localStorage.setItem("labirinto-ricette-todo", l.entity_id); } catch (_e) { /* niente storage */ }
          box.replaceChildren(el("div", { class: "msg ok", textContent:
            `${r.added === 1 ? "Aggiunto 1 ingrediente" : `Aggiunti ${r.added} ingredienti`} a “${l.name}”.` }));
        } catch (err) {
          msg.className = "msg err";
          msg.textContent = "Non aggiunto: " + (err.message || err);
          buttons.forEach((b) => (b.disabled = false));
        }
      },
    }, l.name));
    box.replaceChildren(el("div", { class: "picker" },
      el("div", { class: "hint", textContent: `In quale lista metto ${items.length === 1 ? "1 ingrediente" : `${items.length} ingredienti`}?` }),
      el("div", { class: "row left" }, ...buttons,
        el("button", { class: "btn flat", textContent: "Annulla", onclick: () => box.replaceChildren() })),
      msg));
  }

  /* ---------- News (sola lettura, dalla card RSS News) ---------- */

  async _loadNews() {
    this._newsLoading = true;
    try {
      const r = await this._hass.callWS({ type: `${DOMAIN}/news/list` });
      this._news = r.articles || [];
      this._newsError = null;
    } catch (err) {
      this._newsError = err.message || String(err);
    }
    this._newsLoading = false;
    this._newsAt = Date.now();
    if (this._view === "news") this._render();
  }

  _newsDate(value) {
    const d = new Date(value);
    if (isNaN(d.getTime())) return value || "";
    return d.toLocaleString("it-IT", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
  }

  _newsMeta(a) {
    const parts = [];
    if (a.topic) parts.push(el("span", { class: "topic", textContent: a.topic }));
    if (a.source && a.source.toLowerCase() !== (a.topic || "").toLowerCase()) parts.push(el("span", { textContent: a.source }));
    if (a.pubDate) parts.push(el("span", { textContent: this._newsDate(a.pubDate) }));
    const meta = el("div", { class: "nmeta" });
    parts.forEach((p, i) => { if (i) meta.append(" · "); meta.append(p); });
    return meta;
  }

  _renderNews(main) {
    // Ricarica se mai caricate o se sono passati più di 2 minuti
    if (!this._newsLoading && (!this._newsAt || Date.now() - this._newsAt > 120000)) this._loadNews();
    const refresh = el("button", { class: "btn flat", onclick: () => { this._news = null; this._loadNews(); this._render(); } },
      el("ha-icon", { icon: "mdi:refresh" }), " Aggiorna");
    if (this._newsError) {
      main.append(el("div", { class: "box" },
        el("div", { class: "msg err", textContent: "Notizie non disponibili: " + this._newsError }),
        el("div", { class: "row" }, refresh)));
      return;
    }
    if (!this._news) {
      main.append(el("div", { class: "empty", textContent: "Carico le notizie…" }));
      return;
    }
    if (!this._news.length) {
      main.append(el("div", { class: "empty", textContent: "Nessuna notizia." }));
      return;
    }
    main.append(el("div", { class: "status nbar" },
      el("span", { textContent: `${this._news.length} notizie` }), refresh));
    const list = el("div", { class: "list" });
    for (const a of this._news) {
      const item = el("a", { class: "item news", href: a.link, target: "_blank", rel: "noopener noreferrer" });
      item.addEventListener("click", (e) => {
        if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return;
        e.preventDefault();
        this._openNews(a);
      });
      if (a.image) {
        const img = el("img", { src: a.image, loading: "lazy", referrerPolicy: "no-referrer", alt: "" });
        img.addEventListener("error", () => img.replaceWith(el("div", { class: "ph" })));
        item.append(img);
      } else {
        item.append(el("div", { class: "ph" }));
      }
      item.append(el("div", { class: "info" },
        this._newsMeta(a),
        el("div", { class: "t", textContent: a.title }),
        a.description ? el("div", { class: "s nd", textContent: a.description }) : null));
      list.append(item);
    }
    main.append(list);
  }

  _langName(code) {
    if (!code || code === "und" || code === "other") return "";
    try {
      const n = new Intl.DisplayNames(["it"], { type: "language" }).of(code);
      if (n && n !== code) return n;
    } catch (_e) { /* codice sconosciuto */ }
    return code.toUpperCase();
  }

  _openNews(a) {
    this._closeRecipe();
    const body = el("div", { class: "dlg-body" });
    const close = el("button", { class: "x", title: "Chiudi", onclick: () => this._closeRecipe() }, el("ha-icon", { icon: "mdi:close" }));
    const dialog = el("div", { class: "dlg", role: "dialog", ariaModal: "true", ariaLabel: a.title },
      el("div", { class: "dlg-head" }, el("div", { class: "dlg-title", textContent: a.title }), close),
      body,
      el("div", { class: "dlg-foot" },
        el("a", { class: "btn flat link", href: a.link, target: "_blank", rel: "noopener noreferrer" },
          el("ha-icon", { icon: "mdi:open-in-new" }), " Apri articolo")));
    const overlay = el("div", { class: "overlay", onclick: (e) => { if (e.target === overlay) this._closeRecipe(); } }, dialog);
    this._onKey = (e) => { if (e.key === "Escape") this._closeRecipe(); };
    window.addEventListener("keydown", this._onKey);
    this._overlay = overlay;
    this.shadowRoot.append(overlay);
    close.focus();

    const head = [this._newsMeta(a)];
    if (a.image) head.unshift(el("img", { class: "hero", src: a.image, referrerPolicy: "no-referrer", alt: "" }));
    const load = async (retry = false) => {
      body.replaceChildren(...head, el("div", { class: "loading", textContent: "Leggo l'articolo…" }));
      let data;
      try {
        data = await this._hass.callWS({ type: `${DOMAIN}/news/summary`, url: a.link, retry });
      } catch (err) {
        if (this._overlay !== overlay) return;
        body.replaceChildren(...head,
          el("p", { class: "desc", textContent: "Riassunto non disponibile." }),
          a.description ? el("p", { class: "hint", textContent: a.description }) : null,
          el("p", { class: "hint", textContent: "Motivo: " + (err.message || err) }));
        return;
      }
      if (this._overlay !== overlay) return;
      const list = (items, cls) => el("ul", { class: "sum" + (cls ? " " + cls : "") },
        ...(items || []).map((s) => el("li", { textContent: s })));
      const out = [...head, list(data.sentences)];
      const orig = Array.isArray(data.sentences_original) ? data.sentences_original : [];
      if (data.translated && orig.length) {
        const name = this._langName(data.lang);
        out.push(el("div", { class: "orig-title", textContent: "Originale" + (name ? ` (${name})` : "") }), list(orig, "orig"));
      }
      const notes = [data.partial || data.source === "meta"
        ? "C'era solo la descrizione pubblicata dal sito."
        : "Riassunto automatico: le frasi principali dell'articolo."];
      const failed = !data.translated && data.lang && data.lang !== "it" && !!data.translation_warning;
      if (data.translated) notes.push("Tradotto automaticamente");
      else if (failed) notes.push("Testo originale (traduzione non disponibile)");
      out.push(el("p", { class: "hint", textContent: notes.join(" · ") }));
      if (failed) {
        out.push(el("p", { class: "hint" }, "Motivo: " + data.translation_warning + " ",
          el("button", { class: "btn flat small", textContent: "Riprova la traduzione", onclick: () => load(true) })));
      }
      body.replaceChildren(...out);
    };
    load();
  }

  _closeRecipe() {
    if (this._overlay) this._overlay.remove();
    this._overlay = null;
    if (this._onKey) window.removeEventListener("keydown", this._onKey);
    this._onKey = null;
  }

  _siteLink(url) {
    return el("div", { class: "row" },
      el("a", { class: "btn flat link", href: url, target: "_blank", rel: "noopener noreferrer" },
        el("ha-icon", { icon: "mdi:open-in-new" }), " Apri sul sito"));
  }

  _recipeContent(r, it) {
    const out = [];
    const image = r.image || it.image;
    if (image) out.push(el("img", { class: "hero", src: image, referrerPolicy: "no-referrer", alt: "" }));
    if (r.description) out.push(el("p", { class: "desc", textContent: r.description }));

    const meta = el("div", { class: "meta" });
    const chip = (icon, text) => meta.append(el("span", { class: "chip" }, el("ha-icon", { icon }), text));
    if (r.times.total) chip("mdi:clock-outline", "Totale " + r.times.total);
    if (r.times.prep) chip("mdi:knife", "Preparazione " + r.times.prep);
    if (r.times.cook) chip("mdi:stove", "Cottura " + r.times.cook);
    if (r.yield) chip("mdi:silverware-fork-knife", /^\d+$/.test(r.yield) ? `${r.yield} persone` : r.yield);
    if (meta.childNodes.length) out.push(meta);

    if (!r.ingredients.length && !r.steps.length) {
      out.push(el("div", { class: "msg", textContent:
        "Questo sito non pubblica ingredienti e passaggi in un formato che riesco a leggere." }));
      out.push(this._siteLink(r.url || it.url));
      return out;
    }

    if (r.ingredients.length) {
      const have = this._ingredients.map((i) => i.toLowerCase());
      const ul = el("ul", { class: "ingr" });
      const boxes = [];
      const picker = el("div");
      const addBtn = el("button", { class: "btn" });
      const refresh = () => {
        const missing = boxes.filter((b) => !b.box.checked).length;
        addBtn.disabled = !missing;
        addBtn.replaceChildren(el("ha-icon", { icon: "mdi:playlist-plus" }),
          missing ? ` Aggiungi ${missing} alla lista` : " Hai già tutto");
        picker.replaceChildren();
      };
      for (const ing of r.ingredients) {
        const box = el("input", { type: "checkbox" });
        const li = el("li", {}, el("label", {}, box, el("span", { textContent: ing })));
        if (have.some((h) => ing.toLowerCase().includes(h))) li.classList.add("have");
        box.addEventListener("change", () => {
          li.classList.toggle("done", box.checked);
          refresh();
        });
        boxes.push({ box, ing });
        ul.append(li);
      }
      addBtn.addEventListener("click", () => {
        // Solo gli ingredienti NON spuntati (quelli spuntati li hai già)
        const items = boxes.filter((b) => !b.box.checked).map((b) => b.ing);
        this._todoPicker(picker, items, r.title || it.title);
      });
      refresh();
      out.push(el("h3", { textContent: "Ingredienti" }),
        el("p", { class: "hint", textContent: "Spunta quelli che hai già: gli altri puoi aggiungerli a una lista della spesa." }),
        ul,
        el("div", { class: "row" }, addBtn),
        picker);
    }

    if (r.steps.length) {
      const ol = el("ol", { class: "steps" });
      for (const s of r.steps) {
        if (s.heading) {
          ol.append(el("li", { class: "sec", textContent: s.heading }));
          continue;
        }
        const li = el("li", {}, el("p", { textContent: s.text }));
        if (s.images && s.images.length) {
          li.append(el("div", { class: "pics" },
            ...s.images.map((src) => el("img", { src, loading: "lazy", referrerPolicy: "no-referrer", alt: "" }))));
        }
        ol.append(li);
      }
      out.push(el("h3", { textContent: "Preparazione" }), ol);
    }
    if (r.savedAt) {
      out.push(el("p", { class: "hint", textContent:
        "Salvata nei preferiti il " + new Date(r.savedAt).toLocaleDateString("it-IT") + ": testo e foto sono conservati in Home Assistant." }));
    }
    out.push(this._siteLink(r.url || it.url));
    return out;
  }

  /* ---------- Impostazioni siti ---------- */

  _openSettings() {
    this._draft = this._sites.map((s) => ({ ...s, enabled: s.enabled !== false }));
    this._settingsMsg = null;
    this._view = "settings";
    this._render();
  }

  _closeSettings() {
    this._view = "search";
    this._render();
  }

  _renderSettings(main) {
    const list = el("div");
    if (!this._draft.length) list.append(el("div", { class: "empty", textContent: "Nessun sito. Aggiungine uno qui sotto." }));
    this._draft.forEach((s, idx) => {
      const check = el("input", { type: "checkbox", checked: s.enabled, title: "Attivo" });
      check.addEventListener("change", () => {
        s.enabled = check.checked;
        row.classList.toggle("off", !s.enabled);
      });
      const testMsg = el("div", { class: "msg" });
      const row = el("div", { class: "site" + (s.enabled ? "" : " off") },
        check,
        el("div", { class: "info" },
          el("div", { class: "t", textContent: s.name }),
          el("div", { class: "u", textContent: s.url }),
          testMsg),
        el("div", { class: "actions" },
          el("button", { class: "btn flat", textContent: "Prova", onclick: (e) => this._test(s.url, testMsg, e.currentTarget) }),
          el("button", { class: "btn danger", title: "Rimuovi", onclick: () => {
            this._draft.splice(idx, 1);
            this._render();
          } }, el("ha-icon", { icon: "mdi:delete-outline" }))));
      list.append(row);
    });

    const name = el("input", { type: "text", placeholder: "Nome, es. Fatto in casa da Benedetta" });
    const url = el("input", { type: "text", placeholder: "https://www.sito.it/cerca/?q={query}", inputMode: "url" });
    const addMsg = el("div", { class: "msg" });
    const add = () => {
      const n = name.value.trim();
      const u = url.value.trim();
      addMsg.className = "msg err";
      if (!n) return (addMsg.textContent = "Scrivi il nome del sito.");
      if (!/^https?:\/\//.test(u)) return (addMsg.textContent = "L'URL deve iniziare con https://");
      if (!u.includes("{query}")) return (addMsg.textContent = "Metti {query} nell'URL al posto degli ingredienti.");
      if (this._draft.some((s) => s.name.toLowerCase() === n.toLowerCase())) return (addMsg.textContent = "Esiste già un sito con questo nome.");
      this._draft.push({ name: n, url: u, enabled: true });
      this._settingsMsg = { ok: true, text: `“${n}” aggiunto: premi Salva per confermare.` };
      this._render();
    };

    const saveBtn = el("button", { class: "btn", textContent: "Salva", onclick: () => this._save(saveBtn) });
    const msg = this._settingsMsg ? el("div", { class: "msg " + (this._settingsMsg.ok ? "ok" : "err"), textContent: this._settingsMsg.text }) : null;

    main.append(
      el("div", { class: "box" },
        el("h2", { textContent: "I tuoi siti" }),
        el("p", { class: "hint", textContent: "Spunta i siti su cui cercare. “Prova” fa una ricerca di prova con “pasta”." }),
        list,
        msg,
        el("div", { class: "row" },
          el("button", { class: "btn flat", textContent: "Ripristina predefiniti", onclick: () => {
            const extra = this._draft.filter((s) => !this._defaults.some((d) => d.name === s.name));
            this._draft = [...this._defaults.map((d) => ({ ...d })), ...extra];
            this._settingsMsg = { ok: true, text: "Siti predefiniti ripristinati: premi Salva per confermare." };
            this._render();
          } }),
          el("button", { class: "btn flat", textContent: "Annulla", onclick: () => this._closeSettings() }),
          saveBtn)),
      el("div", { class: "box" },
        el("h2", { textContent: "Aggiungi un sito" }),
        el("div", { class: "form" }, name, url),
        el("p", { class: "hint", textContent:
          "Come trovare l'URL: cerca “pasta” sul sito, copia l'indirizzo della pagina dei risultati e sostituisci “pasta” con {query}." }),
        addMsg,
        el("div", { class: "row" },
          el("button", { class: "btn flat", textContent: "Prova", onclick: (e) => this._test(url.value.trim(), addMsg, e.currentTarget) }),
          el("button", { class: "btn", textContent: "Aggiungi", onclick: add }))),
    );
    if (this._sharePath) main.append(this._shareBox());
  }

  _shareBox() {
    const link = location.origin + this._sharePath;
    const input = el("input", { type: "text", readOnly: true, value: link });
    input.addEventListener("focus", () => input.select());
    const msg = el("div", { class: "msg" });
    const copy = async () => {
      try {
        await navigator.clipboard.writeText(link);
      } catch (_e) {
        // senza https il browser non dà accesso agli appunti
        input.select();
        document.execCommand("copy");
      }
      msg.className = "msg ok";
      msg.textContent = "Link copiato.";
    };
    const regenerate = async (btn) => {
      if (!confirm("Creare un nuovo link? Quello vecchio smetterà di funzionare.")) return;
      btn.disabled = true;
      try {
        const r = await this._hass.callWS({ type: `${DOMAIN}/share/regenerate` });
        this._sharePath = r.share_path;
        this._settingsMsg = { ok: true, text: "Nuovo link creato: il vecchio non funziona più." };
        this._render();
      } catch (err) {
        msg.className = "msg err";
        msg.textContent = "Errore: " + (err.message || err);
        btn.disabled = false;
      }
    };
    return el("div", { class: "box" },
      el("h2", { textContent: "Link diretto al ricettario" }),
      el("p", { class: "hint", textContent:
        "Con questo link chiunque lo abbia può cercare ricette senza entrare in Home Assistant (per esempio da un tablet in cucina). Non può cambiare i siti né vedere altro di Home Assistant." }),
      el("div", { class: "bar" }, input),
      msg,
      el("div", { class: "row" },
        el("button", { class: "btn flat", textContent: "Rigenera", onclick: (e) => regenerate(e.currentTarget) }),
        el("a", { class: "btn flat link", href: link, target: "_blank", rel: "noopener noreferrer" },
          el("ha-icon", { icon: "mdi:open-in-new" }), " Apri"),
        el("button", { class: "btn", textContent: "Copia link", onclick: copy })));
  }

  async _test(url, out, btn) {
    btn.disabled = true;
    out.className = "msg";
    out.textContent = "Provo…";
    try {
      const r = await this._hass.callWS({ type: `${DOMAIN}/sites/test`, url });
      if (!r.ok) {
        out.className = "msg err";
        out.textContent = "Non funziona: " + r.error;
      } else if (!r.count) {
        out.className = "msg err";
        out.textContent = "Il sito risponde ma non trovo ricette nella pagina (forse le carica con JavaScript).";
      } else {
        out.className = "msg ok";
        out.textContent = `Funziona: ${r.count} ricette trovate, es. “${r.results[0].title}”.`;
      }
    } catch (err) {
      out.className = "msg err";
      out.textContent = "Errore: " + (err.message || err);
    }
    btn.disabled = false;
  }

  async _save(btn) {
    btn.disabled = true;
    try {
      const r = await this._hass.callWS({ type: `${DOMAIN}/sites/save`, sites: this._draft });
      this._sites = r.sites;
      this._view = "search";
    } catch (err) {
      this._settingsMsg = { ok: false, text: "Non salvato: " + (err.message || err) };
    }
    this._render();
  }
}

customElements.define("labirinto-ricette-panel", LabirintoRicettePanel);
