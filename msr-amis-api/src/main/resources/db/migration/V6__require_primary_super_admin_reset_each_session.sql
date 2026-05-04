UPDATE users
SET must_change_password = TRUE
WHERE LOWER(email) = LOWER('wkautsa@gmail.com');
