CREATE TABLE guide_tour (
    id               VARCHAR(36)  NOT NULL,
    tour_id          VARCHAR(255) NOT NULL,
    scheduled_start  TIMESTAMP    NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    started_at       TIMESTAMP    NULL,
    CONSTRAINT pk_guide_tour PRIMARY KEY (id)
);
