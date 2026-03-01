---
type: note
goal: Humans take notes and share them
agents: AGENTS MUST IGNORE THIS FILE!
---

# Notes

## Impl

Persistance layer can be enhanced by PostgreSQL usage.

## The Talk

The idea is to create the talk on the go, when implementing the reference project with the 
reference Bounded Context.
 
### Loose Ideas

We need to clarify first 
- Classical Spring Boot application with Vaadin UI? Or just the Backend (would be enough) – IMHO: Let’s see how much time is left after the backend is done
  - Start with Spring Boot and REST-A
- Domain Driven Design 101
  - Application Services (aka the Use-Cases)
  - Domain Services (when one Aggregate is not enough)
  - Entities
  - Value Objects (and ID Value Objects)
  - Aggregates
  - Factories
  - Repositories
  - Domain Events (internal and external)
  - Commands 
- Ports-and-Adapters 101
  - Concept
  - Inbound
  - Outbound
  - Core 
  - Inport 
  - Outport
  - Model
  - Application
  - The wired Interface Thing 
  - Descisions for keeping the core clean
- The Reference Project by its bounded context
  - Add simple Marker-Interfaces to make the code easier to understand
  - Add package descriptions
- Implementation and placement for the various elements including tradeoffs
- Where to place validation rules?
- Domain Events (internal and external) as well as Commands
- Transaction-Handling
- Alignment with AI and SDD is done on the fly, simply by using SDD to implement the reference project
- The Artefacts should therefore be easily accessible and integrateable into the talk
- Remember that the talk is about 30 minutes, which means that there is no time for large excurses

### Possible Slides
- Domain Driven Design 101
  - Application Services (aka the Use-Cases)
  - Domain Services (when one Aggregate is not enough)
  - Entities
  - Value Objects (and ID Value Objects)
  - Aggregates
  - Factories
  - Repositories
  - Domain Events (internal and external)
  - Commands
- Ports-and-Adapters 101
  - Concept
    - Inbound
    - Outbound
    - Core
    - Inport
    - Outport
    - Model
    - Application
  - The wired Interface Thing
  - Decisions for keeping the core clean
- The Reference Project by its bounded context
- Model of the Reference Project
- Implementation and placement for the various elements including tradeoffs
- Where to place validation rules?
- Domain Events (internal and external) as well as Commands
- Transaction-Handling
- Alignment with AI and SDD is done on the fly, simply by using SDD to implement the reference project


### Slide Refinement

#### Overall Strategy

- Domain Driven Design
- Ports-and-Adapters (Hexagonal Architecture)

I focus on building vocabulary in the first half of the talk, and then apply it to the reference project, where
the action happens and the theory is explained in detail and showcased.

#### Slide Content

- Total Slide Count: 15–20 slides max
- Key Focus
  1. Show fewer diagrams, explain more meaning
  2. Use one running example
  3. Be opinionated
  4. Don’t teach everything, teach clarity
- Framing & Why This Matters (3 min)
  - Slides:
    - Title Slide
    - The Problem: "Why does architecture rot? Package discussions are a symptom of tight coupling and framework-driven design and thus rotting architectures!"
    - What will we build today?
  - Goals:
    - Tight coupling
    - Framework-driven design
    - Anemic models
    - Validation chaos
    - Transaction confusion
  - End with: "Let’s build a clean, reliable architecture step by step."
- Domain Driven Design 101 (7 min)
  - Slides in this section are conceptual and light.
  - 6–8 Slides only
  - Slides:
    - What is DDD really about?
    - Entities vs Value Objects
    - Aggregates
    - Domain Services
    - Repositories
    - Domain Events
    - Commands
  - Not covered:
    - Factories
    - ID Value Objects
    - Validation
    - Transactions
  - Goals:
    - Build vocabulary
    - Create a baseline everyone in the meeting can follow
- Ports-and-Adapters 101 (6 min)
  - Slides are high-level mental models only.
  - Slides:
    - The dependency rule (core must not depend on outside)
    - Inbound vs Outbound
    - Ports (inports/outports)
    - Core (Model + Application)
    - The wired interface thing
  - Keep it Simple:
    - Draw the hexagon
    - Show arrows
    - Show example: REST controller → inport → application → outport → repository adapter
  - Goals:
    - Build a high-level mental model of the architecture
    - Build a light understanding of the vocabulary
    - Create a baseline everyone in the meeting can follow
- The Reference Project (Main Part) (14 min)
  - Slides:
    - Bounded Context
      - What Problem does the system solve?
      - What is inside?
      - What is outside?
      - Why this boundary?
    - Model
      - Show:
        - Aggregates
        - Entities
        - Value Objects
        - Domain Events
        - Commands
      - Explain
        - Why this aggregate boundary?
        - Where do invariants live?
        - Where does validation live? (tease — deeper later)
    - Implementation & Placement
      - Show the folder / package structure:
        - core
        - application
        - model
        - adapters
        - inbound
        - outbound
      - Walk through:
        - Where commands live
        - Where domain events live
        - Where repositories live
        - Where validation rules live
        - Where transaction handling happens
        - Tradeoffs discussion happens here
    - Advanced Topics
      - Where to place validation rules?
        - Syntactic validation
          - Inbound Adapter & Factory
          - Aim for always valid models, exceptions only with good arguments
        - Business invariants
          - Aggregate holds business invariants
        - Cross-aggregate rules 
          - That's the core usage of domain services
        - External constraints
          - Application layer
      - Domain Events (internal vs external)
        - Internal for domain-level
        - External for integration layer mapping
        - Don't leak domain events outside
      - Transaction Handling
        - Application layer boundary
        - One aggregate per transaction (violation of this rule is rare, needs good choice, the best case: never)
        - Outbox pattern (Drop it! All events need proper handling, even the internal ones)
- AI & SDD Alignment (1 min)
  - Slides:
    - “When you structure your system this way, AI works better.” 
    - Why? 
      - Clear boundaries
      - Clear responsibilities
      - Isolated logic
      - Testable use cases
      - Refactorable domain 
    - Tie it back to:
      - Clean model 
      - Explicit commands 
      - Explicit events 
      - This is a powerful closing.


#### Content Schedule

| Time  | Section           |
|-------|-------------------|
| 0-3   | Framing           |
| 3-10  | DDD 101           |
| 10-16 | Ports & Adapters  |
| 16-30 | Reference Project |
| 30-33 | AI + Wrap-up      |
| 35-40 | Q&A               |


### Code und Slides 

Sehr gut. Jetzt kommen wir auf die eigentliche Storyline deines Vortrags:
Nicht Architektur erklären – sondern eine Business-Story sauber in Architektur übersetzen.

Du brauchst zwei Ebenen:
1.	Business Feature Story (für Kontext & Einstieg)
2.	Talk-Flow Version (architektonische Dramaturgie entlang dieser Story)

Ich strukturiere das so, dass du es direkt in deine Slides übernehmen kannst.

⸻

🏔 1. Business Feature Story (Outside-In)

Das ist deine Business-Narration. Keine Technik. Keine Aggregate. Keine Ports.

Alpine Tour Booking – Feature Story

“Ich möchte eine alpine Tour buchen können.
Wenn meine Buchung akzeptiert wird, soll sie bestätigt werden.
Wenn ich krank werde, möchte ich sie kündigen können.
Und am Tag der Tour soll der Guide die Tour starten können.”

Die vier Kernfähigkeiten
1.	Tour buchen
•	Ich wähle Tour + Datum
•	Ich gebe Teilnehmerzahl an
•	Ich erhalte eine Buchungs-ID
2.	Tour bestätigen
•	Meine Buchung wird verbindlich
•	Ich erhalte eine Bestätigung
3.	Tour kündigen
•	Ich kann stornieren
•	Ich kenne meinen Status
4.	Tour starten
•	Der Guide startet die Tour
•	Buchungen wechseln in aktiven Zustand

⸻

Business-Regeln (noch ohne Technik)
•	Eine Tour hat begrenzte Plätze
•	Eine Buchung hat einen Status
•	Status dürfen sich nur sinnvoll ändern
•	Man kann keine beendete Tour erneut starten
•	Man kann keine stornierte Tour starten
•	Eine Tour kann nur am richtigen Tag gestartet werden

Das ist die komplette Business-Story.

Alles andere ist Architektur.

⸻

🎤 2. Talk-Flow-Version (Architektur entlang der Story)

Jetzt kommt der dramaturgische Teil.

Du erzählst eine durchgehende Story und modellierst Schritt für Schritt.

⸻

🎬 Act 1 – “Ich möchte eine Tour buchen”

Slide: Business Statement

“Ich möchte eine Tour buchen.”

Du fragst ins Publikum:

Was bedeutet das fachlich?

Antwort:
•	Es entsteht etwas Neues.
•	Es gibt einen Status.
•	Es gibt Regeln.

⸻

Slide: Domain Modell entsteht

Du führst ein:
•	Aggregate: TourBooking
•	Value Objects: TourId, TourDate, ParticipantCount
•	Initialer Status: REQUESTED

Wichtig:

“Wo leben die Regeln? Nicht im Controller. Nicht im Service.
Im Aggregate.”

⸻

Slide: Use Case entsteht

RequestTourBooking

Flow:
1.	Input validieren (syntactic)
2.	Kapazität prüfen (Domain Service)
3.	Aggregate erzeugen
4.	Persistieren
5.	Domain Event emittieren

🎯 Hier erklärst du:
•	Validation Chaos vermeiden
•	Always-valid Modell
•	1 Use Case = 1 Transaktion

⸻

🎬 Act 2 – “Meine Buchung wird bestätigt”

Slide: Business Statement

“Eine gebuchte Tour soll bestätigt werden.”

Du fragst:

Was darf hier passieren?

Antwort:
•	Nur REQUESTED → CONFIRMED
•	Sonst Fehler

⸻

Slide: State Model

Du zeigst die Status-Transitions:

REQUESTED → CONFIRMED
REQUESTED → CANCELLED
CONFIRMED → CANCELLED
CONFIRMED → ACTIVE

Hier erklärst du:
•	Anämisches Modell vs echtes Modell
•	Status-Transitionen gehören ins Aggregate

⸻

Slide: Use Case ConfirmTourBooking

Flow:
1.	Load
2.	confirm()
3.	save
4.	event

🎯 Hier erklärst du:
•	Transaktion liegt im Use Case
•	Repository ist nur Port
•	Event nach Commit

⸻

🎬 Act 3 – “Ich werde krank”

Slide: Business Statement

“Wenn ich krank werde, muss ich kündigen können.”

Frage:

Wo gehört Storno-Logik hin?

Antwort:
•	Status-Regeln → Aggregate
•	Externe Regeln (z.B. Zahlungsrückerstattung) → Application Layer

Hier erklärst du:
•	Business Invariant vs External Constraint
•	Domain Service nur wenn cross-aggregate

⸻

Slide: CancelTourBooking

Du betonst:

“Kein Service-Orchestrierungs-Monster.
Nur: Load → Domain Method → Save.”

⸻

🎬 Act 4 – “Am Tag der Tour startet der Guide”

Jetzt wird es spannend.

Slide: Business Statement

“Am Tag der Tour startet der Guide die Tour.”

Frage:

Was ist die Regel?

Antwort:
•	Nur CONFIRMED → ACTIVE
•	Nur wenn heute >= TourDate

Wo gehört das hin?

➡ Ins Aggregate.

⸻

Slide: startTour(now)

Du zeigst:
•	Preconditions
•	State change
•	Domain Event

🎯 Hier kommt dein Architektur-Punch:

“Das Modell weiß selbst, wann es starten darf.”

Nicht:
•	Controller
•	Scheduler
•	Service
•	If-Kaskade irgendwo im Code

⸻

🎯 Meta-Layer: Was du in jedem Act betonst

Business Story	Architektur-Prinzip
Buchen	Always-valid Aggregate
Bestätigen	State Machine im Model
Kündigen	Invariants vs External Constraints
Starten	Zeitabhängige Regel im Aggregate
Alle	1 Use Case = 1 Transaction
Alle	Events explizit


⸻

🧠 Final Slide – The Real Message

Du schließt mit:

“Das ist kein Tour-System.
Das ist ein Denkmodell.”

Und dann:
•	Klare Aggregate
•	Klare Use Cases
•	Klare Transaktionsgrenzen
•	Klare Validierungs-Orte
•	Keine Framework-Abhängigkeit
•	AI kann damit arbeiten

⸻

💡 Warum diese Story perfekt für deinen Talk ist

Sie ist:
•	Nicht trivial
•	Nicht überkomplex
•	Natürlich sequentiell
•	Voller State-Transitions
•	Voller Invariants
•	Voller Transaktionsfragen
•	Voller Event-Fragen

Und sie zwingt dich nicht in Hotel-/Payment-Komplexität.

⸻

Wenn du willst, kann ich dir jetzt:
•	🧱 Eine perfekte Slide-Reihenfolge (Slide-by-Slide mit Titeln & Sprechtext)
•	🎨 Ein einziges zentrales Diagramm, das du immer wieder referenzierst
•	🔥 Oder eine “opinionated Closing-Statement Version” für deinen Thought-Leadership-Anspruch

Was brauchst du für die Bühne?

### Abstract (DE)

Domain Driven Design (DDD) und Ports-and-Adapters (Hexagonal Architecture) sind eine ideale Kombination: 
Sie schaffen klare Verantwortlichkeiten, halten den geschäftskritischen Core testbar und schützen 
ihn vor äußeren Abhängigkeiten. 

In der Praxis gehen diese Vorteile jedoch oft in endlosen Diskussionen verloren. 
Häufig erlebe ich Diskussionen darüber, welche Klasse in welches Package gehört 
oder wie sich Application- von Domain Services unterscheiden.

Es wird aufgezeigt, welche Regeln und Konzepte helfen, dass jedes Element seinen festen Platz erhält. 
So entsteht ein „Coding mise en place“: ein wiederholbares Ordnungssystem, 
das sofort angewendet werden kann. 

Teams profitieren von verkürzter Rüstzeit, neue Mitglieder finden sich schneller zurecht, und Architekt:innen, 
die viele Code-Basen überblicken müssen, können über Projekte hinweg effizient arbeiten.

Besonderes Augenmerk liegt auf den Trade-offs, die in realen Projekten unvermeidlich sind: 
Sind kleine Annotationen im Core akzeptabel oder gehört alles in einen Mapping-Layer? 
Wo ist Validierung am besten aufgehoben? 
Welche Rolle spielen interne und externe Domain Events, und wie lassen sich Transaktionen handhaben? 

Ergänzt wird der Vortrag durch konkrete Praxisbeispiele mit Paketstruktur, Klassentypen und Architekturregeln. 

Auf diese Weise entsteht ein Werkzeugkasten, der nicht nur Entwickler:innen und Architekt:innen unterstützt, 
sondern auch künftigen KI-Coding-Buddies klare Strukturen vorgibt.

Lernziele
- Welche Vorteile hat eine Blaupause für Code-Strukturierung
- Unterscheidung der einzelnen DDD Building-Blocks
- Platzierung der DDD Building-Blocks in der Ports-and-Adapter Struktur
- An welchen Stellen sind Trade-Offs sinnvoll sind und wie mit ihnen umgegangen werden kann
- Wie durch Architektur-Regeln eine Aufweichung der Strukturvorgaben vermieden werden kann
- Für welche Projekte sich die Strukturen eignen und für welche nicht
  Vorkenntnisse
- Wissen über DDD Building-Blocks und Ports-and-Adapters sind hilfreich
- Interesse für die Idee einer Blaupause um zu wissen "wo man in welchem Fall hingreifen muss"

## SDD Context
