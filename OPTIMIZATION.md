# Optimisation et autonomie — 30 août 2026

## Périmètre

TropimonStocksManager ne référence aucun package, état, configuration ou service de nos autres mods personnalisés. Ses dépendances officielles restent Minecraft, Fabric, Cobblemon et TropimodClient. Les assets et les claims du client officiel restent utilisés : remplacer cette autorité par une estimation locale changerait les permissions et n'a pas été fait.

Les nouveaux composants (`SnapshotWriter`, `TownClaimScan`, `ChestPositions`) appartiennent au package de Stocks Manager. Aucune bibliothèque commune ni nouvelle dépendance obligatoire n'a été créée. Les changements d'interface/historique déjà présents ont été conservés. Aucun garde-fou inter-mods n'a été supprimé.

## Changements et gains attendus

- **Sauvegarde** : les coffres, objets imbriqués et relevés quotidiens sont immuables. Le thread du jeu copie les racines des collections et les seuils, puis un seul thread dédié sérialise le JSON et écrit le fichier temporaire. Il ne manipule jamais d'objet Minecraft ni de registre.
- **Pas de perte des changements concurrents** : chaque modification reçoit une révision. Seule une écriture réussie acquitte cette révision. Une écriture en cours peut être suivie d'un seul snapshot en attente ; les états intermédiaires sont remplacés par le plus récent, qui contient toutes les données.
- **Erreurs et arrêt** : les erreurs sont journalisées et la révision reste non sauvegardée. Nouvelle tentative après le délai de deux secondes, sans boucle serrée. À `CLIENT_STOPPING`, le dernier état est envoyé, la file est vidée et une dernière tentative est faite en cas d'échec. Une erreur explicite signale les révisions restant non sauvegardées. Remplacement atomique du fichier lorsque le système de fichiers le permet, repli sur le remplacement temporaire précédent sinon.
- **Recherche** : cache par serveur des agrégats, totaux, textes normalisés, sources et tris par filtre. Après construction, une frappe ne parcourt plus les contenus des coffres/shulkers et ne renormalise/retrie plus tous les résultats. Elle filtre les types d'objets et construit seulement les vues correspondantes.
- **Invalidation** : toute nouvelle capture invalide le serveur concerné, y compris une relecture identique pour actualiser les dates. Les seuils et comparaisons quotidiennes restent lus à la demande ; ils ne sont pas figés dans le cache de stock. Une recherche « shulkers » ne modifie pas un résultat « total » déjà affiché.
- **Scan** : métadonnées de réflexion résolues une fois, appartenance du joueur relue à chaque passe, un accès aux claims par chunk durant cette passe seulement. Pas de permission conservée entre deux passes ou jusqu'à la capture. Les positions immuables sont créées seulement pour les coffres admissibles, les vecteurs seulement pour les raycasts nécessaires ; identité canonique d'un double coffre calculée une seule fois par candidat.
- **Capture** : suppression de copies temporaires d'ItemStack avant la conversion immédiate en données immuables propres au mod. Les vérifications de ville ne sont plus répétées pendant les ticks où la capture attend ou a déjà été effectuée.

Le format JSON de cette optimisation était en version 2. La version 3 ajoute ensuite les préférences locales de Stocks Manager sans modifier les champs de stock ; les anciens formats sans historique ni `checkedAt` restent lisibles. Une shulker remplie reste un conteneur, une shulker vide reste un objet. Les calculs entiers, filtres, ordre des résultats, seuils et comparaisons sont conservés.

## Protections et temporisations conservées

- Portée maximale inchangée : distance au carré de 20,25 (4,5 blocs).
- Raycast de visibilité, ville du joueur, données serveur, interaction Minecraft normale et restrictions de conteneur conservés.
- Absence de profil/ville/claim ou erreur d'accès aux données officielles : refus.
- Nouvelle vérification des claims au moment de la capture ; le petit cache de scan ne sert jamais à cette vérification ultérieure.
- Réouverture après 20 minutes, nouvel essai d'un coffre inaccessible après 20 secondes.
- Capture/fermeture au troisième tick d'écran, deux ticks entre actions, expiration d'une ouverture après plus de 40 ticks, nouvelle recherche après cinq ticks quand aucun coffre n'est disponible.
- Inventaire, Ender Chest et shulkers personnelles exclus comme auparavant.

## Validation reproductible

Sous Java 21 :

```powershell
.\gradlew.bat test build remapSmokeJar
.\tools\run-smoke.ps1 -LauncherRoot (Join-Path $env:APPDATA '.tropimon') -Profile official
.\tools\run-smoke.ps1 -LauncherRoot (Join-Path $env:APPDATA '.tropimon') -Profile coexistence
```

`run-smoke.ps1` lit les JAR et bibliothèques du launcher, crée une instance **séparée** sous `build/smoke-runs/`, utilise une identité fictive hors connexion et s'arrête automatiquement. Il ne copie aucune configuration de jeu personnelle et ne remplace aucun fichier du launcher. Le petit mod de test est produit sous `build/smoke/` et n'est jamais inclus dans le JAR distribué.

Le profil `official` inclut uniquement Stocks Manager et les dépendances officielles nécessaires. TropimodClient embarque TropimonCore/Tropifurnitures, dont le chargement nécessite aussi Mega Showdown, Architectury, GeckoLib et Trinkets ; Xaero World Map est également requis par le client officiel. Ces dépendances existent déjà dans le pack, elles ne proviennent pas de nos mods personnalisés.

Le profil `coexistence` ajoute les sept autres JAR Tropimon personnalisés installés : BidMaker, CatchPreview, ChatFilter, DamageCalc, TeamBuilder/SaveTeam, TeamHunt et UIBattle. Le marqueur `STOCK_SMOKE_OK` après chargement des ressources valide les assertions de stock et le passage par les quatre écrans, avant l'arrêt propre. Le fichier `config/tropimon_stocks_manager/town-chests.json` de l'instance de test permet de vérifier le vidage de sauvegarde.

Les tests unitaires couvrent les snapshots immuables (contenus et seuils imbriqués), changements pendant une écriture, coalescence, ordre des révisions, nouvelles tentatives et erreur permanente, sauvegarde à l'arrêt, compatibilité JSON, résultats et invalidation des caches, séparation des serveurs/filtres, modifications de seuils/historique, fraîcheur, renouvellement des claims et borne de réouverture. Des assertions de dépendances/imports/ressources protègent l'autonomie du module.

### Résultats de cette exécution

- `test build remapSmokeJar` : succès sous Java 21 ; **22 tests, zéro échec**.
- Profil officiel : `build/smoke-runs/official-20260830-200227/stdout.log`, marqueur de succès à 20:03:31, puis arrêt propre.
- Profil coexistence : `build/smoke-runs/coexistence-20260830-200217/stdout.log`, marqueur de succès à 20:03:27, puis arrêt propre.
- Après arrêt, les deux fichiers de test contiennent le même résultat : un coffre, total quotidien de 128 pierres, seuil de 200, schéma version 2.
- Aucun doublon de classe entre le JAR généré et les sept JAR personnalisés contrôlés. Le JAR distribué ne contient pas `StockSmokeClient`.
- SHA-256 du JAR construit : `A11B9F8656079B6B6B18F9E6E808B977B50170AF1A1AA6AFC80DCDBFE9DC6CB0`.
- SHA-256 du JAR installé, inchangé : `1A2F9547E69978043C20798EC13AF9F8A544D198CD76EB7A627A81E67A2074EF`.

## Limites

- Les essais clients restent hors serveur : ils ne prétendent pas mesurer les FPS en ville ni valider une ouverture réelle sous les permissions du serveur de production. Les décisions de claims et limites temporelles sont couvertes par tests ciblés, pas par une session connectée.
- Le premier chargement JSON, la construction initiale/reconstruction d'un cache après capture et la copie des racines du snapshot restent sur le thread du jeu. Ce n'est donc pas une promesse d'absence totale de saccades pour un index énorme.
- Les caches et snapshots en attente consomment davantage de mémoire en échange d'un travail CPU/IO moindre dans la boucle du jeu. La file d'écriture est bornée à un état en cours et un état en attente.
- La fermeture attend la fin des écritures. Un disque très lent peut retarder l'arrêt ; aucun logiciel ne peut garantir une sauvegarde sur disque plein, panne persistante ou arrêt forcé du processus. Le dernier fichier remplacé avec succès reste la référence et les erreurs sont visibles dans les logs.
- Les avertissements de ressources manquantes émis par les dépendances officielles lors des tests hors pack de ressources complet ne sont pas corrigés par ce travail.

Aucune installation du nouveau JAR, aucun commit et aucun push ne font partie de cette intervention.
