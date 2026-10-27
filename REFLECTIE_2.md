# Zelfreflectie 2

Datum: 27 oktober 2026  
Team: Fabio en Ebrahim  

## Pre coaching

### Geschatte progress: ongeveer 70%

### Status

De samenwerking ging goed. De juiste ontwerpkeuzes maken blijft moeilijk, vooral bij de communicatie tussen services.

**Werkverdeling:**

- Ebrahim: vooral Flight Operations.
- Fabio: vooral Cargo Loading.
- Samen: Partner Communication.

**Werkt al:** vluchten aanmaken, vertragingen beheren, ULD's boeken en plaatsen, aanmelden en FLM/ED/EA-berichten versturen.

**Nog te doen:**

- Frontend afwerken, edge cases controleren en feedback verwerken.
- Laadcontroles samenbrengen, finalisatie en CPM afwerken.
- Werkelijk vertrek en aankomst, AD/AA-berichten, Gantt en oude gegevens opruimen.

### GitLab issues en beslissingen

Sommige issues zijn deels meegenomen bij ander werk, maar zijn nog niet volledig afgerond.

### Feedback tussentijds coachmoment

#### Worth considering

- Twee lijsten voor vertragingen: vertrek en aankomst. Duidelijker en minder filteren op type.
- Berichtformattering in een aparte microservice als anti-corruption layer. Onze Partner Communication hierop nakijken.
- Voor een load alleen de bijbehorende vlucht via HTTP opvragen (Open Host Service).
- Verder afstemmen: latere wijzigingen voor die vlucht via RabbitMQ aan Cargo doorgeven.

## Post coaching

.....
