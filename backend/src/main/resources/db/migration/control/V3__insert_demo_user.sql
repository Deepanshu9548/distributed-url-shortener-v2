-- Insert demo credentials for testing
INSERT INTO users (id, email, email_normalized, password_hash, created_at)
SELECT 1, 'deepanshu95488@gmail.com', 'deepanshu95488@gmail.com', '$2a$10$JuuUKhkgMfBf6H8hWFfL6e9XyQQub40znNdg.7tnS5Yu02go6a6KC', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email_normalized = 'deepanshu95488@gmail.com'
);
