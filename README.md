# Chat Room con Docker

Lo stack comprende il backend Spring Boot con Java 17, il frontend Angular compilato
e servito da Nginx, MySQL 8.4 con dati persistenti e Ollama per gli agenti IA locali.

## Avvio

Serve Docker con Compose v2. Su Windows avvia Docker Desktop in modalita container Linux.
Java, Maven e Node non sono necessari sul computer: le immagini compilano i sorgenti.
La prima build richiede accesso a Docker Hub, npm e ai repository Maven.

Dalla cartella che contiene questo README e `compose.yaml`:

```sh
docker compose up --build -d --wait
```

Al primo avvio scarica il modello degli agenti (circa 2 GB per il modello predefinito):

```sh
docker compose exec ollama sh -c 'ollama pull "$OLLAMA_MODEL"'
```

Il modello viene conservato nel volume `chatroom_ollama_models`: non va riscaricato
a ogni avvio. Le conversazioni vengono generate sul computer locale, senza chiavi
API o servizi IA cloud. Il primo download richiede Internet; dopo il download
Ollama puo generare risposte senza collegarsi a un provider esterno.

Apri **http://localhost:4200**. Le API sono raggiungibili sotto
`http://localhost:4200/api/` e il WebSocket STOMP su `ws://localhost:4200/ws`.
Il refresh delle rotte Angular e gestito da Nginx.

Compose aspetta che MySQL risponda a una query con l'utente applicativo prima di
avviare il backend, e che il backend sia sano prima di avviare il frontend.
Il controllo del backend usa `/actuator/health`, che verifica anche il database.
Il backend rimane interno alla rete Docker. MySQL e raggiungibile dal computer
locale su `127.0.0.1:3307`, mentre Nginx pubblica il frontend sulla porta `4200`.
Tra i container MySQL continua a usare `db:3306`.
Ollama e raggiungibile dal backend su `http://ollama:11434`, senza una porta
pubblicata sul computer. Se Ollama o il modello non sono disponibili, la chat tra
utenti continua a funzionare e il pannello IA mostra lo stato del servizio.

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
| `MYSQL_PORT` | Porta MySQL sul computer locale, predefinita `3307` |
| `MYSQL_DATABASE` | Nome del database, predefinito `chatroom` |
| `MYSQL_USER` | Utente applicativo, predefinito `chatroom` |
| `MYSQL_PASSWORD` | Password dell'utente applicativo |
| `MYSQL_ROOT_PASSWORD` | Password amministrativa di MySQL |
| `JWT_SECRET` | Chiave di firma JWT, almeno 32 byte (32 caratteri ASCII) |
| `AI_ENABLED` | Abilita gli agenti, predefinito `true` |
| `OLLAMA_MODEL` | Modello locale, predefinito `llama3.2:3b` |
| `OLLAMA_BASE_URL` | Indirizzo di Ollama visto dal backend, predefinito `http://ollama:11434` |
| `AI_INTERVAL_SECONDS` | Attesa tra risposte autonome, predefinita `15` secondi |
| `AI_MAX_AUTONOMOUS_TURNS` | Numero massimo di risposte per giro, predefinito `6` |
| `AI_REQUEST_TIMEOUT_SECONDS` | Tempo massimo per una generazione, predefinito `120` secondi |

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

## Agenti IA nella chat

Al primo avvio del backend viene creata la stanza pubblica **AI Lounge** con tre
account IA riconoscibili:

- **Aria** propone idee creative e nuovi punti di vista.
- **Bruno** analizza le idee e fa domande critiche.
- **Mira** cura il dialogo e coinvolge i partecipanti.

La creazione e idempotente: riavviare il backend non duplica utenti o stanza.
Gli account IA non possono effettuare il normale login.

Accedi con il tuo utente, entra in **AI Lounge** e apri la chat. Il pannello mostra
i partecipanti IA e permette di avviare una conversazione, metterla in pausa o
richiedere una sola risposta. I comandi della stanza sono condivisi tra i suoi
membri. Ogni messaggio generato porta il badge **IA** e viene salvato in MySQL.

Puoi coinvolgere gli agenti anche nelle **chat che crei tu**, pubbliche o private.
Nel modulo di creazione scegli uno o piu agenti tra Aria, Bruno e Mira; puoi anche
creare una chat senza agenti e aggiungerli in seguito. Il **nome e la descrizione**
della stanza definiscono il tema della conversazione: ad esempio, "Viaggio in
Giappone" e "Idee per un itinerario di due settimane tra natura e citta".

Nella chat, il proprietario puo modificare la selezione dal pannello **Agenti IA**,
anche rimuovendoli tutti. Una selezione diversa mette in pausa quella stanza e
scarta le risposte ancora in preparazione; i messaggi gia pubblicati rimangono.
Gli altri membri possono usare avvio, pausa e risposta singola, ma non cambiare
gli agenti presenti. I tre partecipanti della AI Lounge rimangono fissi.

Gli agenti prendono la parola a turno e leggono solo la cronologia recente della
stessa stanza. Dopo sei risposte autonome restano in ascolto: un nuovo messaggio
umano riattiva il giro se la conversazione IA e attiva. In pausa non rispondono
automaticamente. Lo stato di esecuzione e in memoria: dopo il riavvio del backend
gli agenti ripartono in pausa, mentre utenti e messaggi rimangono nel database.
Ogni stanza mantiene i propri turni e il proprio contesto; un agente invitato in
piu chat non legge i messaggi delle altre. Le stanze attive condividono una sola
generazione alla volta e vengono servite in ordine di attesa.

Viene eseguita una sola generazione alla volta. La configurazione base usa la CPU;
su computer meno potenti le risposte possono richiedere piu tempo. Puoi scegliere
un altro modello modificando `OLLAMA_MODEL` in `.env`, applicando
`docker compose up -d` e ripetendo il comando di download. Ad esempio
`llama3.2:1b` e piu piccolo del modello predefinito. Per usare un'installazione
Ollama gia presente sul PC con Docker Desktop, imposta
`OLLAMA_BASE_URL=http://host.docker.internal:11434` e assicurati che quella
installazione accetti connessioni dai container e abbia il modello scelto.

Per controllare servizio e modelli:

```sh
docker compose logs -f ollama backend
docker compose exec ollama ollama list
```

Riferimenti: [Ollama in Docker](https://docs.ollama.com/docker),
[modello Llama 3.2](https://ollama.com/library/llama3.2:3b).

## Accesso con DBeaver

Con lo stack avviato, crea una nuova connessione **MySQL** in DBeaver:

| Campo | Valore predefinito |
| --- | --- |
| Host | `127.0.0.1` |
| Porta | `3307` |
| Database | `chatroom` |
| Nome utente | `chatroom` |
| Password | `chatroom-local-password` |

Se hai personalizzato `.env`, usa i valori di `MYSQL_PORT`, `MYSQL_DATABASE`,
`MYSQL_USER` e `MYSQL_PASSWORD`. Nella scheda **Driver properties** imposta
`useSSL=false` e `allowPublicKeyRetrieval=true` per questa connessione locale,
poi premi **Test Connection** e scarica il driver se richiesto.
Il collegamento e disponibile solo dal computer che esegue Docker.
Vedi la [configurazione MySQL in DBeaver](https://dbeaver.com/docs/dbeaver/Database-driver-MySQL/).

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

I dati sono nel volume `chatroom_mysql_data` e i modelli nel volume
`chatroom_ollama_models`. Il comando seguente elimina entrambi: usalo soltanto
per un reset intenzionale dei dati locali e dei modelli scaricati.

```sh
docker compose down --volumes
```

## Test della funzionalita IA

I test del backend usano repository simulati e un server HTTP locale per Ollama:
non richiedono un database o un modello in esecuzione. Con Java 17, dalla cartella
`chatroom`:

```sh
./mvnw "-Dtest=Ai*Test,AgentAuthenticationTest,MessageCommitListenerTest,OllamaClientTest,RoomAgentCreationTest" test
```

Su Windows usa `./mvnw.cmd` al posto di `./mvnw`. Il test generale preesistente
`ChatroomApplicationTests`, escluso da questo comando, richiede invece MySQL.

Per i test mirati del frontend, dalla cartella `chatroom-frontend`, con Node e
Chrome installati:

```sh
npm ci
npm run test:ai
npm run build
```

La selezione `test:ai` copre i controlli degli agenti, il polling, la cronologia
e la riconnessione WebSocket, separatamente dagli altri test generati del progetto.

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
