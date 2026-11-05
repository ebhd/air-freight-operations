-- starter catalogue of iata delay reasons
INSERT INTO delay_reasons (code, label) VALUES
    ('11', 'Late check-in, acceptance after deadline'),
    ('15', 'Boarding, discrepancies and paging'),
    ('21', 'Cargo documentation errors'),
    ('22', 'Late positioning of cargo'),
    ('32', 'Loading and unloading'),
    ('41', 'Aircraft defects'),
    ('43', 'Non-scheduled maintenance'),
    ('63', 'Late crew boarding or departure procedures'),
    ('71', 'Weather at departure station'),
    ('72', 'Weather at destination station'),
    ('75', 'De-icing of aircraft'),
    ('81', 'Air traffic flow restriction en route'),
    ('89', 'Restrictions at airport of departure'),
    ('93', 'Aircraft rotation, late arrival from another flight')
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
