# TropimonStocksManager

By FastedCorsi

Mod Fabric client autonome pour Minecraft 1.21.1. Il indexe localement le dernier contenu connu des coffres de ville que le joueur ouvre normalement.

Toutes les données du mod restent dans son unique fichier JSON local. Aucune base de données, API de stockage, synchronisation distante ou compte externe n’est utilisé.

## Fonctionnement

- Une cinquième icône « coffre » est ajoutée en haut à droite du menu de ville/guilde Tropimon.
- Le bouton utilise directement l'icône `relic_coin` de Cobblemon.
- L'interface s'ouvre aussi avec la touche configurable `N` ou `/tropistock`. L'ancien raccourci `O` est migré une seule fois vers `N` pour éviter les conflits avec JEI et les autres mods.
- La recherche couvre les noms, identifiants de mod, noms de coffre et coordonnées.
- Les filtres séparent les objets directs des objets contenus dans les shulkers rangées dans un coffre.
- Une shulker remplie est traitée comme un conteneur et n'est pas comptée comme objet ; une shulker vide reste comptabilisée.
- Après une première ouverture autorisée, le dernier contenu connu reste consultable sans rouvrir le coffre.
- « Indexer en marchant » (ou `/tropistock scan`) reste actif pendant les déplacements et ouvre puis referme successivement les coffres visibles à portée normale. Relancer la commande, recliquer la pièce ou utiliser `/tropistock stop` arrête le mode.
- Un coffre inaccessible est ignoré temporairement sans arrêter l'indexation ; les nouveaux coffres entrant à portée sont détectés en continu.
- Un coffre déjà contrôlé est ignoré pendant au moins 20 minutes, y compris après une déconnexion ou un redémarrage, afin que l'indexeur avance sur les autres coffres de la zone.
- L'écran reprend directement le style du PC Cobblemon : cinq lignes par page, navigation, recherche et colonnes `OBJET`, `COFFRE`, `SHULKER`, `TOTAL`.
- Pendant l’indexation, un HUD indique le coffre en cours et le bilan de zone : détectés, indexés, inaccessibles et restants. Le bouton de l’écran permet d’arrêter immédiatement le mode.
- La vue de couverture détaille chaque coffre détecté : à jour, ancien, hors de portée, masqué par un obstacle, hors de la ville, refusé par le serveur ou encore en attente.
- Une barre de fraîcheur colore chaque objet et chaque coffre : vert avant 2 h, orange de 2 h à 24 h, rouge au-delà.
- Cliquer sur un objet ouvre sa fiche complète avec tous les coffres, coordonnées, quantités directes, quantités en shulkers et date de dernière vérification.
- L'historique conserve une seule entrée par jour et par serveur. L'entrée du jour est mise à jour sur place, puis comparée au dernier jour précédent (`320 → 245`, soit `-75`).
- Un seuil minimum peut être défini dans la fiche d’un objet. La vue `★` conserve tous les objets surveillés, même à zéro, et une alerte est envoyée lors du passage sous le seuil.
- Le centre d’alertes affiche un badge sur la pièce, conserve le passage sous le seuil entre les reconnexions et permet une pause de 24 heures sans spam.
- Les tris par quantité, nom, variation, fraîcheur et alerte sont combinables avec les filtres de mod, de stock bas et de données anciennes. Quatre vues nommées peuvent être enregistrées localement.
- L’historique affiche un graphique sur 7, 30 ou 90 jours, la consommation quotidienne moyenne et une estimation du nombre de jours restants, tout en conservant un seul relevé par jour.
- Les coffres peuvent recevoir un nom local, jusqu’à huit étiquettes et un favori. Un coffre absent est seulement signalé au premier passage ; le nettoyage collectif exige au moins deux passages, et l’oubli individuel demande une confirmation.
- Les icônes d'objets viennent du registre Minecraft chargé : Minecraft, Cobblemon et contenus Tropimon sont donc rendus avec leurs vrais assets.
- Les claims déjà synchronisés par TropimodClient sont vérifiés : seuls les coffres situés dans la ville du joueur sont acceptés.
- L'inventaire du joueur, son Ender Chest et les shulkers qu'il porte ou ouvre directement ne sont jamais indexés.

Le mod n'envoie aucun paquet personnalisé et ne contourne aucune permission : un coffre doit être ouvert par une interaction Minecraft normale pour que le client reçoive et mémorise son contenu. Une modification ultérieure du coffre est prise en compte lors de sa prochaine ouverture.

L'indexeur assisté utilise les interactions Minecraft normales. La portée, la visibilité, les claims Tropimon et les permissions du serveur restent appliqués.

## Build

Depuis la racine du dépôt :

```powershell
.\gradlew.bat test build
```

Le JAR remappé est généré dans `build/libs/`.

La tâche `deliver` produit deux exemplaires contrôlés de la même version sous `build/deliverables/share/` et `build/deliverables/local/`. Le dossier local contient le script externe d’installation différée ; aucun mécanisme d’installation n’entre dans le JAR partageable.

Le build vérifie aussi la confidentialité des sources et des archives finales (y compris les constantes compilées et archives imbriquées). Voir [la politique et les limites du contrôle](PRIVACY.md). Les captures promotionnelles locales ne font pas partie de la distribution.

## Performances et autonomie

Les sauvegardes utilisent un écrivain asynchrone unique et des snapshots immuables ; les agrégats et index de recherche sont mis en cache par serveur. Les claims restent vérifiés à chaque passe et à la capture.

Voir [le bilan d'optimisation et les tests isolés](OPTIMIZATION.md) pour les garanties, les commandes de validation et les limites.


## Mises à jour automatiques

Le mod vérifie sa propre Release GitHub au démarrage, au maximum une fois toutes les six heures. Lorsqu'une version plus récente est disponible, son JAR et son SHA-256 sont contrôlés, puis la mise à jour est installée après l'arrêt de Minecraft avec sauvegarde de l'ancien JAR. Le launcher peut rester ouvert.

La vérification s'effectue en arrière-plan et n'ajoute aucun travail par tick. Elle peut être désactivée avec "enabled": false dans config/tropimon_stocks_manager-updater.json.

