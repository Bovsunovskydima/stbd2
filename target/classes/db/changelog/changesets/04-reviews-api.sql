-- liquibase formatted sql

--changeset bovsunovsky:04-view-reviews-full
CREATE VIEW v_reviews_full AS
SELECT r.id AS review_id,
       r.review_no,
       r.order_id,
       r.source,
       r.channel,
       r.created_at,
       r.branch_id,
       r.branch_name,
       r.branch_city,
       r.client_id,
       r.client_name,
       r.client_phone,
       r.rating,
       r.body_text,
       r.reply_staff_id,
       r.reply_staff_name,
       r.reply_text,
       r.replied_at,
       (SELECT coalesce(array_agg(a.name ORDER BY a.name), '{}')
        FROM review_aspects a
        WHERE a.review_id = r.id)  AS aspect_names,
       (SELECT coalesce(array_agg(a.score ORDER BY a.name), '{}')
        FROM review_aspects a
        WHERE a.review_id = r.id)  AS aspect_scores,
       (SELECT coalesce(array_agg(t.tag ORDER BY t.tag), '{}')
        FROM review_tags t
        WHERE t.review_id = r.id)  AS tags,
       (SELECT coalesce(array_agg(p.url ORDER BY p.position), '{}')
        FROM review_photos p
        WHERE p.review_id = r.id)  AS photos,
       i.id AS item_id,
       i.menu_item_id,
       i.title,
       i.rating AS item_rating,
       i.comment
FROM reviews r
         LEFT JOIN review_items i ON i.review_id = r.id;

--changeset bovsunovsky:04-view-reviews
CREATE VIEW v_reviews AS
SELECT r.id,
       r.review_no,
       r.created_at,
       r.branch_name,
       r.client_name,
       r.rating,
       r.source,
       (r.reply_text IS NOT NULL)                                      AS answered,
       (SELECT count(*) FROM review_items i WHERE i.review_id = r.id)  AS items_count,
       (SELECT count(*) FROM review_photos p WHERE p.review_id = r.id) AS photos_count
FROM reviews r;

--changeset bovsunovsky:04-sp-review-add splitStatements:false
CREATE PROCEDURE sp_review_add(p_review_no TEXT,
                               p_order_id BIGINT,
                               p_source TEXT,
                               p_channel TEXT,
                               p_created_at TIMESTAMP,
                               p_branch_id INT,
                               p_branch_name TEXT,
                               p_branch_city TEXT,
                               p_client_id BIGINT,
                               p_client_name TEXT,
                               p_client_phone TEXT,
                               p_rating SMALLINT,
                               p_body TEXT,
                               p_aspect_names TEXT[],
                               p_aspect_scores SMALLINT[],
                               p_item_ids BIGINT[],
                               p_item_titles TEXT[],
                               p_item_ratings SMALLINT[],
                               p_item_comments TEXT[],
                               p_tags TEXT[],
                               p_photos TEXT[],
                               INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO reviews (review_no, order_id, source, channel, created_at,
                         branch_id, branch_name, branch_city,
                         client_id, client_name, client_phone,
                         rating, body_text)
    VALUES (p_review_no, p_order_id, p_source, p_channel, p_created_at,
            p_branch_id, p_branch_name, p_branch_city,
            p_client_id, p_client_name, p_client_phone,
            p_rating, p_body)
    RETURNING id INTO p_id;

    IF coalesce(array_length(p_aspect_names, 1), 0) > 0 THEN
        INSERT INTO review_aspects (review_id, name, score)
        SELECT p_id, p_aspect_names[s.i], p_aspect_scores[s.i]
        FROM generate_subscripts(p_aspect_names, 1) AS s(i);
    END IF;

    IF coalesce(array_length(p_item_ids, 1), 0) > 0 THEN
        INSERT INTO review_items (review_id, menu_item_id, title, rating, comment)
        SELECT p_id, p_item_ids[s.i], p_item_titles[s.i], p_item_ratings[s.i], p_item_comments[s.i]
        FROM generate_subscripts(p_item_ids, 1) AS s(i);
    END IF;

    IF coalesce(array_length(p_tags, 1), 0) > 0 THEN
        INSERT INTO review_tags (review_id, tag)
        SELECT DISTINCT p_id, t
        FROM unnest(p_tags) AS t;
    END IF;

    IF coalesce(array_length(p_photos, 1), 0) > 0 THEN
        INSERT INTO review_photos (review_id, position, url)
        SELECT p_id, s.i, p_photos[s.i]
        FROM generate_subscripts(p_photos, 1) AS s(i);
    END IF;
END
$$;

--changeset bovsunovsky:04-sp-review-reply splitStatements:false
CREATE PROCEDURE sp_review_reply(p_review_no TEXT, p_staff_id BIGINT, p_staff_name TEXT, p_text TEXT)
    LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE reviews
    SET reply_staff_id   = p_staff_id,
        reply_staff_name = p_staff_name,
        reply_text       = p_text,
        replied_at       = localtimestamp
    WHERE review_no = p_review_no;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Відгук % не знайдено', p_review_no;
    END IF;
END
$$;

--changeset bovsunovsky:04-sp-reviews-clear splitStatements:false
CREATE PROCEDURE sp_reviews_clear()
    LANGUAGE plpgsql
AS
$$
BEGIN
    TRUNCATE review_aspects, review_items, review_tags, review_photos, reviews RESTART IDENTITY;
END
$$;

--changeset bovsunovsky:04-sp-reviews-analyze splitStatements:false
CREATE PROCEDURE sp_reviews_analyze()
    LANGUAGE plpgsql
AS
$$
BEGIN
    ANALYZE reviews;
    ANALYZE review_aspects;
    ANALYZE review_items;
    ANALYZE review_tags;
    ANALYZE review_photos;
END
$$;

--changeset bovsunovsky:04-fn-branch-ratings splitStatements:false
CREATE FUNCTION fn_branch_ratings(p_from TIMESTAMP, p_to TIMESTAMP)
    RETURNS TABLE
            (
                branch_id  INT,
                branch     TEXT,
                reviews    BIGINT,
                avg_rating NUMERIC
            )
    LANGUAGE sql
    STABLE
AS
$$
SELECT r.branch_id,
       max(r.branch_name)::TEXT AS branch,
       count(*)::BIGINT         AS reviews,
       round(avg(r.rating), 2)  AS avg_rating
FROM reviews r
WHERE r.created_at >= p_from
  AND r.created_at < p_to
GROUP BY r.branch_id
ORDER BY avg_rating DESC, reviews DESC;
$$;

--changeset bovsunovsky:04-fn-aspect-stats splitStatements:false
CREATE FUNCTION fn_aspect_stats(p_from TIMESTAMP, p_to TIMESTAMP)
    RETURNS TABLE
            (
                aspect    TEXT,
                answers   BIGINT,
                avg_score NUMERIC
            )
    LANGUAGE sql
    STABLE
AS
$$
SELECT a.name::TEXT     AS aspect,
       count(*)::BIGINT AS answers,
       round(avg(a.score), 2) AS avg_score
FROM review_aspects a
         JOIN reviews r ON r.id = a.review_id
WHERE r.created_at >= p_from
  AND r.created_at < p_to
GROUP BY a.name
ORDER BY avg_score DESC;
$$;

--changeset bovsunovsky:04-fn-reviews-by-tag splitStatements:false
CREATE FUNCTION fn_reviews_by_tag(p_tag TEXT, p_limit INT)
    RETURNS TABLE
            (
                review_no  TEXT,
                created_at TIMESTAMP,
                rating     SMALLINT,
                branch     TEXT
            )
    LANGUAGE sql
    STABLE
AS
$$
SELECT r.review_no::TEXT,
       r.created_at,
       r.rating,
       r.branch_name::TEXT
FROM review_tags t
         JOIN reviews r ON r.id = t.review_id
WHERE t.tag = p_tag
ORDER BY r.created_at DESC
LIMIT p_limit;
$$;
