SELECT * FROM
  (SELECT * FROM publication WHERE
    ? OR
    LOWER(isbn) LIKE ? OR
    LOWER(title) LIKE ?
    ORDER BY lastmodifieddate DESC LIMIT ? OFFSET ?
  ) AS s1
  JOIN
  (SELECT count(*) as fullcount FROM publication WHERE
    ?
    OR LOWER(isbn) LIKE ?
    OR LOWER(title) LIKE ?) AS s2
  ON true