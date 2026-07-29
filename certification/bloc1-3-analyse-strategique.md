# Dossier d'analyse stratégique

**Certification** : RNCP niveau 7 — Expert en informatique et système d'information (3W Academy)
**Bloc** : 1 — Analyser et définir une stratégie de systèmes d'information
**Livrable** : Dossier d'analyse stratégique
**Candidat** : Arnaud Thery
**SI analysé** : `restaurant-analytics`

> Ce dossier s'appuyait initialement sur *"Étude de cas Aflokkat"* (document de groupe, Drive) — devenu inaccessible au moment de la rédaction. Il est donc reconstruit intégralement à partir du système d'information réel du candidat, `restaurant-analytics`, dans la même logique que les Blocs 2.1-2.3, 3 et 4A : tout ce qui suit est vérifiable dans le dépôt, pas projeté.

---

## 1. Enjeux et orientations stratégiques

`restaurant-analytics` a démarré comme exercice académique (module big data, Aflokkat) et a évolué en un service à trois enjeux distincts, qui se sont ajoutés au fil du projet plutôt que d'être fixés a priori (cf. Bloc 2.3, §2.1 — objectifs fixés vs atteints) :

1. **Enjeu pédagogique initial** — démontrer une maîtrise technique (ingestion de données publiques, agrégations, API REST) sur un cas concret.
2. **Enjeu de portfolio** — un service réellement déployé et utilisable, pas un exercice jetable : démo publique en production, données réelles synchronisées en continu.
3. **Enjeu de certification** — depuis juillet 2026, le SI sert de preuve technique vivante pour 3 des 4 blocs de la certification RNCP (Blocs 3, 4A, et une partie du Bloc 2).

**Orientation stratégique retenue** : maintenir un SI à coût et à complexité opérationnelle minimaux (développeur unique, hébergement à trafic modéré), plutôt que de viser une architecture dimensionnée pour une montée en charge hypothétique — cohérent avec l'absence d'enjeu commercial réel à ce stade. Cette orientation a concrètement guidé des décisions récentes : arrêt d'Elasticsearch en production (juillet 2026) faute de trafic justifiant son coût, limite mémoire applicative resserrée sur la base de métriques observées plutôt que sur un plan de croissance.

---

## 2. Diagnostic du SI actuel

### 2.1 Périmètre fonctionnel

Application web (Spring Boot + Thymeleaf) à 4 profils (visiteur anonyme, client, contrôleur d'hygiène, administrateur), organisée autour de trois bases de données aux rôles disjoints (MongoDB pour les données restaurant, PostgreSQL pour les données utilisateur, Redis pour le cache) et d'une synchronisation nocturne automatisée depuis une source de données publique externe (NYC Open Data). Détail complet : [`bloc2-1-cahier-des-charges-techniques.md`](bloc2-1-cahier-des-charges-techniques.md).

### 2.2 Maturité par domaine

| Domaine | Maturité | Constat |
|---|---|---|
| **Fonctionnel** | Élevée | 4 profils utilisateurs opérationnels, périmètre stable depuis mai 2026 |
| **Sécurité** | Élevée | JWT en cookies httpOnly, rate limiting, scan de secrets en CI, conformité RGPD partielle (voir 2.4) — détail : [`bloc4a-1-analyse-risques.md`](bloc4a-1-analyse-risques.md) |
| **Qualité / tests** | Élevée | CI à 5 vérifications obligatoires avant fusion, couverture JaCoCo suivie — détail : [`bloc3-2-dossier-technique.md`](bloc3-2-dossier-technique.md) |
| **Gouvernance de projet** | Faible historiquement, en progression | Aucune note de cadrage ni planification formelle n'existait avant juillet 2026 (Blocs 2.2/2.3) — reconstruites a posteriori, pas pilotées a priori |
| **Résilience opérationnelle** | Moyenne | Sauvegardes PostgreSQL chiffrées quotidiennes (depuis le 23/07/2026) ; MongoDB sans sauvegarde dédiée mais entièrement re-dérivable de la source NYC Open Data — choix assumé, pas un oubli |
| **Documentation** | Élevée, avec dérive résiduelle constatée | `docs/` et `CLAUDE.md` mis à jour à chaque fonctionnalité livrée ; une référence obsolète (`map.html` au lieu de `inspection-map.html`) a néanmoins été trouvée et corrigée le 29/07/2026 en préparant ce dossier — signe que le contrôle documentaire, bien qu'en place, n'est pas infaillible |

### 2.3 Dépendances externes structurantes

| Dépendance | Rôle | Niveau de maîtrise |
|---|---|---|
| API NYC Open Data | Source unique des données restaurant | Faible — SLA informel, pas de fournisseur alternatif identifié |
| Railway (hébergement applicatif + PostgreSQL) | Exécution et déploiement continu | Moyen — migration possible mais non testée |
| MongoDB Atlas | Hébergement des données restaurant | Moyen — re-synchronisable entièrement depuis la source, donc portable |
| Supabase | Hébergement PostgreSQL, sauvegardes | Moyen |
| Resend | Envoi d'emails transactionnels (reset de mot de passe) | Faible — fonctionnalité dégradée proprement en cas de panne, non bloquante pour le cœur de service |

---

## 3. Processus améliorables

| Processus actuel | Limite constatée | Amélioration envisageable |
|---|---|---|
| Revue de code | Auto-portée (développeur unique) | Compensée par la CI obligatoire, mais aucun regard humain tiers — acceptable en solo, redevient un vrai risque si l'équipe grandit |
| Suivi de projet | Reconstruit a posteriori depuis `CHANGELOG.md` plutôt que planifié a priori (cf. Bloc 2.3) | Adopter une planification légère mais réelle (même a minima) pour les évolutions futures, plutôt que de continuer à documenter après coup |
| Contrôle de cohérence documentation/code | Convention déclarée mais non outillée (pas de vérification automatique) | Un test ou un job CI qui détecte les références de fichiers obsolètes dans `docs/` réduirait la dérive constatée en 2.2 |
| Décision de coût d'infrastructure | Ponctuelle, réactive (ex. arrêt d'Elasticsearch décidé après observation de métriques) | Revue périodique planifiée des coûts d'hébergement plutôt qu'une réaction a posteriori |

---

## 4. Cohérence avec les obligations réglementaires (RGPD)

| Obligation RGPD | État | Preuve |
|---|---|---|
| Droit à l'effacement | Conforme | `DELETE /api/users/me` (vérifié dans `UserController.java:162`) — cascade rapports (+ photos), favoris ; anonymise les entrées d'audit log existantes (`actorUsername` → `[deleted-user]`) plutôt que de les supprimer, préservant la traçabilité tout en respectant l'effacement |
| Minimisation des données | Conforme | Seules les données strictement nécessaires sont stockées côté utilisateur (identifiants, rôle, favoris, rapports) — aucune donnée personnelle sensible collectée |
| Sécurité des données (art. 32) | Conforme | Mots de passe hashés BCrypt, JWT en cookies httpOnly, sauvegardes PostgreSQL chiffrées (GPG AES256, confirmé dans `.github/workflows/backup-postgres.yml`) |
| Traçabilité des traitements | Conforme | `audit_log` journalise les actions sensibles (suppression de compte, actions admin) |
| Registre des traitements / DPO | Non applicable formellement | Projet académique/solo sans personnalité morale distincte — non exigé à ce stade, à revoir si le SI change de nature (commercialisation) |

**Cohérence avec les orientations stratégiques (section 1)** : l'effort de conformité RGPD engagé en juillet 2026 (v2.3.0-2.4.0) est directement aligné avec l'enjeu de certification (le Bloc 4A évalue explicitement la gestion des risques SI) — la priorisation observée dans le temps (sécurité et RGPD traités juste avant la phase de rédaction certificative) n'est pas une coïncidence mais un choix cohérent avec les enjeux du projet.

---

## 5. Priorités et évolutions préconisées

Risques stratégiques évalués (distincts des risques techniques/sécurité déjà cartographiés au Bloc 4A.1, R1-R10) :

| # | Risque stratégique | Probabilité (1-4) | Impact (1-4) | Score | Montant estimé | Priorité |
|---|---|---|---|---|---|---|
| S1 | **Key-person risk** — développeur unique, aucune continuité documentée en cas d'indisponibilité prolongée | 2 | 4 | 8 | Coût de reprise en main par un tiers : plusieurs jours, atténué par `docs/` et `CLAUDE.md` à jour | **Haute** |
| S2 | **Dépendance à une source de données non contractualisée** (NYC Open Data) — panne ou changement de schéma sans préavis | 2 | 3 | 6 | Interruption du service de synchronisation ; pas de coût direct (source gratuite) mais risque de dégradation silencieuse des données | Moyenne |
| S3 | **Dérive de coût d'hébergement** si le trafic ou le périmètre augmentent sans revue | 2 | 2 | 4 | ~20-30 $/mois actuellement (Railway) ; dérive possible mais détectable via les métriques déjà suivies | Moyenne |
| S4 | **Dette de gouvernance** — absence de cadrage/planification a priori, gérable en solo mais non réplicable telle quelle en contexte d'équipe | 3 | 2 | 6 | Coût de transmission/formation si le projet passe en équipe (cf. question en cours sur le Bloc 2.4) | Moyenne |
| S5 | **Non-conformité RGPD résiduelle** en cas de commercialisation (registre de traitement, DPO) | 1 | 3 | 3 | Sanction potentielle si le SI change d'échelle sans mise en conformité renforcée | Faible à ce stade |

**Évolutions préconisées, par ordre de priorité :**

1. **Documenter une procédure de reprise en main** (S1) — un guide "si je disparais demain" au-delà de `CLAUDE.md`/`docs/` actuels : où sont les secrets, comment redéployer, qui contacter pour chaque service tiers.
2. **Ajouter un fallback ou une alerte sur la source NYC Open Data** (S2) — actuellement non traité (R7 du Bloc 4A.1) ; a minima, une alerte si le sync nocturne échoue plusieurs jours de suite.
3. **Formaliser une revue trimestrielle de coût d'infrastructure** (S3) plutôt que des ajustements réactifs.
4. **Clarifier le statut de gouvernance** (S4) avant toute extension du projet à une équipe — trancher la question déjà posée à Aflokkat sur le périmètre du Bloc 2.4 (projet solo vs projet de groupe) en est un prérequis direct.
5. **Revisiter la conformité RGPD** (S5) uniquement si et quand le SI change d'échelle — non urgent, mais à ne pas oublier.

---

## Renvoi croisé

- Cahier des charges techniques : [`bloc2-1-cahier-des-charges-techniques.md`](bloc2-1-cahier-des-charges-techniques.md)
- Analyse des risques SI (sécurité, technique) : [`bloc4a-1-analyse-risques.md`](bloc4a-1-analyse-risques.md)
- Dossier de planification + bilan : [`bloc2-3-dossier-planification-bilan.md`](bloc2-3-dossier-planification-bilan.md)
