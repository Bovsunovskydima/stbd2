-- liquibase formatted sql

--changeset bovsunovsky:03-review-tables

CREATE TABLE reviews
(
    id               BIGSERIAL PRIMARY KEY,
    review_no        VARCHAR(20) NOT NULL,
    order_id         BIGINT,
    source           VARCHAR(10) NOT NULL,
    channel          VARCHAR(10) NOT NULL,
    created_at       TIMESTAMP   NOT NULL,
    branch_id        INT,
    branch_name      VARCHAR(100),
    branch_city      VARCHAR(80),
    client_id        BIGINT,
    client_name      VARCHAR(150),
    client_phone     VARCHAR(20),
    rating           SMALLINT    NOT NULL,
    body_text        TEXT,
    reply_staff_id   BIGINT,
    reply_staff_name VARCHAR(150),
    reply_text       TEXT,
    replied_at       TIMESTAMP,
    CONSTRAINT uq_reviews_no UNIQUE (review_no),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_source CHECK (source IN ('APP', 'QR', 'SITE')),
    CONSTRAINT ck_reviews_channel CHECK (channel IN ('HALL', 'TAKEAWAY', 'DELIVERY'))
);

CREATE TABLE review_aspects
(
    id        BIGSERIAL PRIMARY KEY,
    review_id BIGINT      NOT NULL,
    name      VARCHAR(30) NOT NULL,
    score     SMALLINT    NOT NULL,
    CONSTRAINT fk_aspects_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT uq_aspects_review_name UNIQUE (review_id, name),
    CONSTRAINT ck_aspects_score CHECK (score BETWEEN 1 AND 5)
);

CREATE TABLE review_items
(
    id           BIGSERIAL PRIMARY KEY,
    review_id    BIGINT       NOT NULL,
    menu_item_id BIGINT       NOT NULL,
    title        VARCHAR(120) NOT NULL,
    rating       SMALLINT     NOT NULL,
    comment      VARCHAR(200),
    CONSTRAINT fk_review_items_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT ck_review_items_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE review_tags
(
    review_id BIGINT      NOT NULL,
    tag       VARCHAR(40) NOT NULL,
    CONSTRAINT pk_review_tags PRIMARY KEY (review_id, tag),
    CONSTRAINT fk_review_tags_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
);

CREATE TABLE review_photos
(
    id        BIGSERIAL PRIMARY KEY,
    review_id BIGINT       NOT NULL,
    position  INT          NOT NULL,
    url       VARCHAR(250) NOT NULL,
    CONSTRAINT fk_review_photos_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT uq_review_photos_position UNIQUE (review_id, position)
);

--changeset bovsunovsky:03-review-indexes
CREATE INDEX ix_reviews_branch_created ON reviews (branch_id, created_at DESC);
CREATE INDEX ix_reviews_created ON reviews (created_at DESC);
CREATE INDEX ix_reviews_rating ON reviews (rating);
CREATE INDEX ix_review_aspects_review ON review_aspects (review_id);
CREATE INDEX ix_review_aspects_name ON review_aspects (name);
CREATE INDEX ix_review_items_review ON review_items (review_id);
CREATE INDEX ix_review_items_menu ON review_items (menu_item_id);
CREATE INDEX ix_review_tags_tag ON review_tags (tag);
CREATE INDEX ix_review_photos_review ON review_photos (review_id);
