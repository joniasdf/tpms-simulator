# TPMS-simulaattori (Android)

Simuloi jälkiasennettavia Bluetooth LE -rengaspaineantureita (FL, FR, RL, RR + vararengas) BLE-TPMS-vastaanottimien ja -sovellusten testaukseen.

## Ominaisuudet
- Anturikohtaisesti: ID (12 heksamerkkiä), paine, lämpötila, akku %, hälytykset (nopea vuoto, heikko paristo, ei signaalia).
- Painenäyttö kPa / bar / psi.
- Skenaariot: normaali, hidas vuoto (−0,5 kPa/s), nopea vuoto (−10 kPa/s), ylikuumeneminen, heikko paristo, anturi katoaa (10 s päällä / 10 s pois).
- Mainos kiertää aktiivisten anturien välillä 0,5 s välein.

## Mainosmuoto
Theengs Decoder "TPMS": valmistajadata `00 01` + 6 tavun osoite (ensimmäinen tavu 0x80+paikka) + paine Pa (int32 LE) + lämpö 0,01 °C (int32 LE) + akku % + hälytystavu (bitti 0). Lähde: github.com/theengs/decoder, src/devices/TPMS_json.h.

## Rakentaminen
`./gradlew assembleDebug` (JDK 17, Android SDK 34). Kentän arvo tallentuu, kun kenttä menettää fokuksen.

## Rajoitukset
- Auton tehdasanturit (315/433 MHz) eivät ole simuloitavissa puhelimella.
- Puhelimen todellista BLE-MAC-osoitetta ei voi asettaa (Android satunnaistaa sen); anturin ID kulkee datassa.

## Versio 2
- Material 3 / Jetpack Compose, alapalkki: Simulaattori / Lukija, auton yläkuva värikoodatuilla renkailla (vihreä OK, keltainen varoitus, punainen hälytys, harmaa ei signaalia), tumma teema järjestelmän mukaan.
- Kentät päivittyvät skenaarion aikana; paine syötetään valitussa yksikössä.
- Lukija: BLE-skannaus (BLUETOOTH_SCAN, Android <12: sijaintilupa), dekoodaa Theengs TPMS -muodon; tuntemattomista "TPMS"-nimisistä laitteista näytetään raaka valmistajadata.
- Kuvakaappaukset: `./gradlew recordRoborazziDebug` → /workspace/screenshots/.
