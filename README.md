# Labirinto Ricette

Integrazione per Home Assistant (installabile con HACS): scrivi gli ingredienti che hai in casa e cerca ricette
su più siti di cucina, con anteprima, foto e link alla ricetta.

Siti già impostati: **Giallo Zafferano, Cookist, La Cucina Italiana**, più **HelloFresh** e **Cucchiaio d'Argento**
disattivati di default (HelloFresh carica i risultati con JavaScript, Cucchiaio d'Argento blocca le richieste automatiche). Puoi attivarli/disattivarli e aggiungerne altri.

## Installazione con HACS
1. HACS → menu ⋮ → **Repository personalizzati**.
2. Incolla `https://github.com/LabirintoDigitale/labirinto-ricette`, categoria **Integrazione**.
3. Scarica **Labirinto Ricette** e riavvia Home Assistant.
4. **Impostazioni → Dispositivi e servizi → Aggiungi integrazione → Labirinto Ricette**.

## Uso
### Pagina "Ricette" nel menu laterale
Dopo aver aggiunto l'integrazione compare la voce **Ricette** nella barra laterale di Home Assistant:
1. scrivi gli ingredienti che hai in frigo (separati da virgola o uno alla volta con **Invio**);
2. premi **Cerca ricette**: sotto compare l'elenco delle ricette trovate, con foto e sito;
3. tocca una ricetta: si apre una finestra con descrizione, tempi, ingredienti (in grassetto quelli che hai) e i passaggi con le foto. Il link **Apri sul sito** porta alla pagina originale (anche Ctrl+clic);
4. con **⚙ Siti** (solo amministratori) attivi/disattivi i siti, li rimuovi, ne aggiungi di nuovi e li provi con **Prova**.

### Lista della spesa
Nella finestra della ricetta spunta gli ingredienti che hai già e premi **Aggiungi alla lista**: scegli una delle
tue liste delle attività (Casa, Shopping list…) e gli ingredienti **non spuntati** vengono aggiunti, con il nome della
ricetta come nota se la lista lo supporta. L'ultima lista usata resta evidenziata.

### Preferiti
Nella finestra della ricetta tocca il **♥** in alto per salvarla in **I miei preferiti**. Vengono salvati il testo
completo e tutte le foto (in `config/labirinto_ricette/images`), così la ricetta resta disponibile anche se il sito
la toglie o chiude. Toccando di nuovo il ♥ la ricetta e le sue foto vengono cancellate.

### Link diretto (senza login)
In **⚙ Siti** trovi il **link diretto al ricettario**: si apre senza entrare in Home Assistant (es. un tablet in
cucina). Chi ha il link può solo cercare e leggere ricette. **Rigenera** crea un link nuovo e disattiva il vecchio.

### Carta Lovelace
La carta viene caricata automaticamente. Aggiungi una carta manuale:
```yaml
type: custom:labirinto-ricette-card
title: Cosa cucino?
max_per_site: 6
```
Scrivi gli ingredienti separati da virgola e premi **Cerca**.

### Servizio
```yaml
action: labirinto_ricette.search
data:
  ingredients: "pasta, zucchine, uova"
  max_per_site: 5
response_variable: ricette
```
L'ultimo risultato è disponibile anche nell'attributo `results` di `sensor.labirinto_ricette_risultati`.

## Gestire i siti
**Impostazioni → Dispositivi e servizi → Labirinto Ricette → Configura**: attiva/rimuovi siti o aggiungine uno
indicando nome e URL di ricerca, con `{query}` al posto degli ingredienti:
```
https://www.miosito.it/cerca/?q={query}
```
Per trovare l'URL: fai una ricerca sul sito, copia l'indirizzo e sostituisci il testo cercato con `{query}`.

## Come funziona e limiti
L'integrazione scarica la pagina dei risultati di ricerca e ne estrae titolo, link e immagine (prima dai dati
strutturati JSON-LD, poi dai link con immagine). Non usa API ufficiali: se un sito cambia struttura, usa
protezioni anti-bot o genera i risultati via JavaScript (può succedere con HelloFresh), potrebbe restituire pochi
risultati o nessuno. Apri una issue indicando il sito. Rispetta i termini d'uso dei siti: i risultati
rimandano sempre alla pagina originale.
