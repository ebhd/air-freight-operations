-- temp delay  reasons.
INSERT INTO delay_reasons (code, label) VALUES
    ('63', 'Late crew boarding or departure procedures'),
    ('71', 'Weather at departure station')
ON CONFLICT (code) DO NOTHING;
