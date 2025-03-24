
cd docker
docker-compose up -d
docker exec -i abtact-app psql -U postgres -t < ../aa-application/resources/sql/aa-application.sql
