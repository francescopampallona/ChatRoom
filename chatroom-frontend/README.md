# ChatroomFrontend

## Docker

Dalla cartella principale `CHAT_ROOM`, avvia l'intero progetto seguendo il
[`README.md`](../README.md). Il frontend viene compilato in produzione e servito
da Nginx; le richieste `/api/` e la connessione WebSocket `/ws` vengono inoltrate
al servizio `backend:8080` sulla rete Docker.

La configurazione di produzione usa l'indirizzo del sito aperto nel browser, mentre
`npm start` continua a utilizzare il backend locale `http://localhost:8080`.

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.9.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

Per i test mirati della conversazione IA, del recupero dei messaggi e della
riconnessione WebSocket (richiede Chrome):

```bash
npm run test:ai
```

Questa suite usa `tsconfig.ai-spec.json` per escludere gli altri test generati
inizialmente dal progetto, alcuni dei quali importano nomi di componenti o servizi
che non esistono più.

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
