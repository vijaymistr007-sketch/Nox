CREATE TABLE IF NOT EXISTS knowledge_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    tags TEXT NOT NULL DEFAULT '',
    projectRoot TEXT,
    sessionId TEXT,
    sourceMessageIds TEXT NOT NULL DEFAULT '',
    contentHash TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    hitCount INTEGER NOT NULL DEFAULT 0,
    lastRecalledAt INTEGER,
    pinned INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_entries_contentHash ON knowledge_entries (contentHash);
CREATE INDEX IF NOT EXISTS index_knowledge_entries_projectRoot ON knowledge_entries (projectRoot);
CREATE INDEX IF NOT EXISTS index_knowledge_entries_updatedAt ON knowledge_entries (updatedAt);
