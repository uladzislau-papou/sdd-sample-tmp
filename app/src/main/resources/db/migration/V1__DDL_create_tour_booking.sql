CREATE TABLE tour_booking (
    id                VARCHAR(36)  NOT NULL,
    tour_id           VARCHAR(255) NOT NULL,
    tour_date         DATE         NOT NULL,
    participant_count INT          NOT NULL,
    available_capacity INT         NOT NULL,
    contact_name      VARCHAR(255) NOT NULL,
    contact_email     VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_tour_booking PRIMARY KEY (id)
);
