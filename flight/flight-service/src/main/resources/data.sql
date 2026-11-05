-- starter catalogue of two iata delay reasons until the full list is chosen
INSERT INTO delay_reasons (code, label) VALUES
    ('63', 'Late crew boarding or departure procedures'),
    ('71', 'Weather at departure station')
ON CONFLICT (code) DO NOTHING;

-- starter catalogue of airports, a code that is not listed is shown without a name
INSERT INTO airports (code, name) VALUES
    ('AMS', 'Amsterdam Airport Schiphol'),
    ('ATH', 'Athens International Airport'),
    ('BCN', 'Barcelona-El Prat Airport'),
    ('BRU', 'Brussels Airport'),
    ('CDG', 'Paris Charles de Gaulle Airport'),
    ('DOH', 'Hamad International Airport'),
    ('DXB', 'Dubai International Airport'),
    ('FCO', 'Rome Fiumicino Airport'),
    ('FRA', 'Frankfurt Airport'),
    ('IST', 'Istanbul Airport'),
    ('JFK', 'John F. Kennedy International Airport'),
    ('LGG', 'Liege Airport'),
    ('LHR', 'London Heathrow Airport'),
    ('MAD', 'Adolfo Suarez Madrid-Barajas Airport'),
    ('MUC', 'Munich Airport'),
    ('OST', 'Ostend-Bruges International Airport')
ON CONFLICT (code) DO NOTHING;
