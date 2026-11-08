# User Stories

## 📌 Convenzioni
- ID: US-XXX
- Priorità: HIGH / MEDIUM / LOW
- Stato: TODO / IN_PROGRESS / DONE

---

## 🔐 Autenticazione

### US-001 — Registrazione utente
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come nuovo utente  
voglio registrarmi con username, email e password  
così da poter accedere alla piattaforma.

**Criteri di accettazione:**
- Username univoco
- Email univoca
- Password salvata cifrata (BCrypt)
- Validazione input lato backend
- Risposta di errore se utente già esiste

---

### US-002 — Login utente
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come utente registrato  
voglio effettuare il login  
così da ottenere un token JWT.

**Criteri di accettazione:**
- Login con email e password
- JWT restituito se corretto
- Errore 401 se credenziali errate
- Token usato nelle chiamate successive

---

## 🧑‍🤝‍🧑 Gestione Room

### US-003 — Creazione room
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come utente autenticato  
voglio creare una room pubblica o privata  
così da poter comunicare con altri utenti.

**Criteri di accettazione:**
- Nome obbligatorio
- Tipo PUBLIC o PRIVATE
- Creatore = OWNER
- Inserimento automatico in room_members

---

### US-004 — Join room pubblica
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come utente  
voglio entrare in una room pubblica  
così da partecipare alla chat.

**Criteri di accettazione:**
- Solo per room PUBLIC
- Inserimento in room_members
- No duplicati

---

### US-005 — Invito utente a room privata
**Priorità:** MEDIUM  
**Stato:** TODO

**Descrizione:**  
Come OWNER o ADMIN  
voglio invitare un utente  
così da permettergli di accedere alla room privata.

**Criteri di accettazione:**
- Solo OWNER/ADMIN possono invitare
- Creazione record in room_invites
- Stato iniziale: PENDING

---

### US-006 — Accettazione invito
**Priorità:** MEDIUM  
**Stato:** TODO

**Descrizione:**  
Come utente invitato  
voglio accettare un invito  
così da entrare nella room.

**Criteri di accettazione:**
- Stato → ACCEPTED
- Inserimento in room_members
- Invito non riutilizzabile

---

## 💬 Chat

### US-007 — Invio messaggi
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come utente membro di una room  
voglio inviare messaggi  
così da comunicare con gli altri.

**Criteri di accettazione:**
- Solo membri della room possono inviare
- Messaggio salvato su DB
- Messaggio inviato via WebSocket

---

### US-008 — Ricezione messaggi realtime
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come utente  
voglio ricevere messaggi in tempo reale  
così da vedere subito le conversazioni.

**Criteri di accettazione:**
- Sottoscrizione a /topic/rooms/{id}
- Messaggi ricevuti senza refresh
- Riconnessione automatica

---

### US-009 — Storico messaggi
**Priorità:** HIGH  
**Stato:** TODO

**Descrizione:**  
Come utente  
voglio vedere lo storico dei messaggi  
così da recuperare le conversazioni passate.

**Criteri di accettazione:**
- Endpoint REST
- Paginazione (es: 20 messaggi)
- Ordinamento per data

---
### US-010 — Visualizzazione membri della room

**Priorità:** HIGH

**Descrizione:**
Come utente membro di una room voglio visualizzare l'elenco dei membri così da sapere chi partecipa alla conversazione.

**Criteri di accettazione:**

Solo i membri della room possono vedere l'elenco.
Visualizzazione di username e ruolo (OWNER, ADMIN, MEMBER).
Elenco ordinato per ruolo e poi username.
---

## 🔒 Sicurezza

### US-010 — Autorizzazione accesso room
**Priorità:** CRITICA  
**Stato:** TODO

**Descrizione:**  
Come sistema  
voglio verificare che un utente sia membro della room  
così da proteggere i messaggi.

**Criteri di accettazione:**
- Controllo su ogni invio messaggio
- Controllo su ogni accesso storico
- Errore 403 se non autorizzato

---

## 🚀 Futuro (non MVP)

### US-011 — Utenti online
### US-012 — Typing indicator
### US-013 — Notifiche
### US-014 — IA (riassunti, suggerimenti)