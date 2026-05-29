ALTER TABLE user_legal_entities ADD COLUMN link_requested_at TIMESTAMP;
UPDATE user_legal_entities SET link_requested_at = NOW() WHERE link_status = 'PENDING';
