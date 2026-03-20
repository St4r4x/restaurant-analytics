# Améliorations identifiées

Audit du 2026-03-19. Classées par priorité.

---

## Bugs

### 1. `.env` jamais lu — `AppConfig`

**Fichier** : `src/main/java/com/aflokkat/config/AppConfig.java:18`

L'objet `Dotenv` est créé mais jamais utilisé. En dotenv-java v3.x, les variables du `.env` ne sont **pas** injectées dans `System.getenv()` automatiquement — il faut appeler `dotenv.get("KEY")` explicitement. Résultat : le fichier `.env` est entièrement ignoré au runtime.

L'app fonctionne uniquement grâce aux fallbacks de `application.properties` et aux variables Docker Compose. Lancer l'app localement sans Docker avec un `.env` personnalisé ne fonctionnerait pas.

**Fix** : utiliser `dotenv.get(key, defaultValue)` dans `getProperty()`, ou ajouter `.systemProperties()` au chargement pour injecter dans les system properties.

```java
// Option la plus simple
Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
// puis dans getProperty() :
String envValue = dotenv.get(key.replace(".", "_").toUpperCase(), null);
```

---

### 2. `Address.toString()` produit `"null null, null"`

**Fichier** : `src/main/java/com/aflokkat/domain/Address.java:36`

Si `building`, `street` ou `zipcode` est null (cas où l'adresse MongoDB est incomplète), la méthode retourne une string avec le mot littéral "null".

**Fix** :
```java
return Stream.of(building, street, zipcode)
    .filter(Objects::nonNull)
    .collect(Collectors.joining(" "));
```

---

### 3. Trois classes mortes dans `aggregation/`

**Fichiers** :
- `src/main/java/com/aflokkat/aggregation/BoroughCount.java`
- `src/main/java/com/aflokkat/aggregation/AggregationId.java`
- `src/main/java/com/aflokkat/aggregation/RestaurantAggregation.java`

Ces trois classes ne sont référencées nulle part dans le code applicatif ni dans les tests. Elles sont des doublons de `AggregationCount` et `BoroughCuisineScore`.

**Fix** : supprimer les trois fichiers.

---

## Qualité du code

### 4. N+1 queries dans `getTrashAdvisorRestaurants`

**Fichier** : `src/main/java/com/aflokkat/service/RestaurantService.java:98`

Pour `limit=5`, le service exécute **6 requêtes MongoDB** :
1. une agrégation pour trouver les 5 pires cuisines
2. une requête `find` par cuisine pour récupérer les restaurants

**Fix** : remplacer par une seule pipeline d'agrégation MongoDB qui fait le `$match` borough + `$unwind grades` + calcul du score moyen + `$lookup` (ou un `$group` final). Alternatif plus simple : un seul `findWithFilters` avec `$in` sur la liste de cuisines.

```java
// Récupérer les noms des pires cuisines
List<String> cuisineNames = worstCuisines.stream()
    .map(CuisineScore::getCuisine)
    .collect(Collectors.toList());

// Une seule requête avec $in
Document filter = new Document("borough", borough)
    .append("cuisine", new Document("$in", cuisineNames));
```

---

### 5. Toutes les erreurs API retournent HTTP 500

**Fichier** : `src/main/java/com/aflokkat/controller/RestaurantController.java:154`

Les erreurs de validation (`IllegalArgumentException`) retournent 500 comme les vraies erreurs serveur. Les clients ne peuvent pas distinguer "mauvais paramètre" de "crash serveur".

**Fix** : différencier les types d'exception dans le handler.

```java
private ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
    int status = (e instanceof IllegalArgumentException) ? 400 : 500;
    Map<String, Object> response = new HashMap<>();
    response.put("status", "error");
    response.put("message", e.getMessage());
    return ResponseEntity.status(status).body(response);
}
```

---

### 6. `info-box` div inutilisé dans Trash Advisor

**Fichier** : `src/main/resources/templates/trash-advisor.html:304`

Le div `<div class="info-box" id="info-box">` est en `display:none` et n'est jamais activé dans le JS. La class CSS `.info-box` est aussi orpheline.

**Fix** : supprimer le div et la règle CSS `.info-box`.

---

### 7. CSS `.control-group input` orphelin dans Trash Advisor

**Fichier** : `src/main/resources/templates/trash-advisor.html:99`

Après la suppression du champ `<input type="number">`, la règle `.control-group input` dans le CSS ne s'applique plus à aucun élément.

**Fix** : retirer `input` de la règle `.control-group select, .control-group input`.

---

## Architecture / évolution future

### 8. `MongoClientFactory` : pattern de synchronisation mixte

**Fichier** : `src/main/java/com/aflokkat/config/MongoClientFactory.java`

Le champ `mongoClient` est déclaré `volatile` mais `getInstance()` est `synchronized`. Les deux approches (double-checked locking avec volatile, ou méthode synchronized simple) sont valides séparément — les mélanger est inutile mais inoffensif.

**Fix** : soit retirer `volatile` (la méthode est `synchronized`), soit implémenter correctement le double-checked locking pour éviter le verrou à chaque appel.

---

### 9. `RestaurantService` instancie le DAO directement

**Fichier** : `src/main/java/com/aflokkat/service/RestaurantService.java:23`

`new RestaurantDAOImpl()` dans le constructeur empêche de mocker le DAO dans les tests unitaires du service. C'est pourquoi il n'existe pas de vrais tests unitaires du service.

**Fix** : injecter le DAO via le constructeur (injection de dépendance) ou utiliser `@Autowired` avec Spring.

```java
@Autowired
public RestaurantService(RestaurantDAO restaurantDAO) {
    this.restaurantDAO = restaurantDAO;
}
```

---

*3 bugs · 4 issues qualité · 2 points architecture*
