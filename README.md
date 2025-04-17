
# About Actors

- A mostly minimal example of Akka


## Intellij Setup

- Install Scala Plugin
- `File -> Project Structure -> Global Libraries`
- Download `scala-sdk-3.3.4`

## Pull Backend Dependencies
- `mvn install`

## Build Frontend

```bash
cd aa-application/src/main/about-actors-frontend
npm install
npm run build-dist
```

- NOTE:
  - you may need `npm install --force` until we get around to resolving dependency conflicts between angular and tailwind

## Run

- init docker
```bash
cd docker
docker-compose up
```

- establish the schema
```
docker exec -i abtact-app psql -U postgres -t < aa-application/resources/sql/aa-application.sql
```

- run `AboutActorsApplication::main`