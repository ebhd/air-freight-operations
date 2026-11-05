# Zelfreflectie 1

Datum: 7 oktober 2026  
Team: Fabio en Ebrahim  

## Pre coaching

### Geschatte progress: 15%

### Status

De samenwerking ging heel vlot. We zitten ongeveer op hetzelfde niveau qua kennis en ervaring, waardoor we makkelijk samen door de opdracht konden gaan. Onduidelijkheden en lastige ontwerpkeuzes hebben we samen besproken en volgens ons goed opgelost.

De analyse hebben we ongeveer 50/50 verdeeld:

- Fabio: vooral Cargo Loading en Partner Communication, en daarna de projectsetup met de algemene mappenstructuur, CI en testsetup.
- Ebrahim: vooral Flight Operations, en daarna de GitLab-issues en user stories uitgewerkt en geordend.

We hebben tijdens het feedbackmoment goede vragen kunnen stellen en hebben het gevoel dat we goed op weg zijn om met development te beginnen.

Wat minder goed ging: Alles ging zowat top tegenover andere teamprojecten.

### GitLab issues en beslissingen

We hebben vrijwel alle ontwerpvragen samen afgewogen. 

### Vragen

- Wanneer gebruiken we HTTP-requests en wanneer RabbitMQ, bijvoorbeeld tussen Flight Operations en Cargo Loading?
- Is Partner Communication een apart subdomein en een aparte bounded context?

## Post coaching

De feedback was dat we goed bezig zijn. Verder nemen we dit mee:

- Partner Messages is geen apart subdomein of bounded context. We passen dit aan in de analyse.
- We bekijken of Open Host Service tussen Flight Operations en Cargo Loading weg kan en alleen RabbitMQ volstaat.
- We mogen AI gebruiken voor het uitwerken van user stories.

### Tweede feedbackmoment

- Partner Communication is een aparte module, geen eigen subdomein of bounded context. We passen de context mapping hierop aan. Het nut van die module: partnercommunicatie op één plek kunnen aanpassen.
- De HTTP-requests zorgen voor te veel onderlinge afhankelijkheid. We kunnen daarvoor events via RabbitMQ gebruiken.
- Mogelijke examenvraag: als RabbitMQ als fallback kan dienen, waarom gebruiken we het dan niet meteen?
- Beginnen met development: eerst een vlucht kunnen aanmaken en de basis werkend krijgen.
- Regelmatig en op tijd committen en pushen.
- Eigen GitLab-labels aanmaken?

---

# Zelfreflectie 3

Datum: 5 november 2026  
Team: Fabio en Ebrahim  

## Eindstatus

### Geschatte progress: 100%

### Status

Alles is afgewerkt. De punten die na sprint 2 nog openstonden, zitten nu ook in het project: de laadcontroles, finalisatie en CPM, werkelijk vertrek en aankomst met AD/AA-berichten, de Gantt en het opruimen van oude gegevens.

We zijn tevreden en trots op het eindresultaat. We hebben heel veel geleerd tijdens deze opdracht, niet alleen technisch, maar ook over hoe je samen een groter project aanpakt: van de analyse van het domein tot services die zelfstandig werken en via events met elkaar communiceren.

### Niet afgewerkt

Niets. Alle onderdelen zijn afgewerkt.

### Samenwerking

Ebrahim en ik vonden het allebei heel leuk en handig om in een coöperatief team te zitten, met goede communicatie en samenwerking. Over elke grote beslissing en ontwerpkeuze hebben we samen geredeneerd en samen beslist. Daardoor begrijpen we allebei ook de delen waaraan de ander vooral heeft gewerkt, en kunnen we de keuzes in het hele project verantwoorden.

### Wat we geleerd hebben

**Architectuur.** De juiste ontwerpkeuzes maken bleef het moeilijkste deel, vooral bij de communicatie tussen services. We hebben ervaren dat zulke keuzes altijd een afweging zijn, en dat je ze moet kunnen uitleggen.

**Coachfeedback.** De feedback van de coachmomenten heeft ons ontwerp echt beter gemaakt:

- Partner Communication is geen apart subdomein of bounded context meer, maar een aparte module.
- Cargo Loading houdt een eigen kopie bij van de vluchten die het nodig heeft en krijgt wijzigingen via RabbitMQ. HTTP gebruiken we alleen nog voor een vlucht die Cargo nog niet kent. Zo blijft Cargo werken wanneer Flight Operations niet bereikbaar is.
- Berichtformattering zit nu in een aparte service, zodat de partnerformaten los staan van de rest van de applicatie.

**GitLab.** We hebben geleerd hoe we GitLab-issues en merge requests goed en effectief gebruiken om werk te splitsen, te plannen en op elkaar af te stemmen. Door via merge requests te werken, bleven we ook op de hoogte van elkaars werk.

### Wat we meenemen

- Altijd genoeg vragen stellen aan de coach. Elk coachmoment leverde inzichten op die ons later veel werk bespaarden.
- Op tijd beginnen met de opdrachten en met coderen. Zo blijft er ruimte om feedback echt te verwerken.
- Ontwerpkeuzes samen bespreken voordat we ze bouwen.
