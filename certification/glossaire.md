# Glossaire

Termes techniques employés dans les dossiers de certification, expliqués pour un lecteur non spécialiste.

| Terme | Définition |
|---|---|
| **API REST** | Interface qui permet à un programme (navigateur, application tierce) d'interroger le serveur par des adresses web et de recevoir des données au format JSON. |
| **Backoff exponentiel** | Stratégie de nouvelle tentative après un échec réseau, en doublant l'attente à chaque essai (2 s, 4 s, 8 s) pour ne pas surcharger le service distant. |
| **BPMN** | Notation standard de modélisation des processus métier, en couloirs par acteur. |
| **Bucket4j / rate limiting** | Limitation du nombre de requêtes qu'une même adresse IP peut envoyer par minute, pour bloquer le scraping et les attaques par force brute. |
| **Cache-aside / TTL** | Le serveur cherche d'abord la donnée dans le cache (Redis) ; s'il ne la trouve pas, il la calcule puis la range dans le cache pour une durée limitée (TTL, ici 1 heure). |
| **Carry buffer** | Mémoire tampon qui garde les dernières lignes d'une page de données incomplète pour les fusionner avec la page suivante, afin qu'un restaurant à cheval sur deux pages soit enregistré en entier. |
| **CI/CD** | Intégration et déploiement continus : chaque modification du code est compilée et testée automatiquement, puis déployée en production si tous les contrôles passent. |
| **CORS** | Règle du navigateur qui empêche un site tiers d'appeler l'API au nom d'un utilisateur connecté. |
| **Couverture de code (JaCoCo)** | Pourcentage du code exécuté par les tests automatisés ; JaCoCo est l'outil qui la mesure. |
| **CVE / Dependabot** | Une CVE est une vulnérabilité publiquement référencée ; Dependabot (GitHub) alerte quand une bibliothèque utilisée par le projet en contient une. |
| **DICP** | Les quatre critères de sécurité d'une information : Disponibilité, Intégrité, Confidentialité, Preuve. |
| **EBIOS RM** | Méthode française d'analyse des risques de sécurité publiée par l'ANSSI. |
| **Elasticsearch** | Moteur de recherche plein texte, utilisé pour l'autocomplétion de la recherche. |
| **Forensic** | Analyse a posteriori des traces laissées dans un système pour établir si et comment une intrusion a eu lieu. |
| **GSD / Superpowers** | Deux méthodes outillées successivement utilisées pour piloter le développement assisté par IA ; Superpowers enchaîne pour chaque fonctionnalité : besoin, spécification, plan, implémentation, revue. |
| **HSTS** | En-tête qui oblige le navigateur à toujours utiliser HTTPS pour le site. |
| **IDOR** | Faille où un utilisateur accède aux données d'un autre en modifiant un identifiant dans la requête. |
| **JWT / cookie httpOnly** | Le JWT est un jeton signé qui prouve l'identité de l'utilisateur ; stocké dans un cookie `httpOnly`, il est inaccessible au JavaScript de la page et donc à une attaque XSS. |
| **Kanban** | Organisation du travail en flux continu de tâches priorisées, sans itérations de durée fixe. |
| **MoSCoW** | Méthode de priorisation : Must (indispensable), Should (important), Could (confort), Won't (exclu pour l'instant). |
| **NYC Open Data** | Portail de données publiques de la ville de New York, source des inspections sanitaires. |
| **Pentest** | Test d'intrusion : attaque simulée et autorisée d'un système pour en trouver les failles. |
| **Railway / Railpack** | Railway est la plateforme d'hébergement de l'application ; Railpack est son outil qui détecte le type de projet et le construit automatiquement. |
| **RBAC** | Contrôle d'accès par rôle : les droits dépendent du rôle de l'utilisateur (client, contrôleur, administrateur). |
| **RGPD** | Règlement européen sur la protection des données personnelles. |
| **Row-Level Security (RLS)** | Mécanisme PostgreSQL qui interdit l'accès aux lignes d'une table à tout rôle non explicitement autorisé. |
| **SAST / SCA / DAST** | Analyse de sécurité du code source (SAST), des bibliothèques utilisées (SCA), ou de l'application en fonctionnement (DAST). |
| **Test slice (MockMvc)** | Test qui démarre uniquement la couche web d'un contrôleur pour vérifier ses réponses HTTP, sans base de données. |
| **Testcontainers** | Bibliothèque qui lance de vraies bases de données dans des conteneurs Docker éphémères le temps d'un test. |
| **TJM** | Taux journalier moyen : prix d'une journée de travail d'un prestataire, utilisé pour valoriser une charge en jours. |
| **UML** | Langage de modélisation graphique d'un logiciel : cas d'utilisation, classes, activités. |
| **XSS (stocké, auto-XSS)** | Injection de code JavaScript dans une page vue par d'autres utilisateurs ; « stocké » quand le code est enregistré en base, « auto-XSS » quand seul son auteur peut le voir. |
