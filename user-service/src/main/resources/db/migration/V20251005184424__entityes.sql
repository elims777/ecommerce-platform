INSERT INTO users (email, password, firstname, lastname, surname) VALUES ( 'elims777@yandex.ru',
                          '$2a$12$QgwT7lCmYH4FNSauNxEJhOEw3RYLMvkfsOorRHxIgK.w1EArzPJ1u',
                          'Max',
                          'Kokorin',
                          'Vitalievich');

INSERT INTO roles (id, name) VALUES (1, 'ROLE_ADMIN'),
                                    (2, 'ROLE_USER');

INSERT INTO user_roles (user_id, role_id) VALUES (1,1);