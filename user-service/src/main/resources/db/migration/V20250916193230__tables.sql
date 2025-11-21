CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL ,
    password VARCHAR(255) NOT NULL ,
    firstname VARCHAR(50) NOT NULL ,
    lastname VARCHAR(50) NOT NULL ,
    surname VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    email_verified BOOLEAN DEFAULT FALSE
);

CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY ,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
