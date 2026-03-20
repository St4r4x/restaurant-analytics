# 🍽️ Restaurant Analytics - Application Complète

Une application complète pour analyser les données MongoDB des restaurants de New York avec une **API REST** et un **dashboard web moderne**.

---

## 📋 Caractéristiques

✅ **API REST** - 4 use cases + statistiques  
✅ **Dashboard interactif** - Interface élégante et réactive  
✅ **Export données** - CSV et JSON  
✅ **Docker & Docker Compose** - Déploiement facile  
✅ **Architecture propre** - Couches bien organisées

---

## 🚀 Démarrage rapide

### Option 1 : Docker Compose (Recommandé) 🐳

```bash
# 1. Assurer que MongoDB est en place
# (Si MongoDB n'existe pas déjà) :
docker-compose down  # nettoyer les conteneurs précédents

# 2. Démarrer l'application
docker-compose up -d

# 3. Attendre que les services démarrent (~30s)
sleep 10

# 4. Ouvrir dans le navigateur
http://localhost:8080
```

**Status**:

- MongoDB: http://localhost:27017
- Application: http://localhost:8080

### Option 2 : Lancer le JAR directement

```bash
# Préalable: MongoDB en local sur localhost:27017

# 1. Compiler et packager
mvn clean package -DskipTests

# 2. Lancer l'application
java -jar target/quickstart-app-1.0-SNAPSHOT.jar

# 3. Accéder via navigateur
http://localhost:8080
```

### Option 3 : Maven direct

```bash
# Compiler et lancer directement
mvn clean spring-boot:run
```

---

## 📡 Endpoints API

### 1. Restaurants par Quartier (USE CASE 1)

```bash
curl http://localhost:8080/api/restaurants/by-borough
```

**Réponse** :

```json
{
  "status": "success",
  "data": [
    { "id": "Manhattan", "count": 10259 },
    { "id": "Queens", "count": 6330 }
  ],
  "count": 5
}
```

### 2. Scores Cuisines/Quartiers (USE CASE 2)

```bash
curl "http://localhost:8080/api/restaurants/cuisine-scores?cuisine=Italian"
```

**Réponse** :

```json
{
  "status": "success",
  "cuisine": "Italian",
  "data": [
    { "borough": "Manhattan", "avgScore": 12.5 },
    { "borough": "Queens", "avgScore": 11.8 }
  ]
}
```

### 3. Pires Cuisines (USE CASE 3)

```bash
curl "http://localhost:8080/api/restaurants/worst-cuisines?borough=Manhattan&limit=5"
```

### 4. Cuisines Populaires (USE CASE 4)

```bash
curl "http://localhost:8080/api/restaurants/popular-cuisines?minCount=500"
```

### 5. Statistiques Globales

```bash
curl http://localhost:8080/api/restaurants/stats
```

### 6. Health Check

```bash
curl http://localhost:8080/api/restaurants/health
```

---

## 🎨 Interface Web

### Fonctionnalités du Dashboard

1. **Restaurants par Quartier**
   - Tableau avec comptage par quartier
   - Tri décroissant automatique
   - Export CSV/JSON

2. **Scores de Cuisine**
   - Entrée de cuisine personnalisée
   - Affichage des scores moyens
   - Comparaison par quartier

3. **Pires Cuisines**
   - Filtrage par quartier
   - Limite configurable
   - Classement du pire au meilleur

4. **Cuisines Populaires**
   - Seuil de minimum configurable
   - Tri alphabétique
   - Export simple

5. **Statistiques Globales**
   - Vue d'ensemble rapide
   - Cartes statistiques
   - Résumé des quartiers

---

## 📦 Structure Projet

```
src/
├── main/
│   ├── java/com/aflokkat/
│   │   ├── Application.java          # Point d'entrée Spring Boot
│   │   ├── aggregation/              # DTOs d'agrégation
│   │   ├── controller/                # REST API + Views
│   │   ├── dao/                      # Couche données
│   │   ├── domain/                   # Modèles (POJO)
│   │   ├── service/                  # Logique métier
│   │   ├── config/                   # Configuration
│   │   └── util/                     # Utilitaires
│   └── resources/
│       ├── templates/
│       │   └── index.html            # Dashboard UI
│       └── application.properties
└── test/                              # Tests unitaires
```

---

## 🔧 Configuration

### Via environment variables

```bash
export MONGO_URI=mongodb://localhost:27017
export MONGO_DB=newyork
export MONGO_COLLECTION=restaurants
```

### Via application.properties

```properties
mongodb.uri=mongodb://localhost:27017
mongodb.database=newyork
mongodb.collection=restaurants
```

### Docker Compose (.env)

Créé automatiquement dans docker-compose.yml

---

## 🐛 Troubleshooting

### Erreur : "Connection refused"

```bash
# Vérifier que MongoDB est lancé
docker ps | grep mongo

# Si absent, tous les services :
docker-compose up -d
```

### Erreur : "Database not found"

```bash
# Vérifier les données
mongosh localhost:27017/newyork
> db.restaurants.count()
```

### Port 8080 déjà utilisé

```bash
# Modifier docker-compose.yml ou application.properties
server.port=8081
```

### Lenteur au chargement

```bash
# Les indices aident les performances
# Vérifiez qu'ils sont créés :
mongosh localhost:27017/newyork
> db.restaurants.getIndexes()
```

---

## 📊 Exemples de Requêtes

### Avec cURL

```bash
# Tous les restaurants par quartier
curl -X GET "http://localhost:8080/api/restaurants/by-borough"

# Scores italiens
curl -X GET "http://localhost:8080/api/restaurants/cuisine-scores?cuisine=Chinese"

# Pires cuisines à Queens (top 3)
curl -X GET "http://localhost:8080/api/restaurants/worst-cuisines?borough=Queens&limit=3"

# Cuisines avec >1000 restaurants
curl -X GET "http://localhost:8080/api/restaurants/popular-cuisines?minCount=1000"
```

### Avec JavaScript (depuis le navigateur)

```javascript
// Charger les données
fetch("http://localhost:8080/api/restaurants/by-borough")
  .then((res) => res.json())
  .then((data) => console.log(data))
  .catch((err) => console.error(err));
```

---

## 🧪 Tests

```bash
# Lancer tous les tests
mvn test

# Tests spécifiques
mvn test -Dtest=RestaurantDAOIntegrationTest

# Tests avec couverture
mvn clean test jacoco:report
```

---

## 🐳 Docker

### Construire l'image

```bash
docker build -t restaurant-app:latest .
```

### Lancer le conteneur

```bash
docker run -p 8080:8080 \
  -e MONGO_URI=mongodb://mongo:27017 \
  restaurant-app:latest
```

### Avec Docker Compose (tout compris)

```bash
docker-compose up -d          # Démarrer
docker-compose logs -f app    # Voir les logs
docker-compose down           # Arrêter
```

---

## 📈 Performance

- **Requêtes optimisées** : Indices MongoDB automatiques
- **Caching** : En développement
- **Export rapide** : JSON/CSV natif
- **API responsive** : ~200ms par requête

---

## 🤝 Contribution

Pour améliorer l'application :

1. Créer une branche (`git checkout -b feature/amélioration`)
2. Commit les changements (`git commit -m "Amélioration X"`)
3. Push et Pull Request

---

## 📝 License

MIT - Libre d'utilisation

---

## ❓ FAQ

**Q: Comment charger les données?**  
A: Les données doivent être importées dans MongoDB en amont.

**Q: Peut-on modifier les requêtes?**  
A: Oui via RestaurantService.java et RestaurantDAO.java

**Q: Support de la pagination?**  
A: Oui, modifier le DAO pour ajouter skip/limit aux endpoints

**Q: Comment déplacer en prod?**  
A: Utiliser docker-compose avec variables d'env MongoDB prod

---

**Besoin d'aide?** Vérifier les logs :

```bash
docker-compose logs app -f
```

Bon analyses ! 🚀
