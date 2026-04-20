INSERT INTO user_credential (user_guid, email, password) VALUES
    ('abc-123', 'nelson@example.com',   '{noop}Test1234'),
    ('def-123', 'danielle@example.com', '{noop}Test1234'),
    ('ghi-123', 'kuan@example.com',     '{noop}Test1234'),
    ('jkl-123', 'henry@example.com',    '{noop}Test1234'),
    ('mno-123', 'alice@example.com',    '{noop}Test1234');

INSERT INTO role (role_guid, credential_id, role_name) VALUES
    ('stu-234', 1, 'pastor'),
    ('nop-234', 1, 'member'),
    ('vwx-234', 2, 'deacon'),
    ('qrs-234', 2, 'member'),
    ('yza-234', 3, 'small_group_leader'),
    ('klm-234', 3, 'member'),
    ('bcd-234', 4, 'vice_small_group_leader'),
    ('tuv-234', 4, 'member'),
    ('efg-234', 5, 'member');
