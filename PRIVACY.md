# Confidentialité de TropimonStocksManager

By FastedCorsi

## Règle permanente

L'attribution publique du développeur est exactement `By FastedCorsi`, notamment dans la liste `authors` de `fabric.mod.json`. Les licences et crédits tiers sont conservés. Les consignes complètes figurent dans [AGENTS.md](AGENTS.md) et s'appliquent aux prochaines versions, corrections et fonctionnalités.

Les informations civiles/professionnelles, chemins personnels et secrets ne doivent entrer dans aucun fichier partageable. Utiliser des chemins calculés à partir de l'environnement, jamais une copie du chemin d'une machine. L'obfuscation ne protège pas un secret.

## Contrôles automatiques

Sous Java 21, depuis la racine du dépôt :

```powershell
.\gradlew.bat test build remapSmokeJar
```

- `privacyCheck` inspecte tous les fichiers partageables du dossier de travail, y compris les nouveaux fichiers non suivis et le wrapper Gradle tiers. Il refuse aussi un fichier privé déjà suivi par Git : ajouter un tel fichier à `.gitignore` ne suffit pas.
- `privacyArtifacts` inspecte les JAR finaux et de développement, y compris le JAR de sources, les octets des constantes compilées, les ressources et les archives imbriquées. Les contrôles sont rattachés au build et à la production des JAR remappés. La tâche d'installation locale dépend également de ce contrôle, mais ne se lance jamais automatiquement.
- `privacySmoke` contrôle séparément les deux JAR de test isolé. Les classes du contrôleur et du mod de test ne sont pas distribuées dans le mod de jeu.
- Une détection, une archive illisible, un lien symbolique non examiné ou un dépassement des limites d'inspection fait échouer le contrôle. Les rapports indiquent une règle et un emplacement relatif, jamais la valeur détectée. Aucune exception aux détections n'a été nécessaire pour cette version.
- Les tests utilisent exclusivement des identités fictives et vérifient chemins Windows/Unix, e-mails, signatures de secrets, auteur exact, constantes, texte UTF-16, archives imbriquées, commentaires ZIP, fichiers privés suivis, erreurs d'inspection et préservation des originaux/crédits tiers.

Le contrôleur lit le compte système courant en mémoire. Pour rechercher aussi d'autres identités connues (nom civil, coordonnées professionnelles, employeur), fournir `TROPIMON_PRIVACY_TERMS`, une valeur par ligne, ou `TROPIMON_PRIVACY_TERMS_FILE`, le chemin d'un fichier privé situé **hors du dépôt**. Ne pas saisir de vraies valeurs dans une commande versionnée, une fixture ou un rapport public. Aucun de ces termes n'est écrit dans les artefacts. Dans une CI sous un compte générique, fournir les termes privés par le mécanisme sécurisé de la CI pour conserver cette couverture nominative.

Les signatures génériques détectent des chemins de profil, des adresses e-mail et plusieurs familles de secrets. Une adresse ou un crédit tiers signalé doit être examiné, pas effacé aveuglément. Toute éventuelle exception future devra être étroite, justifiée et sans données personnelles du développeur. Un vrai secret trouvé nécessite une intervention du propriétaire ; le contrôle ne le teste ni ne le révoque.

## Originaux locaux et distribution

Les captures et textes promotionnels sous `promo/`, configurations de jeu, logs, sauvegardes, fichiers `.env`, sorties de build et données Git restent privés. Les originaux existants sont conservés sur disque. `.gitignore`, les exclusions des archives et `.gitattributes` protègent respectivement le suivi futur, les JAR et les exports de sources. Une règle `export-ignore` sur un dossier exclut tout son contenu.

Distribuer le JAR remappé dans `build/libs/`, pas une copie brute du dossier de travail. Les dossiers ignorés, journaux de build et anciennes sorties peuvent toujours contenir des chemins personnels. Les règles d'export des sources s'appliqueront aux futurs commits qui les contiendront ; elles ne modifient pas les archives d'anciens commits.

## Bilan du nettoyage — 31 août 2026

- Attribution corrigée dans les métadonnées du mod et ajoutée au mod de test ; README attribué à `By FastedCorsi`.
- Exemples de chemins personnels remplacés par une résolution portable dans `OPTIMIZATION.md`.
- Contrôle des sources partageables et des nouveaux JAR, avec les identités connues du compte local et des métadonnées Git fournies uniquement en mémoire. Aucune donnée recherchée ni signature de secret détectée après nettoyage.
- Aucun code de jeu, calcul, filtre, permission, délai, sauvegarde ou dépendance modifié par ce nettoyage. Comparaison avec la version locale précédente : seule l'entrée `fabric.mod.json` diffère ; les classes et les autres ressources sont identiques.
- Recompilation complète sous Java 21 : **34 tests réussis, zéro échec**, dont 12 tests du contrôle de confidentialité et 22 tests de non-régression/autonomie. Les essais en jeu isolés de l'optimisation précédente n'ont pas été relancés pour ce changement de métadonnées ; leur compte rendu reste dans `OPTIMIZATION.md`.

## Traces anciennes et limites

- Les deux commits accessibles localement (`0702f0c`, `b3827e1`) conservent une attribution auteur/committer non conforme et une ancienne mention personnelle dans `src/main/resources/fabric.mod.json`. Ils n'ont pas été réécrits. L'état distant n'a pas été mis à jour.
- La consultation publique des releases GitHub du dépôt au 31 août 2026 n'a retourné aucune release. Cela ne vérifie pas les brouillons privés, téléchargements antérieurs, pièces jointes ou copies partagées ailleurs.
- Le JAR déjà installé, ses sauvegardes et les captures personnelles restent intacts ; leurs anciennes données ne sont pas effacées par ce nettoyage. Aucun commit, push, publication, remplacement du launcher ou changement de configuration Git globale n'a été effectué.
- Le contrôle n'est pas une garantie d'anonymat absolu : il ne connaît pas les identités supplémentaires non fournies, ne fait pas d'OCR des images et ne prétend pas détecter tous les secrets possibles ni analyser un contenu chiffré. Les fichiers exclus sont privés, pas anonymisés. Les limites d'archives sont bloquantes, pas des exclusions silencieuses.
- Avant tout futur commit autorisé, vérifier séparément l'auteur et le committer effectifs : `FastedCorsi` avec une adresse GitHub noreply réellement vérifiée. Ne jamais inventer cette adresse.

## Version fonctionnelle 0.2.0 — 2 septembre 2026

- Les états de couverture, métadonnées de coffres, vues, pauses d’alertes et transitions sont enregistrés uniquement dans le JSON local existant. Aucun accès réseau ni vraie base de données n’a été ajouté.
- Les nouveaux écrans réutilisent le fond et les flèches du PC Cobblemon ainsi que les icônes officielles TropimodClient de ville, filtre, tri, historique, horloge, favori, validation et actualisation. Aucun asset provenant d’un autre mod personnalisé n’est embarqué.
- La tâche `deliver` produit un JAR `SHARE` sans installation et un JAR `LOCAL` identique accompagné d’un script externe portable. Le script vérifie le hash, la cible et le verrouillage, attend l’arrêt de Minecraft sans arrêter le jeu ni le launcher, archive l’ancien JAR hors du dossier chargé, puis vérifie la copie. Son fichier d’état distingue `prepared`, `waiting`, `installed` et `blocked`.
- Les sources, JAR intermédiaires, JAR de test, deux livrables, script et hash sont soumis au contrôle de confidentialité. Le JAR public n’embarque ni installateur, ni chemin de machine, ni configuration locale.
- Validation finale : 40 tests sans échec, test client isolé officiel réussi pour les sept écrans, deux JAR identiques et attribution `By FastedCorsi` confirmée. SHA-256 des deux JAR : `55984AEEE261ED36D1B131774EEF4FA05C8E9FA47753B0505D0978DBE5802242`.
