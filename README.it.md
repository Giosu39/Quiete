<p align="center">
  <img src="docs/banner.svg" alt="Quiete" width="100%">
</p>

<p align="center">
  <b>Un'app Android calma e minimale che riproduce rumori rilassanti per aiutarti a dormire.</b><br>
  Rumore marrone, marrone profondo, onde, rosa e bianco, generati in tempo reale.<br>
  Niente file audio, niente loop, niente internet, niente pubblicità. Solo rumore.
</p>

<p align="center">
  <a href="https://github.com/giozuki/Quiete/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/giozuki/Quiete/build.yml?branch=main&style=flat-square&label=build"></a>
  <a href="https://github.com/giozuki/Quiete/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/giozuki/Quiete?style=flat-square&color=A8784A&label=release"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-5B8A78?style=flat-square&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-A8784A?style=flat-square&logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-8E7BB5?style=flat-square&logo=jetpackcompose&logoColor=white">
  <img alt="Licenza: MIT" src="https://img.shields.io/badge/Licenza-MIT-6A5E51?style=flat-square">
</p>

<p align="center">
  <a href="README.md">🇬🇧 English</a> · 🇮🇹 Italiano
</p>

---

<p align="center">
  <img src="docs/themes.svg" alt="Temi giorno, sera e notte" width="100%">
</p>
<p align="center"><sub>L'interfaccia cambia in base all'orario (anteprima grafica).</sub></p>

## ✨ Funzioni

| | |
|---|---|
| 🌊 **Cinque rumori** | Marrone, Marrone profondo, Onde, Rosa, Bianco. Generati in tempo reale e in stereo, quindi non si sente mai un loop. |
| ⏳ **Timer** | Da 15 minuti a 2 ore, oppure senza limite. Con il tasto **+15 min** mentre suona. Alla fine il suono sfuma piano (da 10 secondi a 5 minuti). |
| 🌗 **Tema in base all'orario** | Verde salvia di giorno, indaco la sera, nero caldo e ambra di notte, con pochissima luce blu. |
| 🌑 **Schermo oscurato** | Di notte lo schermo si oscura da solo dopo 20 secondi senza tocchi. Resta solo un orologio tenue che si sposta un po' ogni minuto, per proteggere gli schermi OLED. |
| 🎚️ **Tono** | Da *naturale* a *ovattato*, con un filtro passa-basso. |
| 🔒 **A schermo spento** | Continua a suonare in background, con una notifica che mostra il conto alla rovescia e i comandi rapidi. |
| 🎧 **Audio intelligente** | Si ferma se scolleghi le cuffie, si abbassa durante le chiamate e, se vuoi, suona insieme a podcast o audiolibri. |
| 🌍 **Italiano e inglese** | L'interfaccia segue la lingua del telefono. |
| 🫁 **Cerchio che respira** | Il cerchio centrale respira a circa 6 respiri al minuto, un ritmo spesso usato per rilassarsi. |

## 📥 Download

Scarica l'ultimo APK dalla pagina [**Releases**](../../releases/latest), aprilo sul telefono e consenti l'installazione da origini sconosciute.

Richiede **Android 8.0** (API 26) o superiore.

## 🛠️ Compilare dal codice

```bash
git clone https://github.com/giozuki/Quiete.git
cd Quiete
./gradlew assembleRelease
```

Oppure apri la cartella in **Android Studio** e premi ▶ Run.

GitHub Actions compila anche ogni push su `main`. L'APK si scarica come artefatto dell'esecuzione.

## 🎛️ Come viene generato il suono

Il rumore viene sintetizzato campione per campione in un thread dedicato e inviato a un `AudioTrack` (44,1 kHz, stereo, float):

- **Bianco**: un generatore casuale xorshift.
- **Marrone**: integrazione "con perdita" del bianco, `y = (y + 0.02·x) / 1.02`, che dà una pendenza di −6 dB/ottava.
- **Marrone profondo**: marrone con un ulteriore filtro passa-basso a circa 280 Hz.
- **Rosa**: filtro di Paul Kellet (−3 dB/ottava).
- **Onde**: marrone modulato da due LFO lenti e sfasati, così le onde non si ripetono mai uguali.
- **Tono**: filtro passa-basso a un polo, con la frequenza di taglio che va da 18 kHz a 250 Hz su scala logaritmica.

Ogni canale ha il suo generatore, quindi l'immagine stereo è ampia e naturale. Tutte le variazioni di volume sono graduali, per evitare scatti.

## 🔐 Privacy

Quiete **non** chiede l'accesso a internet: non raccoglie né invia nulla. Usa solo i permessi per le notifiche e la riproduzione in background.

## 🗂️ Struttura

```
app/src/main/java/com/quiete/app/
├── NoiseEngine.kt     # sintesi del rumore in tempo reale
├── SleepPlayer.kt     # stato, timer, dissolvenze
├── NoiseService.kt    # servizio in primo piano, notifica, focus audio
├── Prefs.kt           # impostazioni salvate
├── MainActivity.kt
└── ui/
    ├── QuieteApp.kt   # interfaccia Compose
    └── Palette.kt     # palette giorno / sera / notte
```

<details>
<summary><b>Pubblicare una nuova release (per chi mantiene il progetto)</b></summary>

<br>

**Pubblicare una nuova versione**

1. In `app/build.gradle.kts` aumenta entrambi i valori:
   ```kotlin
   versionCode = 2        // numero intero, deve sempre crescere (1, 2, 3, ...)
   versionName = "1.1"    // la versione mostrata agli utenti
   ```
   Se `versionCode` non aumenta, Android potrebbe non riconoscere il nuovo APK come aggiornamento.
2. Fai commit e push.
3. Crea il tag corrispondente, da *Releases → Draft a new release* su GitHub oppure da terminale:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```
4. Il workflow **Release** compila l'APK firmato e lo allega alla release (circa 3 minuti).

**Chiave di firma**

Le release sono firmate con la chiave salvata nei secret del repository, così gli utenti possono aggiornare senza disinstallare.

Per configurare la chiave da zero (già fatto per questo repository):

1. Crea un keystore una volta sola:
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias quiete -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In *Settings → Secrets and variables → Actions* aggiungi:
   - `KEYSTORE_BASE64`: l'output di `base64 -w0 release.jks`
   - `KEYSTORE_PASSWORD`
   - `KEY_ALIAS` (es. `quiete`)
   - `KEY_PASSWORD`
3. Conserva `release.jks` al sicuro e **non** caricarlo mai nel repository.

Senza questi secret, l'APK viene firmato con una chiave di debug temporanea.

</details>

## 📄 Licenza

[MIT](LICENSE)

Il font [Nunito](https://github.com/googlefonts/nunito) è distribuito con licenza [SIL Open Font License 1.1](third_party/Nunito-OFL.txt).
