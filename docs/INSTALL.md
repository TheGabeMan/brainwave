# Installing Brainwave on your phone · Brainwave installeren

Android only. It takes about five minutes, most of it the first-time email setup (one tap, if you choose the Mail app).
*Alleen Android. Het kost ongeveer vijf minuten, het meeste daarvan is het instellen van e-mail.*

---

## English

### 1. Download and install

1. **On your phone**, open this link: <https://github.com/TheGabeMan/brainwave/releases/latest/download/brainwave.apk>
   — it downloads a file called `brainwave.apk`.
2. Open the downloaded file (tap the download notification, or find it in **Files → Downloads**).
3. Android says it doesn't allow installing apps from this source. Tap **Settings**, switch on
   **Allow from this source**, go back, and tap **Install**. You only do this once.

   Google Play Protect may offer to "scan the app". That is fine; scanning is optional.

### 2. First start

1. Open **Brainwave** and tap the big button.
2. Allow the **microphone**.
3. It asks to download the **speech model** (about 40 MB, once). Tap **Download**, preferably on Wi-Fi.
   Your recording is kept while it downloads.

### 3. Set up email (once)

Open **Settings** (the gear, top right) and fill in **Recipient email** — where your brainwaves are sent,
usually your own address. Then choose **how** under **Sending email**:

**Mail app — the easy way.** Tap **Mail app**. That is all. Each time you save a brainwave, your
mail app opens with the message and the recording already filled in; tap **Send**. Press
**Open mail app with a test message** to see it. (Brainwave only offers email apps, never chat apps.)

**SMTP — automatic, but fiddly.** Sends in the background with no tap, but needs your mail account's
details. For **Gmail**:
  - Host `smtp.gmail.com`, security **STARTTLS**, port `587`.
  - Username: your full Gmail address.
  - Password: **not** your normal password but an **app password**. Create one at
    <https://myaccount.google.com/apppasswords> (needs 2-step verification switched on).
  - Press **Send test email**. If it arrives, you are done.

SMTP is what Brainwave starts with, so if you want the easy way, switch to **Mail app**.

### 4. Updates

Updates are not automatic. When a new version is announced, open the same link above again and
install over the old one — your brainwaves are kept.

---

## Nederlands

### 1. Downloaden en installeren

1. **Open op je telefoon** deze link: <https://github.com/TheGabeMan/brainwave/releases/latest/download/brainwave.apk>
   — er wordt een bestand `brainwave.apk` gedownload.
2. Open het gedownloade bestand (tik op de downloadmelding, of zoek het bij **Bestanden → Downloads**).
3. Android meldt dat apps van deze bron niet geïnstalleerd mogen worden. Tik op **Instellingen**,
   zet **Toestaan van deze bron** aan, ga terug en tik op **Installeren**. Dit doe je maar één keer.

   Google Play Protect kan aanbieden de app te scannen. Dat mag, maar hoeft niet.

### 2. Eerste keer starten

1. Open **Brainwave** en tik op de grote knop.
2. Geef toestemming voor de **microfoon**.
3. De app vraagt om het **spraakmodel** te downloaden (ongeveer 40 MB, één keer). Tik op **Download**,
   bij voorkeur via wifi. Je opname blijft bewaard tijdens het downloaden.

### 3. E-mail instellen (eenmalig)

Open **Settings** (het tandwiel rechtsboven) en vul **Recipient email** in — waar je brainwaves naartoe gaan,
meestal je eigen adres. Kies daarna onder **Sending email** **hoe** de mail wordt verstuurd:

**Mail app — de makkelijke manier.** Tik op **Mail app**. Meer is het niet. Telkens als je een brainwave
bewaart, opent je mailapp met het bericht en de opname al ingevuld; tik op **Verzenden**. Met
**Open mail app with a test message** zie je hoe het werkt. (Brainwave toont alleen mailapps, nooit chatapps.)

**SMTP — automatisch, maar lastig.** Verstuurt op de achtergrond zonder tik, maar heeft de gegevens van je
mailaccount nodig. Voor **Gmail**:
  - Host `smtp.gmail.com`, beveiliging **STARTTLS**, poort `587`.
  - Gebruikersnaam: je volledige Gmail-adres.
  - Wachtwoord: **niet** je gewone wachtwoord maar een **app-wachtwoord**. Maak er een op
    <https://myaccount.google.com/apppasswords> (tweestapsverificatie moet aanstaan).
  - Tik op **Send test email**. Komt die aan, dan ben je klaar.

Brainwave begint met SMTP, dus wil je de makkelijke manier, schakel dan over naar **Mail app**.

### 4. Updates

Updates gaan niet automatisch. Als er een nieuwe versie is, open je dezelfde link opnieuw en installeer
je die over de oude heen — je brainwaves blijven bewaard.
