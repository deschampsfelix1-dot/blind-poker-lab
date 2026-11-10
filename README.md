# Blind Poker Lab

Horloge et gestion de tournois de poker entre amis : structure des blindes, gains, primes (KO), tirage des places, classement.

Application web en un seul fichier (`index.html`), installable sur tablette et téléphone (« Ajouter à l'écran d'accueil »). Les données restent enregistrées dans le navigateur de l'appareil.

## Appli Android (Google Play)

Le dossier `android/` contient l'appli Android : elle embarque `index.html` et fonctionne sans Internet. GitHub la construit automatiquement à chaque modification et publie les fichiers sur la page **Releases** du dépôt :

- `playstore-aab-…` : le fichier `.aab` à envoyer sur la Play Console (signé si la clé d'envoi est enregistrée dans les secrets du dépôt : `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`) ;
- `test-apk-…` : un `.apk` à installer directement sur un téléphone pour essayer.

Politique de confidentialité (demandée par Google Play) : `confidentialite.html`.
