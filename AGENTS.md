# Confidentialité permanente des mods Tropimon

Cette règle demandée par l'utilisateur s'applique à toute création, correction, optimisation, compilation et livraison des mods Tropimon de ce dépôt, y compris leurs futurs modules.

## Attribution et données du développeur

- La mention d'auteur publique du développeur est exactement « By FastedCorsi ». Dans fabric.mod.json, utiliser `"authors": ["By FastedCorsi"]` pour son attribution ; conserver les crédits et licences des autres auteurs.
- Ne jamais ajouter de prénom, nom civil, adresse personnelle ou professionnelle, employeur, nom de compte système ou chemin absolu personnel du développeur dans le code, les ressources, scripts, tests, exemples, documentation, messages de commit ou artefacts destinés à être partagés.
- Ne pas recopier ces données dans ce fichier de consignes, une liste de détection versionnée ou un rapport destiné à la publication. Les éventuels relevés confidentiels restent hors des dépôts et masquent les valeurs sensibles.
- Utiliser des chemins portables et des données fictives neutres. Examiner chaque occurrence avant de la modifier : préserver les identifiants techniques, données de joueurs nécessaires au fonctionnement, dépendances, licences et crédits tiers.
- Conserver les URLs et clés explicitement publiques nécessaires au fonctionnement. Ne jamais embarquer de secret, jeton personnel ou configuration réelle de session ; ne pas considérer l'obfuscation comme une protection.

## Contrôle avant livraison

- Pour chaque changement livré, contrôler les fichiers modifiés et le contenu des nouveaux artefacts : métadonnées auteur/contact, constantes compilées, ressources et archives imbriquées, chemins personnels et secrets.
- Vérifier les JAR finaux après compilation, pas seulement les sources. Exclure des paquets les configurations personnelles, logs, captures personnelles, sauvegardes, fichiers .env et dossiers .git.
- Lors du premier nettoyage d'un module, examiner tout son contenu destiné à être publié ; après cela, prévenir les réintroductions. Installer ou conserver un contrôle automatisé dans le build/CI quand la tâche porte sur ce nettoyage ou sa prévention ; ne pas y inscrire de données personnelles réelles.
- Si le contrôle détecte des données personnelles ou un secret, corriger avant de déclarer l'artefact prêt à diffuser. Ne pas supprimer les originaux personnels sur disque pour nettoyer une distribution.
- Signaler les éléments non vérifiés et les risques résiduels ; ne pas promettre une anonymisation absolue ou l'effacement de copies déjà récupérées.

## Git et périmètre des actions

- Avant tout nouveau commit autorisé, vérifier l'identité d'auteur et de committer effective : pseudonyme FastedCorsi et adresse GitHub noreply valide déjà vérifiée. Ne pas inventer d'adresse ; si aucune adresse valide n'est disponible, terminer le travail local sans créer le commit concerné et signaler le point.
- Ne pas modifier la configuration Git globale. Signaler séparément les données restant dans l'historique, les releases ou les copies déjà distribuées ; changer un fichier ou ajouter .gitignore ne les efface pas.
- Ne pas réécrire l'historique, pousser de force, publier, remplacer un JAR du launcher ou révoquer une clé sans autorisation correspondante. Ne jamais tester un secret contre un service pour vérifier sa validité dans le cadre de ce nettoyage.
- Préserver la logique, les fonctionnalités et l'indépendance de chaque mod. Une dépendance à un autre mod développé par nous doit être remplacée par un équivalent autonome lorsqu'elle est concernée par la tâche, sans retirer une protection nécessaire.
- Ajouter cette règle sans données personnelles dans tout nouveau dépôt autonome de mod Tropimon. Team Hunt et Bid Maker restent mis de côté tant que l'utilisateur ne demande pas leur reprise.

## Vérification propre à Stocks Manager

- Sous Java 21, exécuter `./gradlew deliver` avant livraison. `privacyCheck` contrôle les sources partageables ; `privacyArtifacts`, `privacySmoke` et `privacyDeliverables` contrôlent les archives, le mod de test et les deux livrables avec leurs accompagnements. Les tests de détection utilisent uniquement des identités fictives.
- Le contrôle connaît le compte système courant sans l'enregistrer. Pour des identités civiles/professionnelles supplémentaires, fournir `TROPIMON_PRIVACY_TERMS` (une valeur par ligne) ou `TROPIMON_PRIVACY_TERMS_FILE`, un fichier privé situé hors du dépôt. Ne jamais versionner ces valeurs ni les afficher dans les rapports.
- Ne pas désactiver une détection pour livrer. Examiner les crédits tiers et les données fonctionnelles avant toute correction ; tout ajout d'exception doit être ciblé, justifié et ne contenir aucune donnée personnelle réelle du développeur.
- Les originaux sous `promo/`, les données de jeu et les sorties sous `build/` restent locaux. Ne pas partager une copie brute du dossier de travail. Voir `PRIVACY.md` pour la portée et les limites.

## Deux livraisons JAR à chaque version

- À chaque livraison d'une version ou d'un changement de code, fournir deux JAR clairement séparés : un JAR local accompagné du système de mise à jour différée de l'instance du launcher, et un JAR prêt à partager. Utiliser deux dossiers ou noms explicites ; ne jamais installer les deux exemplaires simultanément.
- Les deux JAR proviennent de la même version validée et offrent les mêmes fonctionnalités. Ils peuvent être identiques octet pour octet : privilégier un petit script externe pour l'installation locale, sans dupliquer le code du mod ni embarquer ce mécanisme dans le JAR public.
- Le launcher peut rester ouvert. Le système local attend uniquement la fermeture du jeu Minecraft concerné avant de remplacer le JAR du mod dans la bonne instance. Ne jamais forcer l'arrêt du launcher ou du jeu, toucher aux autres mods ni remplacer un fichier utilisé ou verrouillé.
- Cette demande constitue l'autorisation permanente de préparer et d'armer cette installation différée lors d'une livraison, sauf consigne explicite contraire pour la tâche. Une demande de conseil, d'audit ou de mise à jour des règles ne déclenche ni compilation ni installation.
- Réutiliser et adapter les outils locaux existants. Vérifier la cible exacte, l'intégrité du JAR et le résultat de la copie ; conserver une sauvegarde de l'ancien JAR hors du dossier des mods chargés. En cas de cible ambiguë, d'accès impossible ou de verrouillage, conserver le fichier préparé et signaler le blocage sans forcer.
- Le JAR partageable ne contient ni chemin personnel, configuration locale, secret, donnée privée ni outil d'installation spécifique à la machine. Appliquer les contrôles de confidentialité aux deux JAR et aux éventuels fichiers qui les accompagnent. Conserver l'attribution « By FastedCorsi » et les crédits tiers.
- Dans la livraison, indiquer les deux JAR et leur version, les contrôles effectués et l'état réel de l'installation locale : préparée, en attente de fermeture ou installée après vérification. Ne pas annoncer une installation réussie parce qu'un script a seulement été lancé.

## Code simple, lisible et efficace

- Préserver strictement la logique, les fonctionnalités et les protections. Chercher les gains utiles de performance, mémoire et poids sans rendre le code difficile à comprendre.
- Choisir la solution la plus simple qui répond au besoin actuel. Éviter les classes, interfaces, factories, couches de services, méthodes relais et dépendances ajoutées sans utilité concrète ; ne pas bâtir un framework pour un cas isolé.
- Garder des classes cohérentes et des méthodes lisibles quand leur séparation aide réellement. Ne pas tout fusionner dans une classe géante ni compacter le code : moins de fichiers ou de lignes ne garantit pas de meilleures performances.
- Réutiliser ce qui existe dans le mod ; supprimer le code mort seulement après vérification des usages, y compris mixins, réflexion, événements, ressources et compatibilité. Pas de réécriture générale pour une optimisation locale.
- Cibler les coûts identifiés : travail répété par tick ou par frame, scans, allocations, entrées/sorties et caches sans limite. Justifier les gains et vérifier les comportements concernés ; ne pas ajouter de cache, de thread ou d'abstraction préventive sans besoin démontré.
- Chaque mod reste autonome : aucune dépendance aux classes, états ou services internes de nos autres mods. Recréer dans le mod concerné la petite implémentation nécessaire plutôt qu'imposer une bibliothèque commune ; préserver les dépendances officielles nécessaires.

## Publication et mise à jour autonome

- Chaque version livrée est poussée sur le dépôt GitHub public propre à ce mod, puis publiée dans une Release dont le tag correspond exactement à la version.
- La Release contient un seul JAR partageable vérifié et son fichier SHA-256. Les JAR LOCAL, configurations et scripts propres à une machine ne sont jamais publiés.
- Ce mod embarque sa propre implémentation de mise à jour. Elle ne dépend d'aucune classe, bibliothèque ou service interne d'un autre mod Tropimon.
- La mise à jour accepte uniquement la Release officielle de ce dépôt, exige le SHA-256, vérifie l'identifiant et la version de fabric.mod.json, prépare le fichier hors du dossier mods, puis remplace l'ancien JAR seulement après l'arrêt de Minecraft. Elle ne force jamais l'arrêt du jeu ou du launcher et conserve une sauvegarde hors des mods chargés.
- Une évolution de l'updater doit rester légère, asynchrone et sans travail répété par tick ou par frame.



## Consentement et mise à jour indépendante du launcher

- Toute récupération de fichier, y compris JAR, empreinte et catalogue externe, exige un accord éclairé préalable du joueur. Ne jamais télécharger en arrière-plan avant cet accord.
- La vérification des métadonnées de mise à jour est désactivée sans consentement explicite ; un ancien `enabled: true` généré automatiquement ne vaut pas accord. L'autorisation de vérifier ne vaut jamais autorisation de télécharger ou installer une version.
- Présenter le mod, la version, la source officielle, les fichiers et le remplacement différé avec sauvegarde avant le bouton de téléchargement. Refuser, reporter ou fermer ne déclenche aucun téléchargement.
- Chaque mod contient sa propre implémentation. Utiliser le Java existant et le JAR réellement chargé ; ne demander aucune modification du launcher, installation d'un outil ou chemin personnel.
- Gérer le dossier mods classique et le stockage Tropimon reconnu. Conserver le nom enregistré, synchroniser les deux copies et préserver le suivi ainsi que les autres mods. Une disposition inconnue doit bloquer proprement, sans contourner une protection du launcher.
- Tester le helper réellement exporté : attente de Minecraft, fichiers modifiés/verrouillés, sauvegarde, deux types de stockage et absence de consentement. Ne pas confondre un installateur local validé avec l'updater livré aux joueurs.

- Canal de transition : publier les nouvelles releases stables avec `--latest=false` et la mention `<!-- tropimon-consent-updater:2 -->` dans leurs notes. Vérifier après publication que `/releases/latest` reste inchangé ; les anciens updaters non consentis ne doivent pas être déclenchés pour récupérer le correctif. Le nouvel updater sélectionne ce canal dans `/releases?per_page=20`. Une première installation manuelle peut être nécessaire depuis une version ancienne.

## Lisibilité aux quatre échelles GUI

- Gérer les échelles Minecraft 1, 2, 3 et 4 sans modifier le réglage global du joueur. Vérifier aussi les fenêtres réduites et le redimensionnement ; distinguer l'échelle demandée de celle réellement appliquée par Minecraft.
- Conserver des textes, valeurs, contrôles et infobulles lisibles. Adapter l'agencement et le défilement à l'espace disponible ; ne pas masquer une valeur essentielle ou remplacer sa lecture par une police minuscule.
- Rendu, clics, survol, glisser-déposer et découpe utilisent la même transformation. Contrôler les interactions et protections existantes, pas seulement une capture à l'échelle 2. Les mods restent indépendants, avec une implémentation locale simple.
