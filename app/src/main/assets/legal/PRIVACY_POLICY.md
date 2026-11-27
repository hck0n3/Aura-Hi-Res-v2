# Privacy Policy — Aura Hi-Res v2

**Version:** 4 · **Effective date:** November 24, 2026

This is the English translation of the privacy policy of **Aura Hi-Res v2** (the "App"). The primary version is the Spanish one, [PRIVACIDAD.md](PRIVACIDAD.md); if the two differ, the Spanish version prevails. It expands on clause 11 of the [Terms and Conditions](app/src/main/assets/legal/TERMINOS_Y_CONDICIONES.md) (in Spanish).

## Who is responsible

**The independent developer of Aura Hi-Res v2**, based in **Honduras** ("we", "us"). Contact for anything about your data: **aurahires@gmail.com**.

## In short

- The App has **no analytics, tracking, advertising or advertising profiles**, and it does not send us crash reports or usage data automatically.
- You have no account with us. Your library, history, playlists, searches and your YouTube or Spotify sessions **are stored on your device**.
- Even so, **some data does leave your device**, and all of it is listed here: what music services and other third parties receive, and what our own services receive (not one server: four services).
- Two features that send data are **on by default**: **Aura services** and **automatic lyrics translation**. In addition, **device sync** turns itself on when you activate your licence with the purchase e-mail. All three can be turned off.
- **We do not sell your data** or share it for advertising.

## 1. What stays on your device

- What you play, pause and skip, and your listening history (for the statistics and for the recommendations computed on the device itself).
- Your playlists, likes, search history, settings and downloads.
- Access to your local music files.
- Cookies and session tokens of the third-party services you sign in to. They are **not included** in backups or in sync. They only leave your device if you send your Google session to your TV with "Sign in on the TV" (section 2), end-to-end encrypted.
- The purchase e-mail, if you type it when activating the licence.

You erase all of it by clearing the App's data or uninstalling it. Whatever has already been sent to our services (section 2) is erased as explained in section 6.

## 2. What our services receive

We run four online services on Cloudflare's infrastructure: the licence server (which also serves the AI features), Aura services, device sync and Aura's own Listen Together rooms, the only server Listen Together uses.

| Service | What it receives | When | How long it is kept |
|---|---|---|---|
| **Licences** | Your licence key and a technical device identifier (ANDROID_ID, or a random one if it is unavailable). If you type it, the purchase e-mail, which is **compared** with Gumroad's and **not stored** on the server. | Always: when starting the demo, activating the licence and opening the App. | The link between your key and your devices (with the last-use date), for as long as your licence exists. The demo record of each device is kept so the demo does not restart when data is cleared. |
| **AI** (same server) | What each AI feature sends (section 4) and your IP address. | Only when you use an AI feature, or when lyrics translation reaches its last resort. | The content is not stored. The IP is kept for at most **48 hours**, only to limit the number of daily requests. |
| **Aura services** (**on by default**; turned off in Settings ▸ Servicios Aura) | The request for the signed configuration (feature switches and notices), with no data about you. Artist names, to get their styles. Artist and album, to look up animated covers. For "Pedir música", an encrypted fingerprint (SHA-256) of the request and the list the AI proposed, **never the text you typed**. | While you use the App. | Artist styles: 30 days. Animated covers: 7 days. "Pedir música" answers: 3 days. These are shared caches that do not identify you. |
| Aura services: **cloud backup** | Your backup (library and history), **encrypted on your device** with a key derived from a secret only you have. We cannot read it. | Only when you create one. | The **last 3** copies; older ones are deleted. |
| Aura services: **diagnostic log** | The App's technical log. It does not include titles, artists, playlist names, accounts, e-mails or keys; it may include song and playlist identifiers. A fingerprint of your IP to limit uploads. | Only when you press **"Enviar registro"** (Send log). | **7 days**. |
| Aura services: **smart-queue AI second opinion** (**off by default**) | Title and artist of what is playing and of the doubtful candidate songs, up to four style names, the language and whether it is Christian music. Your IP. | Only if you turn it on. | The content is not stored. The IP, for at most 48 hours. |
| **Device sync** (turns itself on when you activate the licence **with the purchase e-mail**, or when you pair with a code; turned off in Settings ▸ Sincronizar dispositivos) | Your likes and dislikes, your playlist names and the title, artist and cover of their songs; your device names; what is playing (title, artist, cover and position). Your group is identified by an encrypted fingerprint (SHA-256) of your key and e-mail, or of your code. It does **not** store accounts, cookies, the e-mail or the key. | While it is on. | For as long as your group exists. You can ask us to delete it. |
| **Listen Together: Aura rooms** (the only Listen Together server) | The name you choose, the room code and the data of the songs playing in it and in the host's queue (identifier, title, artist, album, duration and cover). | Only when you create or join a room. | An empty room is deleted after **5 minutes**. |
| Aura rooms: **Sign in on the TV** | Your Google account session (the cookies and session identifiers the App keeps) that your phone sends to your TV, **encrypted on the phone** with a key that exists only in the TV's QR code and never reaches the server, and the model of both devices, encrypted the same way. The server only sees a fingerprint (SHA-256) of the code and can neither read nor change what passes through. | Only when, on the phone, you tap "Send session" after scanning or typing the code the TV shows. | Delivered **once** and deleted; nothing lasts more than **2 minutes**. |
| **Song recognition** (relay) | An audio signature computed from the microphone. | Only while you use recognition, and only if the direct connection to Shazam fails. | It is forwarded to Shazam; we do not store it. |

## 3. What third parties receive

These third parties receive the requests and handle them under **their own** privacy policies. All of them can see your IP address.

**Always, because the App cannot work without them:**

- **YouTube / YouTube Music (Google):** search, browsing, playback, video identifiers and, if you sign in, your account cookies. This is unavoidable in a YouTube Music client.
- **Google image servers:** cover art and thumbnails.
- **GitHub:** downloading the configuration that keeps playback working (no data about you) and the update check (on by default; can be turned off in settings).
- **Gumroad:** when you pay, Gumroad receives your payment and billing data under its own policy. We do not receive or store card data; in our Gumroad dashboard we see the sale data Gumroad provides (for example, the e-mail, date, amount and subscription status).

**Automatic lyrics translation (on by default; can be turned off in settings):** the **lyrics text** goes to **Google Translate**. If there is no translation, **SimpMusic** is queried with the song identifier, and as a last resort the lyrics text goes to our AI server (section 2).

**Depending on the features you use:**

- **Lyrics providers:** song title, artist, album and duration, until one has the lyrics. You can choose the order.
- **iTunes (Apple), Apple Music and other metadata and animated-cover providers:** song, artist and album names.
- **Wikipedia:** the artist's name, when you view artist information.
- **Shazam:** the song-recognition audio signature, only while you use it.
- **Your own AI provider:** if you configure your own key, the text of your AI requests goes to that provider instead of our server.

**Optional integrations (they send nothing until you enable them):** Last.fm (scrobbling and taste import), ListenBrainz, Spotify import, SponsorBlock, AutoEQ, podcasts and the like. Each sends only what it needs (for example, the song playing, a headphone model or a search term). When you sign in to YouTube, Spotify or Last.fm, your credentials go directly to that service.

## 4. Artificial intelligence

The App is developed with the help of AI tools under the developer's direction. Those tools **are not connected to the App** and do not receive your data automatically. If you send us a diagnostic log or a report, we may analyse it with their help; that is why the log does not include titles, artists, accounts, e-mails or keys.

Inside the App, the AI features send to our AI server (third-party open models hosted on Cloudflare) only this:

| Feature | Default | What it sends |
|---|---|---|
| **AI playlist and "Pedir música"** | Available; sends only when you use it | The text you type and, when modifying a playlist, the titles and artists of its songs. |
| **"Recomendado para ti (IA)"** | **Off** | Once turned on, periodically: up to 16 songs (title and artist) from your most played and your likes, up to 8 of your most frequent artists, your dominant genres and up to 24 titles from the previous list. |
| **Smart-queue AI second opinion** | **Off** | As described in section 2. |
| **Lyrics translation** | **On** | Only as a last resort: the lyrics text (section 3). |

## 5. What we use the data for

Only to make the App work: playing, searching, showing lyrics and artwork, verifying your licence or demo, syncing your devices, Listen Together rooms, the AI features you use, and investigating bugs when you send us a log. We do **not** use it for advertising, we build no profiles, and we do not sell it or share it for advertising. We would disclose it only if a law or a competent authority required us to.

## 6. Your rights

You can ask us, by writing to **aurahires@gmail.com**, for:

- **Access:** to know what data about you our services keep and to get a copy.
- **Rectification:** to correct it.
- **Erasure:** to delete it (for example, the link between your licence and your devices, your sync group or your cloud backups). The demo record of each device (its technical identifier and start date) is an exception: we keep it for as long as needed to prevent the demo from being repeated.
- **Objection:** to stop processing it for a specific purpose. You can also turn off Aura services, sync, automatic lyrics translation and the AI features yourself.

To check that the request is yours, we will ask you to write from the purchase e-mail or to give us your licence key. We will answer within **30 days** at most.

In Honduras, **article 182 of the Constitution** recognises the guarantee of **habeas data**: the right to access information about you held in files or records, and to ask for it to be corrected, updated or deleted. If you live in another country, you keep the rights its law gives you, and you can complain to its data-protection or consumer-protection authority.

## 7. Security

Communications with our services are encrypted (HTTPS). Cloud backups are encrypted on your device (AES-256-GCM) before they are sent, and so is the session you send to your TV, with a key that exists only in the TV's code. Your sync group and your cloud-backup identifier are encrypted fingerprints that do not reveal your secret. No system is perfectly secure; if we detect an incident affecting your data, we will announce it in the App.

## 8. Children

The App is not directed to children under 13, and we do not knowingly collect data from children under 13. If you are a parent or guardian and believe a child has sent us data, write to us and we will delete it.

## 9. Where data is processed

Our services run on Cloudflare's global network, so your requests may be processed outside your country. The third parties in section 3 process data in their own locations.

## 10. Changes to this policy

Each version carries its number and effective date. If a change widens the data that is sent or how it is used, we will tell you in the App before applying it and, when it affects the Terms and Conditions, you will be asked to accept them again.

## 11. Contact

- **E-mail:** aurahires@gmail.com
- **Responsible party:** the independent developer of Aura Hi-Res v2 (Honduras) · aurahires@gmail.com
- **Project:** [https://github.com/hck0n3/Aura-Hi-Res-v2](https://github.com/hck0n3/Aura-Hi-Res-v2)

When you use **Reporte de errores y sugerencias** (Bug reports and suggestions) in the App, your own e-mail app opens with a message addressed to aurahires@gmail.com. We receive only what you choose to send (and any logs or screenshots you choose to attach).
