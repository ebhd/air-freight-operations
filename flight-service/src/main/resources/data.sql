-- Temporary starter catalogue: IATA delay codes 63 (FT) and 71 (WO).
INSERT INTO delay_reasons (code, label) VALUES
    ('63', 'Late crew boarding or departure procedures (excluding connection and standby)'),
    ('71', 'Weather at departure station')
ON CONFLICT (code) DO NOTHING;
