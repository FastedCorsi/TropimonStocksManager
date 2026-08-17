# TropimonStocksManager

Mod Fabric client autonome pour Minecraft 1.21.1. Il indexe localement le dernier contenu connu des coffres de ville que le joueur ouvre normalement.

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
- Pendant l'indexation, un HUD indique le coffre en cours et le bilan de zone : détectés, indexés, inaccessibles et restants. Le bouton de l'écran permet d'arrêter immédiatement le mode.
- Une barre de fraîcheur colore chaque objet et chaque coffre : vert avant 2 h, orange de 2 h à 24 h, rouge au-delà.
- Cliquer sur un objet ouvre sa fiche complète avec tous les coffres, coordonnées, quantités directes, quantités en shulkers et date de dernière vérification.
- L'historique conserve une seule entrée par jour et par serveur. L'entrée du jour est mise à jour sur place, puis comparée au dernier jour précédent (`320 → 245`, soit `-75`).
- Un seuil minimum peut être défini dans la fiche d'un objet. La vue `★` conserve tous les objets surveillés, même à zéro, et une alerte est envoyée lors du passage sous le seuil.
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
