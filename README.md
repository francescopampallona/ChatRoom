# Chat Room con Docker

Lo stack comprende il backend Spring Boot con Java 17, il frontend Angular compilato
e servito da Nginx, e MySQL 8.4 con dati persistenti.

## Avvio

Serve Docker con Compose v2. Su Windows avvia Docker Desktop in modalita container Linux.
Java, Maven e Node non sono necessari sul computer: le immagini compilano i sorgenti.
La prima build richiede accesso a Docker Hub, npm e ai repository Maven.

Dalla cartella che contiene questo README e `compose.yaml`:

```sh
docker compose up --build -d --wait
```

Apri **http://localhost:4200**. Le API sono raggiungibili sotto
`http://localhost:4200/api/` e il WebSocket STOMP su `ws://localhost:4200/ws`.
Il refresh delle rotte Angular e gestito da Nginx.

Compose aspetta che MySQL risponda a una query con l'utente applicativo prima di
avviare il backend, e che il backend sia sano prima di avviare il frontend.
Il controllo del backend usa `/actuator/health`, che verifica anche il database.
Le porte di MySQL e del backend rimangono interne alla rete Docker; l'unica porta
pubblicata e quella di Nginx.

Il database viene creato al primo avvio e Hibernate crea/aggiorna le tabelle usando
la configurazione `ddl-auto=update` gia presente nel progetto. Non occorre eseguire
manualmente `chatroom/DATABASE.md`.

## Configurazione

Lo stack funziona subito con valori predefiniti per lo sviluppo locale.
Per personalizzarli, copia `.env.example` in `.env` accanto a `compose.yaml`.
In PowerShell:

```powershell
Copy-Item .env.example .env
```

| Variabile | Uso |
| --- | --- |
| `FRONTEND_PORT` | Porta pubblica, predefinita `4200` |
| `MYSQL_DATABASE` | Nome del database, predefinito `chatroom` |
| `MYSQL_USER` | Utente applicativo, predefinito `chatroom` |
| `MYSQL_PASSWORD` | Password dell'utente applicativo |
| `MYSQL_ROOT_PASSWORD` | Password amministrativa di MySQL |
| `JWT_SECRET` | Chiave di firma JWT, almeno 32 byte (32 caratteri ASCII) |

Usa password e chiave JWT private prima di distribuire lo stack su altri ambienti.
`.env` e escluso da Git e dai contesti di build; le credenziali vengono passate al
backend in esecuzione. Dopo una modifica applica nuovamente `docker compose up -d`.

Le variabili MySQL inizializzano solo un volume vuoto: cambiarle in `.env` non
modifica utenti o password di un database gia creato. Per conservare i dati, aggiorna
prima le credenziali nel database e poi la configurazione.

La porta pubblica puo cambiare senza ricompilare Angular: il frontend usa URL
relativi per le API e il protocollo della pagina per scegliere `ws` o `wss`.
Lo stack serve HTTP; un eventuale proxy HTTPS esterno richiede anche la
configurazione coerente degli header inoltrati in Nginx.

## Gestione

```sh
# Stato e log
docker compose ps
docker compose logs -f

# Ricompila dopo modifiche ai sorgenti
docker compose up --build -d --wait

# Verifica la configurazione
docker compose config --quiet

# Healthcheck del backend dall'interno della rete Docker
docker compose exec backend curl --fail http://localhost:8080/actuator/health

# Arresta e rimuove i container, mantenendo i dati
docker compose down
```

I dati sono nel volume `chatroom_mysql_data`. Il comando seguente elimina anche
il database: usalo soltanto per un reset intenzionale dei dati locali.

```sh
docker compose down --volumes
```

## Build e sviluppo

- `chatroom/Dockerfile`: build Maven e runtime JRE 17 con utente non privilegiato.
  Il test Spring esistente richiede MySQL: nella build immagine viene compilato ma
  non eseguito (`-DskipTests`).
- `chatroom-frontend/Dockerfile`: `npm ci`, build Angular production e runtime Nginx.
- `chatroom-frontend/nginx.conf`: inoltro `/api/` e `/ws` a `backend:8080`,
  supporto WebSocket e fallback per le rotte Angular.
- `application-docker.properties`: connessione a MySQL tramite il nome del
  servizio `db`, credenziali da ambiente e gestione degli header del proxy.

Lo sviluppo locale con `npm start` conserva la connessione a `localhost:8080`.
La configurazione Angular production usa invece lo stesso indirizzo del frontend.
La versione Spring Boot del progetto rimane quella indicata nel `pom.xml`;
essendo una snapshot, la build dipende anche dalla disponibilita del repository
Spring snapshot.

Riferimenti: [ordine di avvio in Compose](https://docs.docker.com/compose/how-tos/startup-order/),
[immagine MySQL](https://hub.docker.com/_/mysql),
[health endpoint Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).
