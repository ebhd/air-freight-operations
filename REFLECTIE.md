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
