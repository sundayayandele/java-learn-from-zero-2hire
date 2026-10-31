-- Which help-centre files are embedded, and from which version (topic 15.2). The vector table itself is created by Spring AI.
CREATE TABLE IF NOT EXISTS help_doc (
    source    text PRIMARY KEY,
    sha256    text NOT NULL,
    chunks    int NOT NULL,
    loaded_at timestamptz NOT NULL DEFAULT now()
);
