-- starter catalogue of two iata delay reasons until the full list is chosen
INSERT INTO delay_reasons (code, label) VALUES
    ('63', 'Late crew boarding or departure procedures'),
    ('71', 'Weather at departure station')
ON CONFLICT (code) DO NOTHING;
