# Ricette per Android

App Android che si collega a Home Assistant e usa l'integrazione **Labirinto Ricette**.
Tiene una **copia locale** delle ricette preferite (database SQLite + foto), così se non c'è rete
o Home Assistant non è raggiungibile l'app si apre comunque **in sola lettura** con le ricette salvate
l'ultima volta — comodo ai fornelli.

## Come funziona

- All'avvio mostra subito i preferiti salvati sul telefono e intanto prova a collegarsi a Home Assistant.
- **Online**: scarica i preferiti (`labirinto_ricette/favorites/list` + `favorites/get`) e le foto nuove, aggiorna
  la copia locale e permette di **cercare** ricette per ingredienti, aprirle, salvarle nei preferiti (♥) e
  rimuoverle. Le ricette tolte su Home Assistant spariscono anche qui.
- **Offline**: banner *Senza connessione · sola lettura* con la data dell'ultimo aggiornamento; i preferiti si
  aprono normalmente (foto, ingredienti con spunta, passaggi con foto) ma non si possono cercare né modificare.
  Appena torna la rete l'app riprova da sola.
- Se il download fallisce a metà la copia locale precedente resta intatta.
- Le ricette salvate sono uno **snapshot**: una volta scaricate non vengono riscaricate, così restano
  disponibili anche se il sito originale le toglie.

## Configurazione

1. Inserisci l'indirizzo di Home Assistant (es. `http://192.168.1.10:8123`).
2. Facoltativo: un **indirizzo alternativo** (es. `https://xxxx.ui.nabu.casa`), provato se il primo non risponde.
3. Accedi con **nome utente e password** di Home Assistant (anche con l'autenticazione a due fattori). La
   password **non viene salvata**: l'app tiene solo il token che Home Assistant le rilascia e lo rinnova da sola.
   In alternativa, scheda **Token**: un token di accesso a lunga durata.

Cambiando server o utente la copia locale viene cancellata e riscaricata.

## Scaricare l'APK

Il workflow **Android app** compila l'APK a ogni push che tocca `app/`: lo trovi negli *Artifacts* della run
(Actions → Android app). L'APK pubblicato è allegato alla **release dell'integrazione** (`vX.Y.Z`): non ci sono
release separate per l'app. Per pubblicare un'app nuova si alza la versione in `manifest.json` dell'integrazione: l'APK viene ricompilato solo
se `app/` è cambiata dalla release precedente, altrimenti viene riallegato quello già compilato.

Senza chiave di firma l'APK è firmato con una chiave di debug temporanea, diversa a ogni build: per
aggiornare bisogna disinstallare la versione precedente. Per aggiornamenti normali crea una chiave e aggiungi
i *secrets* `BCC_KEYSTORE_BASE64`, `BCC_KEYSTORE_PASSWORD`, `BCC_KEY_ALIAS`, `BCC_KEY_PASSWORD`:

```bash
keytool -genkeypair -v -keystore ricette.jks -alias ricette -keyalg RSA -keysize 4096 -validity 10000
```

```bash
base64 -w0 ricette.jks
```

## Compilare in locale

Serve l'Android SDK (Android Studio). Dalla cartella `app/`:

```bash
./gradlew assembleRelease
```

## Note

- La password non viene mai salvata; i token sono nelle preferenze private dell'app (escluse dal backup).
- È ammesso HTTP in chiaro e sono accettati i certificati CA installati dall'utente, per gli Home Assistant
  raggiungibili solo in LAN o con certificato autofirmato.
- Requisiti: Android 8.0 o superiore, integrazione Labirinto Ricette installata e configurata.
