package migration

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

type Runner struct {
	db  *sql.DB
	dir string
}

func NewRunner(db *sql.DB, dir string) *Runner {
	return &Runner{
		db:  db,
		dir: strings.TrimSpace(dir),
	}
}

func (r *Runner) Run(ctx context.Context) error {
	if strings.TrimSpace(r.dir) == "" {
		return fmt.Errorf("migrations directory is required")
	}

	if err := r.ensureSchemaMigrationsTable(ctx); err != nil {
		return err
	}

	files, err := collectMigrationFiles(r.dir)
	if err != nil {
		return err
	}

	applied, err := r.appliedVersions(ctx)
	if err != nil {
		return err
	}

	for _, file := range files {
		version := versionFromPath(file)
		if applied[version] {
			continue
		}

		if err := r.applyFile(ctx, file, version); err != nil {
			return err
		}
	}

	return nil
}

func (r *Runner) ensureSchemaMigrationsTable(ctx context.Context) error {
	const query = `
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);`

	if _, err := r.db.ExecContext(ctx, query); err != nil {
		return fmt.Errorf("ensure schema_migrations table: %w", err)
	}

	return nil
}

func (r *Runner) appliedVersions(ctx context.Context) (map[string]bool, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT version FROM schema_migrations`)
	if err != nil {
		return nil, fmt.Errorf("query applied migrations: %w", err)
	}
	defer rows.Close()

	versions := make(map[string]bool)
	for rows.Next() {
		var version string
		if err := rows.Scan(&version); err != nil {
			return nil, fmt.Errorf("scan applied migration: %w", err)
		}
		versions[version] = true
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate applied migrations: %w", err)
	}

	return versions, nil
}

func (r *Runner) applyFile(ctx context.Context, filePath, version string) error {
	content, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read migration %s: %w", version, err)
	}

	sqlText := strings.TrimSpace(string(content))
	if sqlText == "" {
		return nil
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin migration %s: %w", version, err)
	}

	defer func() {
		_ = tx.Rollback()
	}()

	if _, err := tx.ExecContext(ctx, sqlText); err != nil {
		return fmt.Errorf("execute migration %s: %w", version, err)
	}

	if _, err := tx.ExecContext(ctx, `INSERT INTO schema_migrations (version) VALUES ($1)`, version); err != nil {
		return fmt.Errorf("record migration %s: %w", version, err)
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit migration %s: %w", version, err)
	}

	return nil
}

func collectMigrationFiles(dir string) ([]string, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("read migrations directory: %w", err)
	}

	files := make([]string, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		if filepath.Ext(entry.Name()) != ".sql" {
			continue
		}
		files = append(files, filepath.Join(dir, entry.Name()))
	}

	sort.Strings(files)
	return files, nil
}

func versionFromPath(path string) string {
	return filepath.Base(path)
}
