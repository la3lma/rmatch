-- Initial test data for the prospects test database

INSERT INTO test_data (name, value) VALUES
    ('test_record_1', 42),
    ('test_record_2', 100),
    ('test_record_3', 256),
    ('hello_world', 1),
    ('sample_data', 999);

INSERT INTO regex_patterns (pattern, description, is_active) VALUES
    ('hello.*world', 'Match hello world variations', true),
    ('[a-zA-Z]+', 'Match alphabetic sequences', true),
    ('\\d{3,5}', 'Match 3-5 digits', true),
    ('test_\\w+', 'Match test prefixed words', false),
    ('(pattern|regex)', 'Match pattern or regex words', true);